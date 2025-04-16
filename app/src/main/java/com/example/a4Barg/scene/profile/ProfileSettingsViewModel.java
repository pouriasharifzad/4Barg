package com.example.a4Barg.scene.profile;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Log;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.a4Barg.model.SocketRequest;
import com.example.a4Barg.networking.SocketManager;

import org.json.JSONException;
import org.json.JSONObject;

public class ProfileSettingsViewModel extends AndroidViewModel {

    private final MutableLiveData<String> username = new MutableLiveData<>();
    private final MutableLiveData<String> email = new MutableLiveData<>();
    private final MutableLiveData<Integer> experience = new MutableLiveData<>();
    private final MutableLiveData<Integer> coins = new MutableLiveData<>();
    private final MutableLiveData<byte[]> avatar = new MutableLiveData<>();
    private final MutableLiveData<String> toastMessage = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isUsernameEditable = new MutableLiveData<>(false);
    private final MutableLiveData<String> avatarStatusMessage = new MutableLiveData<>();
    private final MutableLiveData<String> avatarStatus = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(true);

    private String userId;
    private Uri imageUri;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private int pendingRequests = 0;
    private boolean hasCheckedPendingAvatar = false; // فلگ برای جلوگیری از فراخوانی تکراری
    private boolean hasCheckedAvatarStatus = false; // فلگ برای جلوگیری از فراخوانی تکراری

    public ProfileSettingsViewModel(Application application) {
        super(application);
        SharedPreferences prefs = application.getSharedPreferences("auth", Context.MODE_PRIVATE);
        userId = prefs.getString("userId", null);
        if (userId != null) {
            avatarStatus.setValue(null);
            avatarStatusMessage.setValue(null);
            Log.d("ProfileSettings", "Initial values set: avatarStatus=null, avatarStatusMessage=null");

            pendingRequests = 0;
            loadProfile();
            if (!hasCheckedPendingAvatar) {
                checkPendingAvatar();
                hasCheckedPendingAvatar = true;
            }
            if (!hasCheckedAvatarStatus) {
                checkAvatarStatus();
                hasCheckedAvatarStatus = true;
            }
            setupAvatarStatusListener();

            mainHandler.postDelayed(() -> {
                if (pendingRequests > 0) {
                    Log.w("ProfileSettings", "Loading timeout reached, forcing isLoading to false");
                    pendingRequests = 0;
                    isLoading.setValue(false);
                }
            }, 10000);
        } else {
            isLoading.setValue(false);
        }
    }

    public LiveData<String> getUsername() {
        return username;
    }

    public LiveData<String> getEmail() {
        return email;
    }

    public LiveData<Integer> getExperience() {
        return experience;
    }

    public LiveData<Integer> getCoins() {
        return coins;
    }

    public LiveData<byte[]> getAvatar() {
        return avatar;
    }

    public LiveData<String> getToastMessage() {
        return toastMessage;
    }

    public LiveData<Boolean> getIsUsernameEditable() {
        return isUsernameEditable;
    }

    public LiveData<String> getAvatarStatusMessage() {
        return avatarStatusMessage;
    }

    public LiveData<String> getAvatarStatus() {
        return avatarStatus;
    }

    public LiveData<Boolean> getIsLoading() {
        return isLoading;
    }

    public void setImageUri(Uri uri) {
        this.imageUri = uri;
    }

    public void checkPendingAvatar() {
        try {
            pendingRequests++;
            Log.d("ProfileSettings", "checkPendingAvatar: pendingRequests=" + pendingRequests);

            JSONObject data = new JSONObject();
            data.put("event", "check_pending_avatar");
            data.put("userId", userId);

            SocketRequest request = new SocketRequest(null, data, new SocketManager.Response() {
                @Override
                public void onResponse(JSONObject response, Boolean isError) throws JSONException {
                    mainHandler.post(() -> {
                        try {
                            if (!isError && response.getBoolean("success")) {
                                if (response.has("pendingAvatar") && !response.isNull("pendingAvatar")) {
                                    avatarStatus.setValue("pending");
                                    avatarStatusMessage.setValue("درخواست تغییر عکس شما در حال بررسی است");
                                    Log.d("ProfileSettings", "checkPendingAvatar: SetGRA avatarStatus=pending");
                                } else {
                                    avatarStatus.setValue(null);
                                    avatarStatusMessage.setValue(null);
                                    Log.d("ProfileSettings", "checkPendingAvatar: No pending avatar");
                                }
                            } else {
                                avatarStatus.setValue(null);
                                avatarStatusMessage.setValue(null);
                                Log.d("ProfileSettings", "checkPendingAvatar: Error or unsuccessful response");
                            }
                        } catch (JSONException e) {
                            throw new RuntimeException(e);
                        }
                        completeRequest();
                    });
                }

                @Override
                public void onError(Throwable t) {
                    mainHandler.post(() -> {
                        avatarStatus.setValue(null);
                        avatarStatusMessage.setValue(null);
                        Log.e("ProfileSettings", "checkPendingAvatar: Error - " + t.getMessage());
                        completeRequest();
                    });
                }
            });

            SocketManager.sendRequest(getApplication(), request);
        } catch (Exception e) {
            mainHandler.post(() -> {
                avatarStatus.setValue(null);
                avatarStatusMessage.setValue(null);
                Log.e("ProfileSettings", "checkPendingAvatar: Exception - " + e.getMessage());
                completeRequest();
            });
        }
    }

