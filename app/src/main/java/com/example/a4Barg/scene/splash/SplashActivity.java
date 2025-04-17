package com.example.a4Barg.scene.splash;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.core.view.ViewCompat;
import androidx.lifecycle.ViewModelProvider;

import com.example.a4Barg.R;
import com.example.a4Barg.common.BaseActivity;
import com.example.a4Barg.scene.loginandregister.LoginActivity;
import com.example.a4Barg.scene.lobby.LobbyActivity;
import com.example.a4Barg.utils.ConsValue;

import android.animation.ValueAnimator;
import android.view.animation.LinearInterpolator;

@SuppressLint("CustomSplashScreen")
public class SplashActivity extends BaseActivity {
    private SplashViewModel viewModel;
    private SharedPreferences prefs;
    private View loadingFill;
    private ValueAnimator loadingAnimator;
    private boolean isConnectionSuccessful = false;
    private LinearLayout loadingContainer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        prefs = getSharedPreferences("auth", MODE_PRIVATE);
        viewModel = new ViewModelProvider(this).get(SplashViewModel.class);

        // مقداردهی کادر پرشونده
        loadingContainer = findViewById(R.id.loading_container);
        loadingFill = findViewById(R.id.loading_fill);

        // اطمینان از اینکه کادر بیرونی رندر شده است
        loadingContainer.getViewTreeObserver().addOnGlobalLayoutListener(
                new android.view.ViewTreeObserver.OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                        // حذف listener برای جلوگیری از فراخوانی چندباره
                        loadingContainer.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                        // شروع انیمیشن بعد از رندر کامل
                        startInitialLoadingAnimation(loadingContainer);
                    }
                });
    }

    private void startInitialLoadingAnimation(LinearLayout container) {
        // تنظیم عرض اولیه به صفر
        loadingFill.getLayoutParams().width = 0;
        loadingFill.requestLayout();

        // محاسبه عرض نهایی با توجه به padding و margin
        int containerWidth = container.getWidth() - container.getPaddingLeft() - container.getPaddingRight();
        // محاسبه margin کادر داخلی (5dp از هر طرف)
        int marginInPx = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 10, getResources().getDisplayMetrics()); // 5dp از چپ + 5dp از راست
        int maxFillWidth = containerWidth - marginInPx; // عرض کادر داخلی با احتساب margin
        float targetFraction = 0.5f; // پر شدن تا 50 درصد

        // ایجاد انیمیشن برای پر شدن تا 50 درصد
        loadingAnimator = ValueAnimator.ofInt(0, (int) (maxFillWidth * targetFraction));
        loadingAnimator.setDuration(1700); // 3 ثانیه برای پر شدن تا 50 درصد
        loadingAnimator.setInterpolator(new LinearInterpolator()); // برای حرکت یکنواخت و نرم
        loadingAnimator.addUpdateListener(animation -> {
            int animatedWidth = (int) animation.getAnimatedValue();
            loadingFill.getLayoutParams().width = animatedWidth;
            loadingFill.requestLayout();
        });
        loadingAnimator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                // بعد از اتمام انیمیشن اولیه، درخواست به سرور ارسال می‌شود
                checkServerConnection();
            }
        });
        loadingAnimator.start();
    }

    private void checkServerConnection() {
        // ارسال درخواست به سرور
        viewModel.checkConnection(this, prefs);

        // مشاهده نتیجه اعتبارسنجی توکن
        viewModel.getIsTokenValid().observe(this, isValid -> {
            if (isValid != null && isValid) {
                isConnectionSuccessful = true;
                startFinalLoadingAnimation(); // شروع انیمیشن نهایی در صورت موفقیت
            }
        });

        // مشاهده پیام‌های خطا یا تلاش‌ها
        viewModel.getErrorMessage().observe(this, message -> {
            if (message != null) {
                if (message.startsWith("retry_")) {
                    int retryCount = Integer.parseInt(message.split("_")[1]);
                    Toast.makeText(this, "تلاش برای اتصال (" + retryCount + "/3)...", Toast.LENGTH_SHORT).show();
                } else if (message.equals("no_token")) {
                    isConnectionSuccessful = false;
                    stopLoadingAtHalf(); // توقف در 50 درصد
                    navigateToLoginActivity();
                } else {
                    isConnectionSuccessful = false;
                    stopLoadingAtHalf(); // توقف در 50 درصد
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                    if (!message.startsWith("تلاش برای اتصال")) {
                        navigateToLoginActivity();
                    }
                }
            }
        });

        // مشاهده نتیجه Refresh Token
        viewModel.getRefreshTokenResult().observe(this, result -> {
            if (result != null && result.equals("success")) {
                Toast.makeText(this, "توکن با موفقیت به‌روزرسانی شد", Toast.LENGTH_SHORT).show();
            } else if (result != null && result.equals("failed")) {
                isConnectionSuccessful = false;
                stopLoadingAtHalf(); // توقف در 50 درصد
            }
        });

        // نمایش دیالوگ در صورت عدم موفقیت اتصال
        viewModel.getConnectionFailed().observe(this, failed -> {
            if (failed != null && failed) {
                isConnectionSuccessful = false;
                stopLoadingAtHalf(); // توقف در 50 درصد
                showConnectionErrorDialog();
            }
        });
    }

    private void startFinalLoadingAnimation() {
        // محاسبه عرض نهایی با توجه به padding و margin
        int containerWidth = loadingContainer.getWidth() -
                loadingContainer.getPaddingLeft() -
                loadingContainer.getPaddingRight();
        int marginInPx = (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 10, getResources().getDisplayMetrics()); // 5dp از چپ + 5dp از راست
        int maxFillWidth = containerWidth - marginInPx; // عرض کادر داخلی با احتساب margin

        // ایجاد انیمیشن برای پر شدن از 50 درصد به 100 درصد
        ValueAnimator finalAnimator = ValueAnimator.ofInt(loadingFill.getWidth(), maxFillWidth);
        finalAnimator.setDuration(1700); // 3 ثانیه برای پر شدن از 50 درصد به 100 درصد
        finalAnimator.setInterpolator(new LinearInterpolator()); // برای حرکت یکنواخت و نرم
        finalAnimator.addUpdateListener(animation -> {
            int animatedWidth = (int) animation.getAnimatedValue();
            loadingFill.getLayoutParams().width = animatedWidth;
            loadingFill.requestLayout();
        });
        finalAnimator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                // بعد از اتمام انیمیشن نهایی، به اکتیویتی مربوطه منتقل می‌شود
                navigateToLobbyActivity();
            }
        });
        finalAnimator.start();
    }

    private void stopLoadingAtHalf() {
        if (loadingAnimator != null) {
            loadingAnimator.cancel(); // توقف انیمیشن
            // تنظیم عرض به 50 درصد با توجه به padding و margin
            int containerWidth = loadingContainer.getWidth() -
                    loadingContainer.getPaddingLeft() -
                    loadingContainer.getPaddingRight();
            int marginInPx = (int) TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP, 10, getResources().getDisplayMetrics());
            int maxFillWidth = containerWidth - marginInPx;
            loadingFill.getLayoutParams().width = (int) (maxFillWidth * 0.5f);
            loadingFill.requestLayout();
        }
    }

    private void navigateToLobbyActivity() {
        viewModel.getUserId().observe(this, userId -> {
            ConsValue.isRegistered = true;
            Intent intent = new Intent(SplashActivity.this, LobbyActivity.class);
            intent.putExtra("userId", userId);
            startActivity(intent);
            finish();
        });
    }

    private void navigateToLoginActivity() {
        Intent intent = new Intent(SplashActivity.this, LoginActivity.class);
        startActivity(intent);
        finish();
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
        if (loadingAnimator != null) {
            loadingAnimator.cancel();
        }
        // SocketManager.disconnect();
    }
}