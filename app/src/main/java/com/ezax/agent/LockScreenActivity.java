package com.ezax.agent;

import android.app.Activity;
import android.os.Bundle;

public class LockScreenActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_lock);
    }

    @Override
    public void onBackPressed() {
        // Sengaja dikosongin biar tombol back gak bisa keluar
    }
}