    public void checkAvatarStatus() {
        try {
            pendingRequests++;
            Log.d("ProfileSettings", "checkAvatarStatus: pendingRequests=" + pendingRequests);

            JSONObject data = new JSONObject();
            data.put("event", "check_avatar_status");
            data.put("userId", userId);

            SocketRequest request = new SocketRequest(null, data, new SocketManager.Response() {
                @Override
                public void onResponse(JSONObject response, Boolean isError) throws JSONException {
                    mainHandler.post(() -> {
                        try {
                            if (!isError && response.getBoolean("success")) {
                                String status = response.getString("status");
                                String message = response.getString("message");
                                avatarStatus.setValue(status);
                                avatarStatusMessage.setValue(message);
                                Log.d("ProfileSettings", "checkAvatarStatus: Set avatarStatus=" + status);
                            } else {
                                avatarStatus.setValue(null);
                                avatarStatusMessage.setValue(null);
                                Log.d("ProfileSettings", "checkAvatarStatus: Error or unsuccessful response");
                            }
                        } catch (JSONException e) {
                            throw new RuntimeException(e);
                        }
                        completeRequest();
                    });
                }

                @Override
                public void onError(Throwable t) {
                    mainHandler.post(() -> {
                        avatarStatus.setValue(null);
                        avatarStatusMessage.setValue(null);
                        Log.e("ProfileSettings", "checkAvatarStatus: Error - " + t.getMessage());
                        completeRequest();
                    });
                }
            });

            SocketManager.sendRequest(getApplication(), request);
        } catch (Exception e) {
            mainHandler.post(() -> {
                avatarStatus.setValue(null);
                avatarStatusMessage.setValue(null);
                Log.e("ProfileSettings", "checkAvatarStatus: Exception - " + e.getMessage());
                completeRequest();
            });
        }
    }

    private void setupAvatarStatusListener() {
        SocketManager.addCustomListener("avatar_status", data -> {
            try {
                String status = data.getString("status");
                String message = data.getString("message");
                mainHandler.post(() -> {
                    avatarStatus.setValue(status);
                    avatarStatusMessage.setValue(message);
                    Log.d("ProfileSettings", "setupAvatarStatusListener: Received avatar_status, set avatarStatus=" + status);
                    if (status.equals("approved")) {
                        loadProfile();
                        SharedPreferences prefs = getApplication().getSharedPreferences("avatar_status", Context.MODE_PRIVATE);
                        SharedPreferences.Editor editor = prefs.edit();
                        editor.putBoolean("has_pending_avatar", false);
                        editor.apply();
                        Log.d("ProfileSettings", "setupAvatarStatusListener: Avatar approved");
                    } else if (status.equals("rejected")) {
                        SharedPreferences prefs = getApplication().getSharedPreferences("avatar_status", Context.MODE_PRIVATE);
                        SharedPreferences.Editor editor = prefs.edit();
                        editor.putBoolean("has_pending_avatar", false);
                        editor.apply();
                        Log.d("ProfileSettings", "setupAvatarStatusListener: Avatar rejected");
                    }
                });
            } catch (JSONException e) {
                Log.e("ProfileSettings", "Error in avatar_status listener: " + e.getMessage());
            }
        });
    }

