package com.example.a4Barg.scene.splash;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.widget.Toast;
import androidx.lifecycle.ViewModelProvider;
import com.example.a4Barg.R;
import com.example.a4Barg.common.BaseActivity;
import com.example.a4Barg.scene.loginandregister.LoginActivity;
import com.example.a4Barg.scene.lobby.LobbyActivity;
import com.example.a4Barg.utils.ConsValue;

@SuppressLint("CustomSplashScreen")
public class SplashActivity extends BaseActivity {
    private SplashViewModel viewModel;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        prefs = getSharedPreferences("auth", MODE_PRIVATE);
        viewModel = new ViewModelProvider(this).get(SplashViewModel.class);

        // تاخیر برای نمایش Splash (2 ثانیه) و شروع فرآیند اتصال
        new Handler().postDelayed(() -> viewModel.checkConnection(this, prefs), 2000);

        // مشاهده نتیجه اعتبارسنجی توکن
        viewModel.getIsTokenValid().observe(this, isValid -> {
            if (isValid != null && isValid) {
                viewModel.getUserId().observe(this, userId -> {
                    ConsValue.isRegistered = true;
                    Intent intent = new Intent(SplashActivity.this, LobbyActivity.class);
                    intent.putExtra("userId", userId);
                    startActivity(intent);
                    finish();
                });
            }
        });

        // مشاهده پیام‌های خطا یا تلاش‌ها
        viewModel.getErrorMessage().observe(this, message -> {
            if (message != null) {
                if (message.startsWith("retry_")) {
                    int retryCount = Integer.parseInt(message.split("_")[1]);
                    Toast.makeText(this, "تلاش برای اتصال (" + retryCount + "/3)...", Toast.LENGTH_SHORT).show();
                } else if (message.equals("no_token")) {
                    Intent intent = new Intent(SplashActivity.this, LoginActivity.class);
                    startActivity(intent);
                    finish();
                } else {
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                    if (!message.startsWith("تلاش برای اتصال")) {
                        Intent intent = new Intent(SplashActivity.this, LoginActivity.class);
                        startActivity(intent);
                        finish();
                    }
                }
            }
        });

        // مشاهده نتیجه Refresh Token
        viewModel.getRefreshTokenResult().observe(this, result -> {
            if (result != null && result.equals("success")) {
                Toast.makeText(this, "توکن با موفقیت به‌روزرسانی شد", Toast.LENGTH_SHORT).show();
            }
        });

        // نمایش دیالوگ در صورت عدم موفقیت اتصال
        viewModel.getConnectionFailed().observe(this, failed -> {
            if (failed != null && failed) {
                showConnectionErrorDialog();
            }
        });
    }

    private void showConnectionErrorDialog() {
        new AlertDialog.Builder(this)
                .setTitle("خطای ارتباط")
                .setMessage("ارتباط به سرور برقرار نشد")
                .setCancelable(false)
                .setPositiveButton("خروج", (dialog, which) -> {
                    finish(); // بستن برنامه
                })
                .show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // SocketManager.disconnect();
    }
}