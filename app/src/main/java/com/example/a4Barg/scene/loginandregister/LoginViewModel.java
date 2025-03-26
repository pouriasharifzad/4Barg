package com.example.a4Barg.scene.loginandregister;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.a4Barg.model.SocketRequest;
import com.example.a4Barg.networking.SocketManager;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

public class LoginViewModel extends ViewModel {
    private MutableLiveData<String> status = new MutableLiveData<>();
    private MutableLiveData<String> userId = new MutableLiveData<>();
    private MutableLiveData<String> token = new MutableLiveData<>();
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@(.+)$");

    public LiveData<String> getStatus() {
        return status;
    }

    public LiveData<String> getUserId() {
        return userId;
    }

    public LiveData<String> getToken() {
        return token;
    }

    public void login(Activity context, String email, String password) {
        if (!isValidEmail(email)) {
            status.setValue("فرمت ایمیل نادرست است");
            return;
        }
        if (password.isEmpty()) {
            status.setValue("رمز عبور را وارد کنید");
            return;
        }

        Map<String, String> dataMap = new HashMap<>();
        dataMap.put("email", email);
        dataMap.put("password", password);
        dataMap.put("event", "login");

        try {
            JSONObject data = new JSONObject(dataMap);
            SocketRequest request = new SocketRequest(context, data, new SocketManager.Response() {
                @Override
                public void onResponse(JSONObject object, Boolean isError) throws JSONException {
                    status.postValue(object.getString("message"));
                    if (object.getBoolean("success")) {
                        String receivedUserId = object.getString("userId");
                        String receivedToken = object.getString("token");
                        userId.postValue(receivedUserId);
                        token.postValue(receivedToken);

                        // ذخیره userId و token در SharedPreferences
                        SharedPreferences prefs = context.getSharedPreferences("auth", Context.MODE_PRIVATE);
                        SharedPreferences.Editor editor = prefs.edit();
                        editor.putString("userId", receivedUserId);
                        editor.putString("token", receivedToken);
                        editor.putString("username", object.optString("username", "User")); // اگر username در پاسخ باشد
                        editor.apply();
                    }
                }

                @Override
                public void onError(Throwable t) {
                    status.postValue(t != null ? t.getMessage() : "خطا در ارتباط");
                }
            });
            SocketManager.sendRequest(context, request);
        } catch (JSONException e) {
            status.setValue("خطا در پردازش داده‌ها");
        }
    }

    public void register(Activity context, String username, String email, String password) {
        if (username.isEmpty()) {
            status.setValue("نام کاربری را وارد کنید");
            return;
        }
        if (!isValidEmail(email)) {
            status.setValue("فرمت ایمیل نادرست است");
            return;
        }
        if (password.isEmpty()) {
            status.setValue("رمز عبور را وارد کنید");
            return;
        }

        Map<String, String> dataMap = new HashMap<>();
        dataMap.put("username", username);
        dataMap.put("email", email);
        dataMap.put("password", password);
        dataMap.put("event", "register");

        try {
            JSONObject data = new JSONObject(dataMap);
            SocketRequest request = new SocketRequest(context, data, new SocketManager.Response() {
                @Override
                public void onResponse(JSONObject object, Boolean isError) throws JSONException {
                    status.postValue(object.getString("message"));
                    if (object.getBoolean("success")) {
                        String receivedUserId = object.getString("userId");
                        String receivedToken = object.getString("token");
                        userId.postValue(receivedUserId);
                        token.postValue(receivedToken);

                        // ذخیره userId و token در SharedPreferences
                        SharedPreferences prefs = context.getSharedPreferences("auth", Context.MODE_PRIVATE);
                        SharedPreferences.Editor editor = prefs.edit();
                        editor.putString("userId", receivedUserId);
                        editor.putString("token", receivedToken);
                        editor.putString("username", username);
                        editor.apply();
                    }
                }

                @Override
                public void onError(Throwable t) {
                    status.postValue(t != null ? t.getMessage() : "خطا در ارتباط");
                }
            });
            SocketManager.sendRequest(context, request);
        } catch (JSONException e) {
            status.setValue("خطا در پردازش داده‌ها");
        }
    }

    private boolean isValidEmail(String email) {
        return EMAIL_PATTERN.matcher(email).matches();
    }
}