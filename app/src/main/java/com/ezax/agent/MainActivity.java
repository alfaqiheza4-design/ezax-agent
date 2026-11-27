package com.ezax.agent;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Langsung buka halaman Setup Izin
        startActivity(new Intent(this, PermissionActivity.class));
        finish();
    }
}
