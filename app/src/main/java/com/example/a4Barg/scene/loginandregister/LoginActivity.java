package com.example.a4Barg.scene.loginandregister;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.a4Barg.R;
import com.example.a4Barg.common.BaseActivity;
import com.example.a4Barg.networking.SocketManager;
import com.example.a4Barg.scene.lobby.LobbyActivity;
import com.example.a4Barg.utils.ConsValue;
import com.example.a4Barg.utils.RandomInteger;
import com.google.android.material.textfield.TextInputLayout;

import org.json.JSONException;
import org.json.JSONObject;

public class LoginActivity extends BaseActivity {
    private LoginViewModel viewModel;
    private EditText emailField, passwordField, usernameField;
    private Button loginButton;
    private TextView toggleRegister, titleText;
    private TextInputLayout usernameInputLayout; // تغییر از LinearLayout به TextInputLayout
    private boolean isRegisterMode = false;
    private String userId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        emailField = findViewById(R.id.email);
        passwordField = findViewById(R.id.password);
        usernameField = findViewById(R.id.username);
        loginButton = findViewById(R.id.loginButton);
        toggleRegister = findViewById(R.id.toggleRegister);
        usernameInputLayout = findViewById(R.id.usernameInputLayout); // استفاده از ID جدید
        titleText = findViewById(R.id.titleText); // دسترسی به عنوان

        viewModel = new ViewModelProvider(this).get(LoginViewModel.class);
        viewModel.getStatus().observe(this, status -> {
            Toast.makeText(this, status, Toast.LENGTH_SHORT).show();
        });
        viewModel.getUserId().observe(this, id -> {
            userId = id;
            Intent intent = new Intent(LoginActivity.this, LobbyActivity.class);
            intent.putExtra("userId", userId);
            startActivity(intent);
            finish();
        });
        viewModel.getToken().observe(this, token -> {
            SharedPreferences prefs = getSharedPreferences("auth", MODE_PRIVATE);
            prefs.edit().putString("token", token).apply();
            ConsValue.token = token;
        });

        // چک کردن توکن ذخیره‌شده برای ورود خودکار
        SharedPreferences prefs = getSharedPreferences("auth", MODE_PRIVATE);
        String token = prefs.getString("token", null);
        if (token != null) {
            try {
                SocketManager.verifyToken(this, token, new SocketManager.Response() {
                    @Override
                    public void onResponse(JSONObject object, Boolean isError) throws JSONException {
                        if (object.getBoolean("success")) {
                            // توکن معتبره، مستقیم به LobbyActivity برو
                            userId = object.getString("userId");
                            Intent intent = new Intent(LoginActivity.this, LobbyActivity.class);
                            intent.putExtra("userId", userId);
                            startActivity(intent);
                            finish();
                        } else {
                            String message = object.getString("message").toLowerCase();
                            if (message.contains("jwt expired")) {
                                // تلاش برای Refresh Token
                                JSONObject requestData = new JSONObject();
                                requestData.put("requestId", String.valueOf(RandomInteger.getRandomId()));
                                requestData.put("token", token);
                                SocketManager.refreshToken(LoginActivity.this, requestData, new SocketManager.Response() {
                                    @Override
                                    public void onResponse(JSONObject object, Boolean isError) throws JSONException {
                                        if (object.getBoolean("success")) {
                                            String newToken = object.getString("token");
                                            prefs.edit().putString("token", newToken).apply();
                                            ConsValue.token = newToken;
                                            Toast.makeText(LoginActivity.this, "توکن با موفقیت به‌روزرسانی شد", Toast.LENGTH_SHORT).show();
                                            // دوباره اعتبارسنجی توکن جدید
                                            SocketManager.verifyToken(LoginActivity.this, newToken, this);
                                        } else {
                                            Toast.makeText(LoginActivity.this, "توکن منقضی شده است، لطفاً دوباره وارد شوید", Toast.LENGTH_LONG).show();
                                            prefs.edit().remove("token").apply();
                                        }
                                    }

                                    @Override
                                    public void onError(Throwable t) {
                                        Toast.makeText(LoginActivity.this, "خطا در دریافت توکن جدید: " + t.getMessage(), Toast.LENGTH_LONG).show();
                                        prefs.edit().remove("token").apply();
                                    }
                                });
                            } else {
                                Toast.makeText(LoginActivity.this, "خطا در اعتبارسنجی: " + object.getString("message"), Toast.LENGTH_LONG).show();
                                prefs.edit().remove("token").apply();
                            }
                        }
                    }

                    @Override
                    public void onError(Throwable t) {
                        Toast.makeText(LoginActivity.this, "خطا در اتصال به سرور: " + t.getMessage(), Toast.LENGTH_LONG).show();
                        prefs.edit().remove("token").apply();
                    }
                });
            } catch (JSONException e) {
                e.printStackTrace();
                Toast.makeText(this, "خطا در پردازش توکن", Toast.LENGTH_LONG).show();
                prefs.edit().remove("token").apply();
            }
        }

        loginButton.setOnClickListener(v -> {
            String email = emailField.getText().toString();
            String password = passwordField.getText().toString();
            if (isRegisterMode) {
                String username = usernameField.getText().toString();
                viewModel.register(this, username, email, password);
            } else {
                viewModel.login(this, email, password);
            }
        });

        toggleRegister.setOnClickListener(v -> {
            isRegisterMode = !isRegisterMode;
            if (isRegisterMode) {
                usernameInputLayout.setVisibility(View.VISIBLE);
                loginButton.setText("ثبت‌نام");
                toggleRegister.setText("حساب دارید؟ وارد شوید");
                // انیمیشن برای تغییر متن عنوان
                AlphaAnimation fadeOut = new AlphaAnimation(1.0f, 0.0f);
                fadeOut.setDuration(300);
                fadeOut.setFillAfter(true);
                titleText.startAnimation(fadeOut);
                fadeOut.setAnimationListener(new AlphaAnimation.AnimationListener() {
                    @Override
                    public void onAnimationStart(android.view.animation.Animation animation) {}

                    @Override
                    public void onAnimationEnd(android.view.animation.Animation animation) {
                        titleText.setText("ثبت‌نام");
                        AlphaAnimation fadeIn = new AlphaAnimation(0.0f, 1.0f);
                        fadeIn.setDuration(300);
                        fadeIn.setFillAfter(true);
                        titleText.startAnimation(fadeIn);
                    }

                    @Override
                    public void onAnimationRepeat(android.view.animation.Animation animation) {}
                });
            } else {
                usernameInputLayout.setVisibility(View.GONE);
                loginButton.setText("ورود");
                toggleRegister.setText("حساب ندارید؟ ثبت‌نام کنید");
                // انیمیشن برای تغییر متن عنوان
                AlphaAnimation fadeOut = new AlphaAnimation(1.0f, 0.0f);
                fadeOut.setDuration(300);
                fadeOut.setFillAfter(true);
                titleText.startAnimation(fadeOut);
                fadeOut.setAnimationListener(new AlphaAnimation.AnimationListener() {
                    @Override
                    public void onAnimationStart(android.view.animation.Animation animation) {}

                    @Override
                    public void onAnimationEnd(android.view.animation.Animation animation) {
                        titleText.setText("ورود به بازی");
                        AlphaAnimation fadeIn = new AlphaAnimation(0.0f, 1.0f);
                        fadeIn.setDuration(300);
                        fadeIn.setFillAfter(true);
                        titleText.startAnimation(fadeIn);
                    }

                    @Override
                    public void onAnimationRepeat(android.view.animation.Animation animation) {}
                });
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // SocketManager.disconnect();
    }
}