package com.ezax.agent;

import android.app.Activity;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.TextView;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

// Kita pakai Activity biasa, bukan AppCompatActivity
public class MainActivity extends Activity { 

    private TextView statusText;
    private Handler handler = new Handler(Looper.getMainLooper());
    
    // ⚠️ GANTI IP INI KALAU PERLU ⚠️
    private static final String SERVER_URL = "http://127.0.0.1:5000/api/update"; 

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        statusText = new TextView(this);
        statusText.setTextSize(24f);
        statusText.setTextColor(0xFF00FF00); 
        statusText.setBackgroundColor(0xFF000000); 
        statusText.setPadding(50, 50, 50, 50);
        setContentView(statusText);

        startSendingData();
    }

    private void startSendingData() {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                int batteryLevel = getBatteryLevel();
                String networkType = getNetworkType();
                
                statusText.setText("EZAX AGENT\n\nStatus: ONLINE\nBaterai: " + batteryLevel + "%\nJaringan: " + networkType + "\n\nMengirim data...");

                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        sendDataToServer(batteryLevel, networkType);
                    }
                }).start();

                handler.postDelayed(this, 5000);
            }
        }, 1000);
    }

    private int getBatteryLevel() {
        BatteryManager bm = (BatteryManager) getSystemService(Context.BATTERY_SERVICE);
        return bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
    }

    private String getNetworkType() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkCapabilities nc = cm.getNetworkCapabilities(cm.getActiveNetwork());
        if (nc == null) return "Offline";
        if (nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return "WiFi";
        if (nc.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) return "Data Seluler";
        return "Lainnya";
    }

    private void sendDataToServer(int battery, String network) {
        try {
            URL url = new URL(SERVER_URL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);

            String jsonInputString = String.format("{\"battery\": %d, \"network\": \"%s\"}", battery, network);

            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = jsonInputString.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            int responseCode = conn.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        statusText.append("\n✅ Terkirim ke Server!");
                    }
                });
            } else {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        statusText.append("\n❌ Gagal: " + responseCode);
                    }
                });
            }
            conn.disconnect();
        } catch (Exception e) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    statusText.append("\n⚠️ Error Jaringan: " + e.getMessage());
                }
            });
        }
    }
}