    private void loadProfile() {
        try {
            pendingRequests++;
            Log.d("ProfileSettings", "loadProfile: pendingRequests=" + pendingRequests);

            JSONObject data = new JSONObject();
            data.put("event", "get_profile");
            data.put("userId", userId);

            SocketRequest request = new SocketRequest(null, data, new SocketManager.Response() {
                @Override
                public void onResponse(JSONObject response, Boolean isError) throws JSONException {
                    mainHandler.post(() -> {
                        try {
                            if (!isError && response.getBoolean("success")) {
                                JSONObject data = response.getJSONObject("data");
                                username.setValue(data.getString("username"));
                                Log.d("ProfileSettings", "Username set to: " + data.getString("username"));
                                email.setValue(data.getString("email"));
                                experience.setValue(data.getInt("experience"));
                                coins.setValue(data.getInt("coins"));
                                if (data.has("avatar") && !data.isNull("avatar")) {
                                    avatar.setValue(android.util.Base64.decode(data.getString("avatar"), android.util.Base64.DEFAULT));
                                } else {
                                    avatar.setValue(null);
                                }
                            } else {
                                toastMessage.setValue("خطا: " + response.getString("message"));
                            }
                        } catch (JSONException e) {
                            throw new RuntimeException(e);
                        }
                        completeRequest();
                    });
                }

                @Override
                public void onError(Throwable t) {
                    mainHandler.post(() -> {
                        toastMessage.setValue("خطا در بارگذاری پروفایل");
                        Log.e("ProfileSettings", "loadProfile: Error - " + t.getMessage());
                        completeRequest();
                    });
                }
            });

            SocketManager.sendRequest(getApplication(), request);
        } catch (Exception e) {
            mainHandler.post(() -> {
                toastMessage.setValue("خطا در بارگذاری پروفایل");
                Log.e("ProfileSettings", "loadProfile: Exception - " + e.getMessage());
                completeRequest();
            });
        }
    }

    private void completeRequest() {
        pendingRequests--;
        Log.d("ProfileSettings", "completeRequest: pendingRequests=" + pendingRequests);
        if (pendingRequests <= 0) {
            isLoading.setValue(false);
            Log.d("ProfileSettings", "All requests completed, isLoading set to false");
        }
    }

    public void toggleUsernameEdit() {
        isUsernameEditable.setValue(true);
    }

    public void saveProfile(String newUsername) {
        if (newUsername == null || newUsername.trim().isEmpty()) {
            toastMessage.setValue("لطفاً نام کاربری رو وارد کن");
            return;
        }

        try {
            JSONObject data = new JSONObject();
            data.put("event", "update_profile");
            data.put("userId", userId);
            data.put("username", newUsername);

            SocketRequest request = new SocketRequest(null, data, new SocketManager.Response() {
                @Override
                public void onResponse(JSONObject response, Boolean isError) throws JSONException {
                    if (!isError && response.getBoolean("success")) {
                        mainHandler.post(() -> {
                            try {
                                toastMessage.setValue(response.getString("message"));
                            } catch (JSONException e) {
                                throw new RuntimeException(e);
                            }
                            isUsernameEditable.setValue(false);
                            loadProfile();
                        });
                    } else {
                        mainHandler.post(() -> {
                            try {
                                toastMessage.setValue(response.getString("message"));
                            } catch (JSONException e) {
                                throw new RuntimeException(e);
                            }
                            try {
                                if (response.getString("message").contains("نام کاربری قبلاً تغییر کرده")) {
                                    isUsernameEditable.setValue(false);
                                }
                            } catch (JSONException e) {
                                throw new RuntimeException(e);
                            }
                        });
                    }
                }

                @Override
                public void onError(Throwable t) {
                    mainHandler.post(() -> toastMessage.setValue("خطا در ذخیره‌سازی"));
                }
            });

            if (imageUri != null) {
                SocketManager.uploadAvatar(getApplication(), userId, imageUri, new SocketManager.UploadCallback() {
                    @Override
                    public void onSuccess() {
                        try {
                            SharedPreferences prefs = getApplication().getSharedPreferences("avatar_status", Context.MODE_PRIVATE);
                            SharedPreferences.Editor editor = prefs.edit();
                            editor.putBoolean("has_pending_avatar", true);
                            editor.putBoolean("has_shown_approved_dialog", false);
                            editor.putBoolean("has_shown_rejected_dialog", false);
                            editor.apply();

                            SocketManager.sendRequest(getApplication(), request);
                        } catch (JSONException e) {
                            mainHandler.post(() -> {
                                toastMessage.setValue("خطا در ذخیره‌سازی");
                                e.printStackTrace();
                            });
                        }
                    }

                    @Override
                    public void onError(String error) {
                        mainHandler.post(() -> toastMessage.setValue("خطا در آپلود تصویر: " + error));
                    }
                });
            } else {
                SocketManager.sendRequest(getApplication(), request);
            }
        } catch (Exception e) {
            mainHandler.post(() -> {
                toastMessage.setValue("خطا در ذخیره‌سازی");
                e.printStackTrace();
            });
        }
    }
}