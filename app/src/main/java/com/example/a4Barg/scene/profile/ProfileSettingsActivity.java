package com.example.a4Barg.scene.profile;

import android.content.Intent;
import android.content.SharedPreferences;
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
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.ViewModelProvider;
import androidx.core.util.Pair;

import com.bumptech.glide.Glide;
import com.example.a4Barg.R;
import com.example.a4Barg.common.BaseActivity;
import com.yalantis.ucrop.UCrop;

import java.io.File;

public class ProfileSettingsActivity extends BaseActivity {

    private static final int PICK_IMAGE_REQUEST = 1;
    private ProfileSettingsViewModel viewModel;
    private ImageView ivProfilePic;
    private EditText etUsername;
    private TextView tvEmail, tvExperience, tvCoins;
    private Button btnChangePic, btnEditUsername, btnSave;
    private ProgressBar loadingProgressBar;
    private ScrollView contentLayout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile_settings);

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

        SharedPreferences prefs = getSharedPreferences("auth", MODE_PRIVATE);
        if (prefs.getString("userId", null) == null) {
            Toast.makeText(this, "خطا: کاربر شناسایی نشد", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        viewModel = new ViewModelProvider(this).get(ProfileSettingsViewModel.class);

        viewModel.getIsLoading().observe(this, isLoading -> {
            if (isLoading) {
                loadingProgressBar.setVisibility(View.VISIBLE);
                contentLayout.setVisibility(View.GONE);
                Log.d("ProfileSettings", "Showing loading state");
            } else {
                loadingProgressBar.setVisibility(View.GONE);
                contentLayout.setVisibility(View.VISIBLE);
                Log.d("ProfileSettings", "Hiding loading state, showing content");
            }
        });

        viewModel.getUsername().observe(this, etUsername::setText);
        viewModel.getEmail().observe(this, tvEmail::setText);
        viewModel.getExperience().observe(this, exp -> tvExperience.setText(String.valueOf(exp)));
        viewModel.getCoins().observe(this, coins -> tvCoins.setText(String.valueOf(coins)));
        viewModel.getAvatar().observe(this, avatar -> {
            if (avatar != null) {
                Glide.with(this).load(avatar).placeholder(R.drawable.ic_default_avatar).into(ivProfilePic);
            } else {
                Glide.with(this).load(R.drawable.ic_default_avatar).into(ivProfilePic);
            }
        });
        viewModel.getToastMessage().observe(this, message -> {
            if (message != null) {
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
            }
        });
        viewModel.getIsUsernameEditable().observe(this, editable -> {
            etUsername.setEnabled(editable);
            btnEditUsername.setVisibility(editable ? View.GONE : View.VISIBLE);
        });

        // ترکیب avatarStatus و avatarStatusMessage با MediatorLiveData
        MediatorLiveData<Pair<String, String>> avatarStatusMediator = new MediatorLiveData<>();
        avatarStatusMediator.addSource(viewModel.getAvatarStatus(), status -> {
            String message = viewModel.getAvatarStatusMessage().getValue();
            Log.d("ProfileSettings", "avatarStatusMediator: Source avatarStatus triggered, status=" + status + ", message=" + message);
            avatarStatusMediator.setValue(new Pair<>(status, message));
        });
        avatarStatusMediator.addSource(viewModel.getAvatarStatusMessage(), message -> {
            String status = viewModel.getAvatarStatus().getValue();
            Log.d("ProfileSettings", "avatarStatusMediator: Source avatarStatusMessage triggered, status=" + status + ", message=" + message);
            avatarStatusMediator.setValue(new Pair<>(status, message));
        });

        // مشاهده وضعیت آواتار و نمایش دیالوگ
        avatarStatusMediator.observe(this, pairValue -> {
            if (pairValue == null) {
                Log.d("ProfileSettings", "avatarStatusMediator: pairValue is null");
                btnChangePic.setEnabled(true);
                return;
            }

            String status = pairValue.first;
            String message = pairValue.second;
            Log.d("ProfileSettings", "avatarStatusMediator: Observed pairValue, status=" + status + ", message=" + message);

            if (message != null && status != null) {
                switch (status) {
                    case "pending":
                        showDialog(message);
                        btnChangePic.setEnabled(false);
                        Log.d("ProfileSettings", "Showing dialog: " + message + ", btnChangePic disabled");
                        break;
                    case "approved":
                        btnChangePic.setEnabled(true);
                        Log.d("ProfileSettings", "Avatar approved, btnChangePic enabled");
                        break;
                    case "rejected":
                        showDialog(message);
                        btnChangePic.setEnabled(true);
                        Log.d("ProfileSettings", "Showing dialog: " + message + ", btnChangePic enabled");
                        break;
                    default:
                        btnChangePic.setEnabled(true);
                        Log.d("ProfileSettings", "Default case, btnChangePic enabled");
                        break;
                }
            } else {
                btnChangePic.setEnabled(true);
                Log.d("ProfileSettings", "Not showing dialog: status or message is null, btnChangePic enabled");
            }
        });

        btnChangePic.setOnClickListener(v -> openImagePicker());
        btnEditUsername.setOnClickListener(v -> viewModel.toggleUsernameEdit());
        btnSave.setOnClickListener(v -> viewModel.saveProfile(etUsername.getText().toString()));
    }

    private void showDialog(String message) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(message)
                .setPositiveButton("اوکی", (dialog, which) -> dialog.dismiss())
                .setCancelable(false);
        AlertDialog dialog = builder.create();
        dialog.show();
    }

    private void openImagePicker() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(intent, PICK_IMAGE_REQUEST);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == RESULT_OK && data != null) {
            Uri imageUri = data.getData();
            startCrop(imageUri);
        } else if (requestCode == UCrop.REQUEST_CROP && resultCode == RESULT_OK) {
            Uri croppedUri = UCrop.getOutput(data);
            if (croppedUri != null) {
                Glide.with(this).load(croppedUri).into(ivProfilePic);
                viewModel.setImageUri(croppedUri);
            } else {
                Toast.makeText(this, "خطا در کراپ تصویر", Toast.LENGTH_SHORT).show();
            }
        } else if (resultCode == UCrop.RESULT_ERROR) {
            Toast.makeText(this, "خطا در کراپ تصویر: " + UCrop.getError(data).getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void startCrop(Uri sourceUri) {
        Uri destinationUri = Uri.fromFile(new File(getCacheDir(), "cropped_image_" + System.currentTimeMillis() + ".jpg"));
        UCrop uCrop = UCrop.of(sourceUri, destinationUri)
                .withAspectRatio(1, 1)
                .withMaxResultSize(512, 512);
        uCrop.start(this);
    }
}