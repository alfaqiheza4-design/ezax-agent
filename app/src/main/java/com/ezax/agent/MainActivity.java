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

public class MainActivity extends Activity { 

    private TextView statusText;
    private Handler handler = new Handler(Looper.getMainLooper());
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
        try {
            BatteryManager bm = (BatteryManager) getSystemService(Context.BATTERY_SERVICE);
            if (bm != null) {
                return bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
            }
        } catch (Exception e) {
            return -1; // Kalau gagal ambil baterai
        }
        return 0;
    }

    private String getNetworkType() {
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return "Error";
            
            android.net.Network activeNetwork = cm.getActiveNetwork();
            if (activeNetwork == null) return "Offline"; // Kalau nggak ada internet sama sekali
            
            NetworkCapabilities nc = cm.getNetworkCapabilities(activeNetwork);
            if (nc == null) return "Unknown";
            
            if (nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return "WiFi";
            if (nc.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) return "Data Seluler";
            return "Lainnya";
        } catch (Exception e) {
            return "Error Jaringan";
        }
    }

    private void sendDataToServer(int battery, String network) {
        try {
            URL url = new URL(SERVER_URL);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            conn.setConnectTimeout(5000); // Kasih waktu 5 detik buat konek

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
