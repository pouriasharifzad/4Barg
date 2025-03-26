package com.example.a4Barg.scene.splash;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.a4Barg.networking.SocketManager;
import com.example.a4Barg.utils.ConsValue;
import com.example.a4Barg.utils.RandomInteger;

import org.json.JSONException;
import org.json.JSONObject;

public class SplashViewModel extends ViewModel {
    private static final int MAX_RETRIES = 3;
    private int retryCount = 0;

    private final MutableLiveData<Boolean> isTokenValid = new MutableLiveData<>();
    private final MutableLiveData<String> userId = new MutableLiveData<>();
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();
    private final MutableLiveData<Boolean> connectionFailed = new MutableLiveData<>();
    private final MutableLiveData<String> refreshTokenResult = new MutableLiveData<>();

    public void checkConnection(Context context, SharedPreferences prefs) {
        String token = prefs.getString("token", null);
        if (token != null) {
            attemptTokenVerification(context, token, prefs);
            ConsValue.token = token;
        } else {
            // توکن وجود نداره، به LoginActivity برو
            errorMessage.postValue("no_token");
        }
    }

    private void attemptTokenVerification(Context context, String token, SharedPreferences prefs) {
        try {
            SocketManager.verifyToken(context, token, new SocketManager.Response() {
                @Override
                public void onResponse(JSONObject object, Boolean isError) throws JSONException {
                    if (object.getBoolean("success")) {
                        // توکن معتبره
                        String receivedUserId = object.getString("userId");
                        isTokenValid.postValue(true);
                        userId.postValue(receivedUserId);

                        // ذخیره userId در SharedPreferences
                        SharedPreferences.Editor editor = prefs.edit();
                        editor.putString("userId", receivedUserId);
                        editor.apply();
                    } else {
                        String message = object.getString("message").toLowerCase();
                        if (message.contains("jwt expired")) {
                            // توکن منقضی شده، تلاش برای Refresh Token
                            attemptRefreshToken(context, prefs);
                        } else {
                            errorMessage.postValue("خطا در اعتبارسنجی: " + object.getString("message"));
                            prefs.edit().remove("token").apply();
                        }
                    }
                }

                @Override
                public void onError(Throwable t) {
                    handleConnectionFailure(context, prefs);
                }
            });
        } catch (JSONException e) {
            handleConnectionFailure(context, prefs);
        }
    }

    private void attemptRefreshToken(Context context, SharedPreferences prefs) {
        try {
            JSONObject requestData = new JSONObject();
            requestData.put("requestId", String.valueOf(RandomInteger.getRandomId()));
            requestData.put("token", prefs.getString("token", null));

            SocketManager.refreshToken(context, requestData, new SocketManager.Response() {
                @Override
                public void onResponse(JSONObject object, Boolean isError) throws JSONException {
                    if (object.getBoolean("success")) {
                        String newToken = object.getString("token");
                        prefs.edit().putString("token", newToken).apply();
                        refreshTokenResult.postValue("success");
                        // دوباره توکن جدید رو اعتبارسنجی کن
                        attemptTokenVerification(context, newToken, prefs);
                    } else {
                        refreshTokenResult.postValue("failed");
                        errorMessage.postValue("توکن منقضی شده است، لطفاً دوباره وارد شوید");
                        prefs.edit().remove("token").apply();
                    }
                }

                @Override
                public void onError(Throwable t) {
                    refreshTokenResult.postValue("failed");
                    errorMessage.postValue("خطا در دریافت توکن جدید: " + t.getMessage());
                    prefs.edit().remove("token").apply();
                }
            });
        } catch (JSONException e) {
            refreshTokenResult.postValue("failed");
            errorMessage.postValue("خطا در پردازش درخواست توکن جدید");
            prefs.edit().remove("token").apply();
        }
    }

    private void handleConnectionFailure(Context context, SharedPreferences prefs) {
        retryCount++;
        if (retryCount < MAX_RETRIES) {
            errorMessage.postValue("retry_" + retryCount); // برای نمایش پیام تلاش
            // تلاش مجدد بعد از 2 ثانیه
            new android.os.Handler().postDelayed(() -> checkConnection(context, prefs), 2000);
        } else {
            connectionFailed.postValue(true); // دیالوگ خطا رو نمایش بده
        }
    }

    public LiveData<Boolean> getIsTokenValid() {
        return isTokenValid;
    }

    public LiveData<String> getUserId() {
        return userId;
    }

    public LiveData<String> getErrorMessage() {
        return errorMessage;
    }

    public LiveData<Boolean> getConnectionFailed() {
        return connectionFailed;
    }

    public LiveData<String> getRefreshTokenResult() {
        return refreshTokenResult;
    }
}