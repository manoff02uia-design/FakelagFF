package com.fakelag.ff;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import androidx.core.app.NotificationCompat;

public class OverlayService extends Service {

    private WindowManager mWm;
    private View          mRoot;
    private WindowManager.LayoutParams mParams;

    private boolean mBtnFreeze = false;
    private boolean mBtnGhost  = false;
    private boolean mBtnTele   = false;

    private static final String CHANNEL_ID = "OverlayChannel";
    private static final int    NOTIF_ID   = 2;

    @Override
    public void onCreate() {
        super.onCreate();
        mWm = (WindowManager) getSystemService(WINDOW_SERVICE);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(NOTIF_ID, buildNotification());
        showOverlay();
        return START_STICKY;
    }

    private void showOverlay() {
        if (mRoot != null) return; // already shown

        mRoot = LayoutInflater.from(this).inflate(R.layout.overlay_layout, null);

        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
            ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            : WindowManager.LayoutParams.TYPE_PHONE;

        mParams = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        );
        mParams.gravity = Gravity.TOP | Gravity.START;
        mParams.x = 10;
        mParams.y = 200;

        // Drag support
        mRoot.setOnTouchListener(new DragListener());

        // Buttons
        Button btnFreeze = mRoot.findViewById(R.id.btn_freeze);
        Button btnGhost  = mRoot.findViewById(R.id.btn_ghost);
        Button btnTele   = mRoot.findViewById(R.id.btn_tele);
        TextView tvStatus = mRoot.findViewById(R.id.tv_status);

        btnFreeze.setOnClickListener(v -> {
            mBtnFreeze = !mBtnFreeze;
            updateBtn(btnFreeze, "FREEZE", mBtnFreeze);
            sendFlagUpdate();
            tvStatus.setText(mBtnFreeze ? "● FREEZE ON" : "○ OFF");
        });

        btnGhost.setOnClickListener(v -> {
            mBtnGhost = !mBtnGhost;
            updateBtn(btnGhost, "GHOST", mBtnGhost);
            sendFlagUpdate();
            tvStatus.setText(mBtnGhost ? "● GHOST ON" : "○ OFF");
        });

        btnTele.setOnClickListener(v -> {
            mBtnTele = !mBtnTele;
            updateBtn(btnTele, "TELE", mBtnTele);
            sendFlagUpdate();
            tvStatus.setText(mBtnTele ? "● TELE ON" : "○ OFF");
        });

        mWm.addView(mRoot, mParams);
    }

    private void updateBtn(Button btn, String label, boolean active) {
        if (active) {
            btn.setBackgroundColor(Color.parseColor("#00C853")); // green
            btn.setTextColor(Color.WHITE);
            btn.setText(label + " ✓");
        } else {
            btn.setBackgroundColor(Color.parseColor("#212121")); // dark
            btn.setTextColor(Color.WHITE);
            btn.setText(label);
        }
    }

    private void sendFlagUpdate() {
        Intent i = new Intent(this, FakeLagVpnService.class);
        i.setAction(FakeLagVpnService.ACTION_START);
        i.putExtra(FakeLagVpnService.EXTRA_FREEZE, mBtnFreeze);
        i.putExtra(FakeLagVpnService.EXTRA_GHOST,  mBtnGhost);
        i.putExtra(FakeLagVpnService.EXTRA_TELE,   mBtnTele);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(i);
        } else {
            startService(i);
        }
        // Also update static flags directly for speed
        FakeLagVpnService.sFreeze.set(mBtnFreeze);
        FakeLagVpnService.sGhost .set(mBtnGhost);
        FakeLagVpnService.sTele  .set(mBtnTele);
    }

    // ── Drag Listener ──────────────────────────────────────────
    private class DragListener implements View.OnTouchListener {
        int initX, initY;
        float touchX, touchY;

        @Override
        public boolean onTouch(View v, MotionEvent e) {
            switch (e.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    initX  = mParams.x;
                    initY  = mParams.y;
                    touchX = e.getRawX();
                    touchY = e.getRawY();
                    return false; // allow click to propagate
                case MotionEvent.ACTION_MOVE:
                    mParams.x = initX + (int)(e.getRawX() - touchX);
                    mParams.y = initY + (int)(e.getRawY() - touchY);
                    mWm.updateViewLayout(mRoot, mParams);
                    return true;
            }
            return false;
        }
    }

    @Override
    public void onDestroy() {
        if (mRoot != null) {
            mWm.removeView(mRoot);
            mRoot = null;
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    private Notification buildNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                CHANNEL_ID, "Overlay", NotificationManager.IMPORTANCE_LOW);
            getSystemService(NotificationManager.class).createNotificationChannel(ch);
        }
        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Fake Lag FF")
            .setContentText("Overlay đang hiển thị")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build();
    }
}
