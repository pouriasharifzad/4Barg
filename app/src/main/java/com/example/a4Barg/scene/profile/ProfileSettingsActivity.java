package com.example.a4Barg.scene.profile;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;

import com.bumptech.glide.Glide;
import com.example.a4Barg.R;
import com.example.a4Barg.common.BaseActivity;
import com.yalantis.ucrop.UCrop;

import java.io.File;

public class ProfileSettingsActivity extends BaseActivity {

    private static final int PICK_IMAGE_REQUEST = 1;
    private static final String TAG = "ProfileSettingsAct";

    private ProfileSettingsViewModel viewModel;
    private ImageView ivProfilePic;
    private EditText etUsername;
    private TextView tvEmail, tvExperience, tvCoins;
    private Button btnChangePic, btnEditUsername, btnSave;
    private ProgressBar loadingProgressBar;
    private ScrollView contentLayout;
    private AlertDialog statusDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile_settings);
        Log.d(TAG, "onCreate started");

        findViews();

        SharedPreferences prefs = getSharedPreferences("auth", MODE_PRIVATE);
        if (prefs.getString("userId", null) == null) {
            Log.e(TAG, "UserId not found. Finishing activity.");
            Toast.makeText(this, "خطا: کاربر شناسایی نشد.", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        viewModel = new ViewModelProvider(this).get(ProfileSettingsViewModel.class);
        Log.d(TAG, "ViewModel initialized.");

        setupObservers();
        setupListeners();

        Log.d(TAG, "onCreate finished.");
    }

    private void findViews() {
        ivProfilePic = findViewById(R.id.iv_profile_pic);
        etUsername = findViewById(R.id.et_username);
        tvEmail = findViewById(R.id.tv_email);
        tvExperience = findViewById(R.id.tv_experience);
        tvCoins = findViewById(R.id.tv_coins);
        btnChangePic = findViewById(R.id.btn_change_pic);
        btnEditUsername = findViewById(R.id.btn_edit_username);
        btnSave = findViewById(R.id.btn_save);
        loadingProgressBar = findViewById(R.id.loading_progress_bar);
        contentLayout = findViewById(R.id.content_layout);
        Log.d(TAG, "Views found.");
    }

    private void setupObservers() {
        Log.d(TAG, "Setting up observers...");

        // --- آبزرور isLoading (اصلاح شده) ---
        viewModel.getIsLoading().observe(this, isLoading -> {
            Log.d(TAG, "Observer: isLoading changed: " + isLoading);
            loadingProgressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
            contentLayout.setVisibility(isLoading ? View.GONE : View.VISIBLE);

            // فعال/غیرفعال کردن دکمه‌های ذخیره و ویرایش
            btnSave.setEnabled(!isLoading);
            btnEditUsername.setEnabled(!isLoading);

            // --- منطق فعال/غیرفعال کردن دکمه تغییر عکس در زمان لودینگ ---
            if (isLoading) {
                // اگر در حال لودینگ هستیم، دکمه تغییر عکس *همیشه* غیرفعال باشه
                btnChangePic.setEnabled(false);
                btnChangePic.setAlpha(0.5f);
                Log.d(TAG, "isLoading Observer: Setting ChangePic button DISABLED because isLoading is true.");
            } else {
                // اگر لودینگ تمام شد، وضعیت دکمه تغییر عکس رو بر اساس مقدار LiveData خودش تنظیم کن
                // این اطمینان حاصل می‌کنه که وقتی لودینگ تموم شد، وضعیت دکمه به حالت درست برمی‌گرده
                Boolean changeButtonShouldBeEnabled = viewModel.getIsChangeAvatarButtonEnabled().getValue();
                // اگر مقدار LiveData نال بود (که نباید باشه ولی محض احتیاط)، پیش‌فرض رو true می‌گیریم
                boolean shouldEnable = changeButtonShouldBeEnabled != null ? changeButtonShouldBeEnabled : true;
                btnChangePic.setEnabled(shouldEnable);
                btnChangePic.setAlpha(shouldEnable ? 1.0f : 0.5f);
                Log.d(TAG, "isLoading Observer: Setting ChangePic button ENABLED state to: " + shouldEnable + " because isLoading is false.");
            }
        });

        // --- بقیه آبزرورها (بدون تغییر نسبت به کد شما) ---
        viewModel.getUsername().observe(this, username -> {
            if (etUsername.getText() == null || !etUsername.getText().toString().equals(username)) { // اضافه کردن null check
                Log.d(TAG, "Observer: Username updated: " + username);
                etUsername.setText(username);
            }
        });
        viewModel.getEmail().observe(this, email -> {
            Log.d(TAG, "Observer: Email updated: " + email);
            tvEmail.setText(email);
        });
        viewModel.getExperience().observe(this, exp -> {
            Log.d(TAG, "Observer: Experience updated: " + exp);
            tvExperience.setText(String.valueOf(exp));
        });
        viewModel.getCoins().observe(this, coins -> {
            Log.d(TAG, "Observer: Coins updated: " + coins);
            tvCoins.setText(String.valueOf(coins));
        });
        viewModel.getAvatar().observe(this, avatarBytes -> {
            Log.d(TAG, "Observer: Avatar updated. Bytes is null: " + (avatarBytes == null));
            Glide.with(this)
                    .load(avatarBytes)
                    .placeholder(R.drawable.ic_default_avatar)
                    .error(R.drawable.ic_default_avatar)
                    .circleCrop()
                    .into(ivProfilePic);
        });

        viewModel.getToastMessage().observe(this, message -> {
            if (message != null && !message.isEmpty()) {
                Log.d(TAG, "Observer: Displaying toast: " + message);
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
                // viewModel.clearToastMessage(); // Optional
            }
        });

        viewModel.getIsUsernameEditable().observe(this, editable -> {
            Log.d(TAG, "Observer: isUsernameEditable changed: " + editable);
            etUsername.setEnabled(editable);
            boolean isLoadingNow = Boolean.TRUE.equals(viewModel.getIsLoading().getValue());
            btnEditUsername.setVisibility(editable || isLoadingNow ? View.GONE : View.VISIBLE);
        });

        // این آبزرور همچنان لازمه، چون ممکنه وضعیت دکمه خارج از حالت لودینگ تغییر کنه (مثلا موقع pending شدن)
        viewModel.getIsChangeAvatarButtonEnabled().observe(this, isEnabled -> {
            Log.d(TAG, "Observer: isChangeAvatarButtonEnabled triggered directly. isEnabled: " + isEnabled);
            boolean isLoadingNow = Boolean.TRUE.equals(viewModel.getIsLoading().getValue());
            if (!isLoadingNow) {
                btnChangePic.setEnabled(isEnabled);
                btnChangePic.setAlpha(isEnabled ? 1.0f : 0.5f);
            } else {
                Log.d(TAG, "Observer: isChangeAvatarButtonEnabled - Skipping direct UI update because isLoading is true.");
                // اگر در حال لودینگ هستیم، وضعیت دکمه توسط آبزرور isLoading مدیریت می‌شه
                // پس اینجا کاری انجام نمی‌دیم
            }
        });

        viewModel.getAvatarDialogMessage().observe(this, message -> {
            Log.d(TAG, "Observer: avatarDialogMessage triggered. Message: " + (message != null ? "\"" + message + "\"" : "null"));
            if (message != null && !message.isEmpty()) {
                showStatusDialog(message);
            } else {
                dismissStatusDialog();
            }
        });

        viewModel.getAvatarDisplayStatus().observe(this, status -> {
            Log.d(TAG,"Observer: Avatar display status changed: " + status);
        });

        Log.d(TAG, "Observers set up.");
    }

    // --- setupListeners (بدون تغییر) ---
    private void setupListeners() {
        Log.d(TAG, "Setting up listeners...");
        btnChangePic.setOnClickListener(v -> {
            Log.d(TAG, "Change Picture button clicked.");
            openImagePicker();
        });
        btnEditUsername.setOnClickListener(v -> {
            Log.d(TAG, "Edit Username button clicked.");
            viewModel.toggleUsernameEdit();
        });
        btnSave.setOnClickListener(v -> {
            String newUsername = etUsername.getText().toString().trim();
            Log.d(TAG, "Save button clicked. New username: " + newUsername);
            viewModel.saveProfile(newUsername);
        });
        Log.d(TAG, "Listeners set up.");
    }

    // --- showStatusDialog (بدون تغییر) ---
    private void showStatusDialog(String message) {
        dismissStatusDialog();
        Log.d(TAG, "Showing status dialog with message: \"" + message + "\"");

        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.AlertDialogCustom);
        builder.setMessage(message)
                .setPositiveButton("باشه", (dialog, which) -> {
                    Log.d(TAG, "Dialog positive button clicked.");
                    dialog.dismiss();
                    String currentStatus = viewModel.getAvatarDisplayStatus().getValue();
                    Log.d(TAG, "Current avatar status on dialog dismiss: " + currentStatus);
                    if ("rejected".equals(currentStatus)) {
                        Log.d(TAG, "Calling markRejectionAsSeen() because status was 'rejected'.");
                        viewModel.markRejectionAsSeen();
                    } else {
                        Log.d(TAG,"Dialog dismissed for non-rejected status (" + currentStatus + ").");
                    }
                })
                .setCancelable(false);
        statusDialog = builder.create();

        statusDialog.setOnShowListener(dialogInterface -> {
            Button positiveButton = statusDialog.getButton(AlertDialog.BUTTON_POSITIVE);
            // positiveButton.setTextColor(ContextCompat.getColor(this, R.color.black)); // استفاده از رنگ مناسب
        });
        statusDialog.show();
    }

    // --- dismissStatusDialog (بدون تغییر) ---
    private void dismissStatusDialog() {
        if (statusDialog != null && statusDialog.isShowing()) {
            Log.d(TAG, "Dismissing existing status dialog.");
            statusDialog.dismiss();
        }
        statusDialog = null;
    }

    // --- openImagePicker (بدون تغییر) ---
    private void openImagePicker() {
        Log.d(TAG, "Opening image picker...");
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        if (intent.resolveActivity(getPackageManager()) != null) {
            startActivityForResult(intent, PICK_IMAGE_REQUEST);
        } else {
            Log.e(TAG, "No activity found to handle image picking intent.");
            Toast.makeText(this, "برنامه‌ای برای انتخاب عکس یافت نشد.", Toast.LENGTH_SHORT).show();
        }
    }

    // --- onActivityResult (بدون تغییر) ---
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Log.d(TAG, "onActivityResult: requestCode=" + requestCode + ", resultCode=" + resultCode);

        if (requestCode == PICK_IMAGE_REQUEST && resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri sourceUri = data.getData();
            Log.d(TAG, "Image picked: " + sourceUri.toString());
            startCrop(sourceUri);
        } else if (requestCode == UCrop.REQUEST_CROP && resultCode == RESULT_OK && data != null) {
            final Uri resultUri = UCrop.getOutput(data);
            if (resultUri != null) {
                Log.d(TAG, "Image cropped successfully: " + resultUri.toString());
                Glide.with(this)
                        .load(resultUri)
                        .circleCrop()
                        .placeholder(R.drawable.ic_default_avatar)
                        .into(ivProfilePic);
                viewModel.setImageUriToUpload(resultUri);
            } else {
                Log.e(TAG, "UCrop resultUri is null.");
                Toast.makeText(this, "خطا در دریافت نتیجه کراپ عکس", Toast.LENGTH_SHORT).show();
            }
        } else if (resultCode == UCrop.RESULT_ERROR && data != null) {
            final Throwable cropError = UCrop.getError(data);
            Log.e(TAG, "UCrop error: ", cropError);
            Toast.makeText(this, "خطا در کراپ عکس: " + (cropError != null ? cropError.getMessage() : "Unknown error"), Toast.LENGTH_LONG).show();
        } else {
            Log.d(TAG, "onActivityResult: Unhandled request code or result code.");
        }
    }

    // --- startCrop (بدون تغییر) ---
    private void startCrop(Uri sourceUri) {
        String destinationFileName = "cropped_" + System.currentTimeMillis() + ".jpg";
        Uri destinationUri = Uri.fromFile(new File(getCacheDir(), destinationFileName));
        Log.d(TAG, "Starting crop for source: " + sourceUri + ", destination: " + destinationUri);

        UCrop.Options options = new UCrop.Options();
        options.setCompressionQuality(90);
        options.setActiveControlsWidgetColor(ContextCompat.getColor(this, R.color.black)); // Use color resource
        options.setCircleDimmedLayer(true);
        options.setShowCropGrid(false);

        UCrop.of(sourceUri, destinationUri)
                .withOptions(options)
                .withAspectRatio(1, 1)
                .withMaxResultSize(512, 512)
                .start(this);
    }

    // --- onDestroy (بدون تغییر) ---
    @Override
    protected void onDestroy() {
        super.onDestroy();
        dismissStatusDialog();
        Log.d(TAG, "onDestroy called.");
    }
}