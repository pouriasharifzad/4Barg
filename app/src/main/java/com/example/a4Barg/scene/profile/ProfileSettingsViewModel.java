package com.example.a4Barg.scene.profile;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.a4Barg.model.SocketRequest;
import com.example.a4Barg.networking.SocketManager;

import org.json.JSONException;
import org.json.JSONObject;

public class ProfileSettingsViewModel extends AndroidViewModel {

    private static final String TAG = "ProfileSettingsVM";

    // --- SharedPreferences Constants ---
    private static final String PREF_NAME = "avatar_status_prefs";
    private static final String KEY_UNSEEN_REJECTION = "unseen_rejection";
    private static final String KEY_REJECTION_MESSAGE = "rejection_message";
    private final SharedPreferences sharedPreferences; // SharedPreferences instance

    // --- LiveData ---
    private final MutableLiveData<String> username = new MutableLiveData<>();
    private final MutableLiveData<String> email = new MutableLiveData<>();
    private final MutableLiveData<Integer> experience = new MutableLiveData<>();
    private final MutableLiveData<Integer> coins = new MutableLiveData<>();
    private final MutableLiveData<byte[]> avatar = new MutableLiveData<>();
    private final MutableLiveData<String> toastMessage = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isUsernameEditable = new MutableLiveData<>(false);
    private final MutableLiveData<String> avatarDisplayStatus = new MutableLiveData<>(null); // null, "pending", "rejected", "approved"
    private final MutableLiveData<String> avatarDialogMessage = new MutableLiveData<>(null);
    private final MutableLiveData<Boolean> isChangeAvatarButtonEnabled = new MutableLiveData<>(true);
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(true);

    // --- Other members ---
    private String userId;
    private Uri imageUriToUpload;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private int pendingRequests = 0;

    public ProfileSettingsViewModel(@NonNull Application application) {
        super(application);
        // Initialize SharedPreferences
        sharedPreferences = application.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);

        SharedPreferences authPrefs = application.getSharedPreferences("auth", Context.MODE_PRIVATE);
        userId = authPrefs.getString("userId", null);

