package com.fakelag.ff;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.net.VpnService;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import androidx.core.app.NotificationCompat;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class FakeLagVpnService extends VpnService {

    private static final String TAG = "FakeLagVpn";
    private static final String CHANNEL_ID = "FakeLagChannel";
    private static final int NOTIF_ID = 1;

    public static final String ACTION_START = "START";
    public static final String ACTION_STOP = "STOP";
    public static final String EXTRA_FREEZE = "freeze";
    public static final String EXTRA_GHOST = "ghost";
    public static final String EXTRA_TELE = "tele";
    public static final String EXTRA_LAG_MS = "lag_ms";

    // Singleton state flags — accessed from OverlayService & MainActivity
    public static final AtomicBoolean sFreeze = new AtomicBoolean(false);
    public static final AtomicBoolean sGhost  = new AtomicBoolean(false);
    public static final AtomicBoolean sTele   = new AtomicBoolean(false);
    public static volatile int        sLagMs  = 150;
    public static volatile boolean    sRunning = false;

    private ParcelFileDescriptor mVpnInterface;
    private ExecutorService      mExecutor;
    private Thread               mTunThread;

    // Packet queue for tele / fake-lag buffering
    private final Queue<TimedPacket> mPacketQueue = new ArrayDeque<>();
    private final Object             mQueueLock   = new Object();

    private static class TimedPacket {
        byte[] data;
        long   releaseMs;
        TimedPacket(byte[] d, long r) { data = d; releaseMs = r; }
    }

    // ─────────────────────────────────────────────────────────
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_STICKY;

        if (ACTION_STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }

        // Update feature flags on the fly
        sFreeze.set(intent.getBooleanExtra(EXTRA_FREEZE, sFreeze.get()));
        sGhost .set(intent.getBooleanExtra(EXTRA_GHOST,  sGhost .get()));
        sTele  .set(intent.getBooleanExtra(EXTRA_TELE,   sTele  .get()));
        sLagMs  =   intent.getIntExtra    (EXTRA_LAG_MS, sLagMs);

        if (!sRunning) {
            startForeground(NOTIF_ID, buildNotification());
            startVpn();
        }
        return START_STICKY;
    }

    // ─────────────────────────────────────────────────────────
    private void startVpn() {
        try {
            Builder b = new Builder();
            b.setSession("FakeLagFF");
            b.addAddress("10.233.0.1", 24);
            b.addDnsServer("8.8.8.8");
            // Route ALL traffic through tun
            b.addRoute("0.0.0.0", 0);
            b.setMtu(1500);
            mVpnInterface = b.establish();
            if (mVpnInterface == null) { Log.e(TAG, "VPN establish failed"); return; }

            sRunning = true;
            mExecutor = Executors.newFixedThreadPool(2);

            FileInputStream  in  = new FileInputStream (mVpnInterface.getFileDescriptor());
            FileOutputStream out = new FileOutputStream(mVpnInterface.getFileDescriptor());

            // Reader thread — read from tun, apply feature, queue or forward
            mExecutor.submit(() -> {
                ByteBuffer packet = ByteBuffer.allocate(32767);
                while (sRunning) {
                    try {
                        packet.clear();
                        int len = in.read(packet.array());
                        if (len <= 0) continue;
                        byte[] raw = new byte[len];
                        System.arraycopy(packet.array(), 0, raw, 0, len);

                        if (sFreeze.get()) {
                            // Drop all packets (freeze position on server side)
                            continue;
                        }
                        if (sGhost.get()) {
                            // Drop position/move packets (keep only handshake/ack)
                            // Simple heuristic: drop UDP packets > 20 bytes (game state)
                            if (isUdp(raw) && len > 40) continue;
                        }
                        if (sTele.get()) {
                            // Buffer packets for TELE_HOLD_MS then flush all at once
                            synchronized (mQueueLock) {
                                long release = System.currentTimeMillis() + 800L;
                                mPacketQueue.offer(new TimedPacket(raw, release));
                            }
                        } else {
                            // Normal path: delay by sLagMs (fake lag)
                            long delay = sLagMs;
                            synchronized (mQueueLock) {
                                mPacketQueue.offer(new TimedPacket(raw,
                                    System.currentTimeMillis() + delay));
                            }
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "read err", e);
                    }
                }
            });

            // Writer thread — flush packets when their timer expires
            mExecutor.submit(() -> {
                while (sRunning) {
                    try {
                        long now = System.currentTimeMillis();
                        synchronized (mQueueLock) {
                            while (!mPacketQueue.isEmpty()) {
                                TimedPacket tp = mPacketQueue.peek();
                                if (tp == null) break;
                                if (now >= tp.releaseMs) {
                                    mPacketQueue.poll();
                                    out.write(tp.data);
                                    out.flush();
                                } else {
                                    break;
                                }
                            }
                        }
                        Thread.sleep(5);
                    } catch (Exception e) {
                        Log.e(TAG, "write err", e);
                    }
                }
            });

        } catch (Exception e) {
            Log.e(TAG, "startVpn error", e);
        }
    }

    private boolean isUdp(byte[] pkt) {
        if (pkt.length < 20) return false;
        return (pkt[9] & 0xFF) == 17; // IP proto 17 = UDP
    }

    // ─────────────────────────────────────────────────────────
    @Override
    public void onDestroy() {
        sRunning = false;
        if (mExecutor != null) mExecutor.shutdownNow();
        try { if (mVpnInterface != null) mVpnInterface.close(); } catch (Exception ignored) {}
        super.onDestroy();
    }

    // ─────────────────────────────────────────────────────────
    private Notification buildNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                CHANNEL_ID, "Fake Lag FF", NotificationManager.IMPORTANCE_LOW);
            getSystemService(NotificationManager.class).createNotificationChannel(ch);
        }
        Intent pi = new Intent(this, MainActivity.class);
        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Fake Lag FF")
            .setContentText("Đang chạy — bấm để mở")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(PendingIntent.getActivity(this, 0, pi,
                PendingIntent.FLAG_IMMUTABLE))
            .build();
    }
}
