package com.fakelag.ff;

import android.app.Activity;
import android.content.Intent;
import android.net.VpnService;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

    private static final int REQ_VPN     = 1;
    private static final int REQ_OVERLAY = 2;

    private Button   btnStart;
    private TextView tvLagVal;
    private SeekBar  seekLag;
    private boolean  mVpnGranted = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btnStart = findViewById(R.id.btn_start);
        tvLagVal = findViewById(R.id.tv_lag_value);
        seekLag  = findViewById(R.id.seek_lag);

        // Lag slider 0-500 ms
        seekLag.setMax(500);
        seekLag.setProgress(FakeLagVpnService.sLagMs);
        tvLagVal.setText(FakeLagVpnService.sLagMs + " ms");

        seekLag.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean u) {
                FakeLagVpnService.sLagMs = p;
                tvLagVal.setText(p + " ms");
            }
            public void onStartTrackingTouch(SeekBar s) {}
            public void onStopTrackingTouch(SeekBar s) {}
        });

        btnStart.setOnClickListener(v -> {
            if (FakeLagVpnService.sRunning) {
                stopAll();
            } else {
                requestPermissions();
            }
        });

        updateStartBtn();
    }

    // ─── Permission flow ──────────────────────────────────────
    private void requestPermissions() {
        // 1. VPN permission
        Intent vpnIntent = VpnService.prepare(this);
        if (vpnIntent != null) {
            startActivityForResult(vpnIntent, REQ_VPN);
        } else {
            mVpnGranted = true;
            requestOverlay();
        }
    }

    private void requestOverlay() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && !Settings.canDrawOverlays(this)) {
            Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
            startActivityForResult(i, REQ_OVERLAY);
            Toast.makeText(this, "Cho phép hiển thị trên màn hình → quay lại bấm ON", Toast.LENGTH_LONG).show();
        } else {
            startAll();
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        if (req == REQ_VPN) {
            if (res == RESULT_OK) {
                mVpnGranted = true;
                requestOverlay();
            } else {
                Toast.makeText(this, "Cần quyền VPN để hoạt động", Toast.LENGTH_SHORT).show();
            }
        } else if (req == REQ_OVERLAY) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(this)) {
                startAll();
            } else {
                Toast.makeText(this, "Cần quyền hiển thị overlay", Toast.LENGTH_SHORT).show();
            }
        }
    }

    // ─── Start / Stop ─────────────────────────────────────────
    private void startAll() {
        // Start VPN service
        Intent vpn = new Intent(this, FakeLagVpnService.class);
        vpn.setAction(FakeLagVpnService.ACTION_START);
        vpn.putExtra(FakeLagVpnService.EXTRA_LAG_MS, FakeLagVpnService.sLagMs);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(vpn);
        else startService(vpn);

        // Start overlay
        Intent ov = new Intent(this, OverlayService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(ov);
        else startService(ov);

        updateStartBtn();
    }

    private void stopAll() {
        stopService(new Intent(this, FakeLagVpnService.class));
        stopService(new Intent(this, OverlayService.class));
        FakeLagVpnService.sRunning = false;
        updateStartBtn();
    }

    private void updateStartBtn() {
        if (FakeLagVpnService.sRunning) {
            btnStart.setText("● ON  —  bấm để TẮT");
            btnStart.setBackgroundColor(0xFF00C853);
        } else {
            btnStart.setText("○ OFF  —  bấm để BẬT");
            btnStart.setBackgroundColor(0xFF212121);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateStartBtn();
    }
}