        if (userId != null) {
            Log.d(TAG, "ViewModel initialized for userId: " + userId);
            resetAvatarStatusLiveData(); // Reset LiveData only
            startInitialLoading();
        } else {
            Log.e(TAG, "UserId is null. Cannot load profile.");
            isLoading.setValue(false);
            toastMessage.postValue("خطا: شناسه کاربر یافت نشد.");
        }
    }

    private void startInitialLoading() {
        pendingRequests = 0;
        isLoading.setValue(true);
        // ترتیب مهمه: اول پروفایل، بعد چک کردن وضعیت آواتار
        loadProfile();
        checkInitialAvatarStatus(); // This will now also check SharedPreferences for unseen rejection
        setupAvatarStatusListener();
        setupLoadingTimeout();
    }

    private void setupLoadingTimeout() {
        mainHandler.postDelayed(() -> {
            if (pendingRequests > 0) {
                Log.w(TAG, "Initial loading timeout reached, forcing isLoading to false");
                pendingRequests = 0;
                if (Boolean.TRUE.equals(isLoading.getValue())) {
                    isLoading.setValue(false);
                }
            }
        }, 10000);
    }

    // --- Getters --- (بدون تغییر)
    public LiveData<String> getUsername() { return username; }
    public LiveData<String> getEmail() { return email; }
    public LiveData<Integer> getExperience() { return experience; }
    public LiveData<Integer> getCoins() { return coins; }
    public LiveData<byte[]> getAvatar() { return avatar; }
    public LiveData<String> getToastMessage() { return toastMessage; }
    public LiveData<Boolean> getIsUsernameEditable() { return isUsernameEditable; }
    public LiveData<String> getAvatarDisplayStatus() { return avatarDisplayStatus; }
    public LiveData<String> getAvatarDialogMessage() { return avatarDialogMessage; }
    public LiveData<Boolean> getIsChangeAvatarButtonEnabled() { return isChangeAvatarButtonEnabled; }
    public LiveData<Boolean> getIsLoading() { return isLoading; }


    // --- Setters / Actions ---

    public void setImageUriToUpload(Uri uri) {
        this.imageUriToUpload = uri;
        Log.d(TAG, "New image URI set for upload: " + (uri != null ? uri.toString() : "null"));
        if (uri != null) {
            clearUnseenRejectionState();
            resetAvatarStatusLiveData();
            Log.d(TAG, "Cleared unseen rejection state because new image was selected.");
        }
    }

    private void resetAvatarStatusLiveData() {
        avatarDisplayStatus.postValue(null);
        avatarDialogMessage.postValue(null);
        isChangeAvatarButtonEnabled.postValue(true);
        Log.d(TAG, "Avatar LiveData reset: status=null, message=null, buttonEnabled=true");
    }

    private void clearUnseenRejectionState() {
        boolean wasPresent = sharedPreferences.contains(KEY_UNSEEN_REJECTION); // چک کردن قبل از حذف برای لاگ بهتر
        if (wasPresent) {
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.remove(KEY_UNSEEN_REJECTION);
            editor.remove(KEY_REJECTION_MESSAGE);
            editor.apply();
            Log.d(TAG, "Cleared unseen rejection keys from SharedPreferences.");
        } else {
            Log.d(TAG, "clearUnseenRejectionState called, but no rejection state was present.");
        }
    }

    private void checkInitialAvatarStatus() {
        if (userId == null) return;

        boolean unseenRejection = sharedPreferences.getBoolean(KEY_UNSEEN_REJECTION, false);
        if (unseenRejection) {
            String rejectionMessage = sharedPreferences.getString(KEY_REJECTION_MESSAGE, "عکس شما قبلاً رد شده است.");
            Log.w(TAG, "checkInitialAvatarStatus: Found unseen rejection in SharedPreferences. Message: " + rejectionMessage);
            mainHandler.post(() -> {
                avatarDisplayStatus.setValue("rejected");
                avatarDialogMessage.setValue(rejectionMessage);
                isChangeAvatarButtonEnabled.setValue(true);
                // چون این اولین چک در لودینگ هست، اینجا complete صدا نمیزنیم
                // complete در onResponse یا onError درخواست loadProfile صدا زده خواهد شد
            });
            // **مهم:** دیگه return نمی‌کنیم. باید وضعیت pending رو هم چک کنیم.
            // سناریو: عکس رد شده، کاربر دوباره عکس فرستاده و منتظر تاییده.
            // باید SharedPreferences رو نادیده بگیریم و وضعیت pending رو نشون بدیم.
            // پس return رو حذف می‌کنیم و به مرحله بعد میریم.
            // return;
            Log.d(TAG, "Unseen rejection found, but will proceed to check server for pending status anyway.");
        } else {
            Log.d(TAG,"checkInitialAvatarStatus: No unseen rejection found in SharedPreferences. Proceeding to check server for pending status.");
        }

        // --- مرحله ۲: چک کردن سرور برای وضعیت pending ---
        try {
            pendingRequests++;
            Log.d(TAG, "checkInitialAvatarStatus (Server Check): Sending request. Pending requests: " + pendingRequests);
            JSONObject data = new JSONObject();
            data.put("event", "check_pending_avatar");
            data.put("userId", userId);

            SocketRequest request = new SocketRequest(null, data, new SocketManager.Response() {
                @Override
                public void onResponse(JSONObject response, Boolean isError) {
                    mainHandler.post(() -> {
                        try {
                            Log.d(TAG, "checkInitialAvatarStatus (Server Check) Response: " + response.toString());
                            if (!isError && response.getBoolean("success")) {
                                // وضعیت Pending همیشه اولویت داره نسبت به Rejected دیده نشده
                                if (response.has("pendingAvatar") && !response.isNull("pendingAvatar")) {
                                    // وضعیت: منتظر تایید -> SharedPreferences نادیده گرفته میشه
                                    clearUnseenRejectionState(); // اگر رد شده ای بود پاک میکنیم چون pending جدید داریم
                                    avatarDisplayStatus.setValue("pending");
                                    avatarDialogMessage.setValue("درخواست تغییر عکس شما در حال بررسی است.");
                                    isChangeAvatarButtonEnabled.setValue(false);
                                    Log.d(TAG, "checkInitialAvatarStatus (Server Check): Pending avatar found. OVERWRITING any previous rejected state. Status=pending, ButtonEnabled=false");
                                } else {
                                    // وضعیت Pending نداریم. حالا ببینیم آیا از قبل وضعیت rejected از SharedPreferences داشتیم؟
                                    // اگر داشتیم، دیگه نباید resetAvatarStatusLiveData رو صدا بزنیم.
                                    boolean unseenRejectionWasFound = sharedPreferences.getBoolean(KEY_UNSEEN_REJECTION, false);
                                    if (!unseenRejectionWasFound) {
                                        // نه Pending داریم نه Rejected دیده نشده -> ریست به حالت عادی
                                        if (avatarDisplayStatus.getValue() == null) { // فقط اگه هیچ وضعیتی از قبل ست نشده باشه
                                            resetAvatarStatusLiveData();
                                            Log.d(TAG, "checkInitialAvatarStatus (Server Check): No pending avatar and no unseen rejection found. Resetting LiveData to default.");
                                        }
                                    } else {
                                        // وضعیت Pending نداشتیم، ولی Rejected دیده نشده داشتیم که قبلا LiveData رو ست کرده، پس کاری نمیکنیم
                                        Log.d(TAG,"checkInitialAvatarStatus (Server Check): No pending avatar, but unseen rejection was found earlier. Keeping rejected state.");
                                    }
                                }
                            } else {
                                // خطا در پاسخ سرور
                                Log.w(TAG, "checkInitialAvatarStatus (Server Check): Error or unsuccessful response.");
                                // اگر SharedPreferences رد شده رو نشون نمیداد، ریست میکنیم
                                if (!sharedPreferences.contains(KEY_UNSEEN_REJECTION) && avatarDisplayStatus.getValue() == null) {
                                    resetAvatarStatusLiveData();
                                }
                            }
                        } catch (JSONException e) {
                            Log.e(TAG, "checkInitialAvatarStatus (Server Check): JSONException in onResponse", e);
                            if (!sharedPreferences.contains(KEY_UNSEEN_REJECTION) && avatarDisplayStatus.getValue() == null) resetAvatarStatusLiveData();
                        } finally {
                            completeInitialRequest(); // این درخواست چک وضعیت هم تموم شد
                        }
                    });
                }

                @Override
                public void onError(Throwable t) {
                    mainHandler.post(() -> {
                        Log.e(TAG, "checkInitialAvatarStatus (Server Check): onError - " + t.getMessage());
                        if (!sharedPreferences.contains(KEY_UNSEEN_REJECTION) && avatarDisplayStatus.getValue() == null) resetAvatarStatusLiveData();
                        completeInitialRequest();
                    });
                }
            });

            SocketManager.sendRequest(getApplication(), request);
        } catch (Exception e) {
            mainHandler.post(() -> {
                Log.e(TAG, "checkInitialAvatarStatus (Server Check): Exception - " + e.getMessage());
                if (!sharedPreferences.contains(KEY_UNSEEN_REJECTION) && avatarDisplayStatus.getValue() == null) resetAvatarStatusLiveData();
                completeInitialRequest();
            });
        }
    }

    // --- گوش دادن به رویداد avatar_status (اصلاح شده) ---
    private void setupAvatarStatusListener() {
        Log.d(TAG, "Setting up avatar_status listener.");
        SocketManager.addCustomListener("avatar_status", data -> {
            Log.i(TAG, "Received avatar_status event from server: " + data.toString());
            mainHandler.post(() -> {
                try {
                    String status = data.getString("status"); // "approved" یا "rejected"
                    String message = data.getString("message");
                    Log.i(TAG, "Processing avatar_status event: status=" + status + ", message=" + message);

                    // !!!!! خط `clearUnseenRejectionState()` از اینجا حذف شد !!!!!

                    // ابتدا LiveData ها رو آپدیت می‌کنیم تا UI واکنش نشون بده
                    avatarDisplayStatus.setValue(status);
                    avatarDialogMessage.setValue(message);

                    if (status.equals("approved")) {
                        // اگر تایید شد، وضعیت رد شده قبلی (اگه بود) رو پاک می‌کنیم
                        clearUnseenRejectionState();
                        isChangeAvatarButtonEnabled.setValue(true);
                        loadProfile(); // لود عکس جدید
                        Log.d(TAG, "Avatar Approved: ButtonEnabled=true. Reloading profile. Cleared any unseen rejection.");
                        // پاک کردن پیام تایید بعد از چند ثانیه
                        mainHandler.postDelayed(() -> {
                            if ("approved".equals(avatarDisplayStatus.getValue())) {
                                avatarDisplayStatus.postValue(null);
                                avatarDialogMessage.postValue(null);
                            }
                        }, 5000);
                    } else if (status.equals("rejected")) {
                        isChangeAvatarButtonEnabled.setValue(true); // دکمه فعال بشه
                        Log.d(TAG, "Avatar Rejected: ButtonEnabled=true.");
                        // *** ذخیره وضعیت رد شده در SharedPreferences ***
                        // اینجا دیگه clearUnseenRejectionState صدا زده نمی‌شه
                        SharedPreferences.Editor editor = sharedPreferences.edit();
                        editor.putBoolean(KEY_UNSEEN_REJECTION, true);
                        editor.putString(KEY_REJECTION_MESSAGE, message);
                        editor.apply();
                        Log.w(TAG, "Saved unseen rejection status to SharedPreferences. Message: " + message);
                        // پیام rejected دیگه نباید خودکار پاک بشه
                    }
                    // حالت pending از اینجا مدیریت نمی‌شود

                } catch (JSONException e) {
                    Log.e(TAG, "Error processing avatar_status event json: " + e.getMessage());
                }
            });
        });
    }

    // --- loadProfile (بدون تغییر) ---
    private void loadProfile() {
        if (userId == null) return;
        try {
            pendingRequests++;
            Log.d(TAG, "loadProfile: Sending request. Pending requests: " + pendingRequests);
            JSONObject data = new JSONObject();
            data.put("event", "get_profile");
            data.put("userId", userId);
            SocketRequest request = new SocketRequest(null, data, new SocketManager.Response() {
                @Override
                public void onResponse(JSONObject response, Boolean isError) { /* ... کد قبلی ... */
                    mainHandler.post(() -> {
                        try {
                            Log.d(TAG, "loadProfile Response: " + response.toString());
                            if (!isError && response.getBoolean("success")) {
                                JSONObject profileData = response.getJSONObject("data");
                                username.setValue(profileData.getString("username"));
                                email.setValue(profileData.getString("email"));
                                experience.setValue(profileData.getInt("experience"));
                                coins.setValue(profileData.getInt("coins"));
                                if (profileData.has("avatar") && !profileData.isNull("avatar")) {
                                    String base64Avatar = profileData.getString("avatar");
                                    try {
                                        byte[] decodedString = android.util.Base64.decode(base64Avatar, android.util.Base64.DEFAULT);
                                        avatar.setValue(decodedString);
                                        Log.d(TAG,"Avatar loaded successfully.");
                                    } catch (IllegalArgumentException e) {
                                        Log.e(TAG, "Error decoding Base64 avatar: " + e.getMessage());
                                        avatar.setValue(null);
                                    }
                                } else {
                                    avatar.setValue(null);
                                    Log.d(TAG,"No avatar found for user.");
                                }
                            } else {
                                String errorMsg = (response != null && response.has("message")) ? response.getString("message") : "Unknown error";
                                toastMessage.setValue("خطا در بارگذاری پروفایل: " + errorMsg);
                                Log.e(TAG, "loadProfile: Error or unsuccessful response - " + errorMsg);
                            }
                        } catch (JSONException e) {
                            Log.e(TAG, "loadProfile: JSONException in onResponse", e);
                            toastMessage.setValue("خطا در پردازش اطلاعات پروفایل.");
                        } finally {
                            completeInitialRequest(); // این درخواست هم تموم شد
                        }
                    });
                }
                @Override
                public void onError(Throwable t) { /* ... کد قبلی ... */
                    mainHandler.post(() -> {
                        Log.e(TAG, "loadProfile: onError - " + t.getMessage());
                        toastMessage.setValue("خطا در ارتباط برای بارگذاری پروفایل.");
                        completeInitialRequest();
                    });
                }
            });
            SocketManager.sendRequest(getApplication(), request);
        } catch (Exception e) { /* ... کد قبلی ... */
            mainHandler.post(() -> {
                Log.e(TAG, "loadProfile: Exception - " + e.getMessage());
                toastMessage.setValue("خطای غیرمنتظره در بارگذاری پروفایل.");
                completeInitialRequest();
            });
        }
    }

    // --- completeInitialRequest (بدون تغییر) ---
    private void completeInitialRequest() {
        pendingRequests--;
        Log.d(TAG, "completeInitialRequest: Pending requests remaining: " + pendingRequests);
        if (pendingRequests <= 0) {
            if (Boolean.TRUE.equals(isLoading.getValue())) {
                isLoading.postValue(false);
                Log.d(TAG, "All initial requests completed, isLoading set to false");
            }
            pendingRequests = 0;
        }
    }

    // --- toggleUsernameEdit (بدون تغییر) ---
    public void toggleUsernameEdit() {
        isUsernameEditable.setValue(true);
    }

    // --- saveProfile (اصلاح شد: فقط clearUnseenRejectionState رو صدا می‌زنه) ---
    public void saveProfile(String newUsername) {
        if (userId == null) {
            toastMessage.postValue("خطا: شناسه کاربر نامعتبر است.");
            return;
        }
        boolean usernameChanged = newUsername != null && !newUsername.trim().isEmpty() && !newUsername.equals(username.getValue());
        boolean avatarChanged = imageUriToUpload != null;
        Log.d(TAG, "saveProfile called. Username changed: " + usernameChanged + ", Avatar changed: " + avatarChanged);

        if (!usernameChanged && !avatarChanged) {
            toastMessage.postValue("تغییری برای ذخیره وجود ندارد.");
            isUsernameEditable.setValue(false);
            return;
        }
        if (usernameChanged && (newUsername == null || newUsername.trim().isEmpty())) {
            toastMessage.postValue("لطفاً نام کاربری معتبر وارد کنید.");
            return;
        }

        // *** اگر قرار است آواتار جدید آپلود شود، وضعیت رد شده قبلی پاک می‌شود ***
        if (avatarChanged) {
            Log.d(TAG, "Clearing unseen rejection state because saveProfile is initiating a new avatar upload.");
            clearUnseenRejectionState();
            uploadAvatarAndThenUpdateProfile(usernameChanged ? newUsername : null);
        } else if (usernameChanged) {
            // فقط نام کاربری تغییر کرده
            updateUsernameOnly(newUsername);
        }
    }

    // --- updateUsernameOnly (بدون تغییر) ---
    private void updateUsernameOnly(String newUsername) {
        Log.d(TAG, "Attempting to update username only to: " + newUsername);
        isLoading.setValue(true);
        try {
            SocketRequest request = createUpdateProfileRequest(newUsername);
            if (request != null) {
                SocketManager.sendRequest(getApplication(), request);
            } else {
                isLoading.setValue(false);
            }
        } catch (Exception e) {
            isLoading.setValue(false);
            toastMessage.setValue("خطای پیشبینی نشده در آپدیت نام کاربری: " + e.getMessage());
            Log.e(TAG, "updateUsernameOnly Exception: ", e);
        }
    }

    // --- uploadAvatarAndThenUpdateProfile (بدون تغییر) ---
    private void uploadAvatarAndThenUpdateProfile(String newUsernameIfChanged) {
        if (imageUriToUpload == null) return;
        Log.d(TAG, "Attempting to upload avatar. New username (if changed): " + newUsernameIfChanged);
        isLoading.setValue(true);
        avatarDisplayStatus.postValue("pending");
        avatarDialogMessage.postValue("در حال ارسال عکس جدید...");
        isChangeAvatarButtonEnabled.postValue(false);

        SocketManager.uploadAvatar(getApplication(), userId, imageUriToUpload, new SocketManager.UploadCallback() {
            @Override
            public void onSuccess() {
                Log.i(TAG, "Avatar upload successful via SocketManager callback.");
                mainHandler.post(() -> {
                    imageUriToUpload = null;
                    toastMessage.postValue("عکس شما ارسال شد و در انتظار تایید است.");
                    if (newUsernameIfChanged != null) {
                        Log.d(TAG, "Avatar uploaded. Now updating username to: " + newUsernameIfChanged);
                        updateUsernameAfterAvatarUpload(newUsernameIfChanged);
                    } else {
                        Log.d(TAG, "Avatar uploaded. No username change needed. Hiding loading.");
                        isLoading.setValue(false);
                        isUsernameEditable.setValue(false);
                    }
                });
            }
            @Override
            public void onError(String error) {
                Log.e(TAG, "Avatar upload error via SocketManager callback: " + error);
                mainHandler.post(() -> {
                    isLoading.setValue(false);
                    toastMessage.setValue("خطا در آپلود تصویر: " + error);
                    Log.d(TAG,"Resetting avatar status after upload error.");
                    resetAvatarStatusLiveData();
                    // **مهم:** اینجا نباید وضعیت اولیه رو دوباره چک کنیم، چون ممکنه وضعیت rejected ای بوده باشه که الان باید نشون داده بشه
                    // checkInitialAvatarStatus(); // حذف شد
                });
            }
        });
    }

    // --- updateUsernameAfterAvatarUpload (بدون تغییر) ---
    private void updateUsernameAfterAvatarUpload(String newUsername) {
        Log.d(TAG, "Updating username after successful avatar upload to: " + newUsername);
        try {
            SocketRequest request = createUpdateProfileRequest(newUsername);
            if (request != null) {
                SocketManager.sendRequest(getApplication(), request);
            } else {
                isLoading.setValue(false);
            }
        } catch (Exception e) {
            isLoading.setValue(false);
            toastMessage.setValue("خطای پیشبینی نشده در آپدیت نام کاربری پس از آپلود عکس: " + e.getMessage());
            Log.e(TAG, "updateUsernameAfterAvatarUpload Exception: ", e);
            isUsernameEditable.setValue(false);
        }
    }

    // --- createUpdateProfileRequest (بدون تغییر) ---
    private SocketRequest createUpdateProfileRequest(String targetUsername) {
        JSONObject data = new JSONObject();
        try {
            data.put("event", "update_profile");
            data.put("userId", userId);
            if (targetUsername != null) {
                Log.d(TAG, "Adding username to update_profile request: " + targetUsername);
                data.put("username", targetUsername);
            } else {
                Log.d(TAG, "No targetUsername provided, not adding to update_profile request.");
            }
        } catch (JSONException e) {
            Log.e(TAG, "Error creating JSON for update profile request", e);
            mainHandler.post(() -> toastMessage.setValue("خطای داخلی در ساخت درخواست آپدیت."));
            return null;
        }
        return new SocketRequest(null, data, new SocketManager.Response() {
            @Override
            public void onResponse(JSONObject response, Boolean isError) { /* ... کد قبلی ... */
                mainHandler.post(() -> {
                    isLoading.setValue(false);
                    try {
                        String message = (response != null && response.has("message")) ? response.getString("message") : (isError ? "خطای نامشخص" : "پاسخ نامعتبر");
                        toastMessage.setValue(message);
                        if (!isError && response != null && response.getBoolean("success")) {
                            Log.i(TAG, "Update profile successful via server response: " + message);
                            isUsernameEditable.setValue(false);
                            loadProfile();
                        } else {
                            Log.w(TAG, "Update profile failed via server response: " + message);
                            if (message.contains("نام کاربری قبلاً تغییر کرده")) {
                                isUsernameEditable.setValue(false);
                            }
                        }
                    } catch (JSONException e) {
                        Log.e(TAG, "Error processing update_profile response JSON: ", e);
                        toastMessage.setValue("خطا در پردازش پاسخ سرور.");
                    }
                });
            }
            @Override
            public void onError(Throwable t) { /* ... کد قبلی ... */
                mainHandler.post(() -> {
                    isLoading.setValue(false);
                    toastMessage.setValue("خطا در ارتباط برای ذخیره پروفایل.");
                    Log.e(TAG, "Update profile onError: ", t);
                });
            }
        });
    }

    // --- markRejectionAsSeen (بدون تغییر) ---
    public void markRejectionAsSeen() {
        Log.d(TAG, "Marking rejection as seen.");
        clearUnseenRejectionState();
        resetAvatarStatusLiveData();
    }
}