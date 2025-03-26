package com.example.a4Barg.scene.lobby;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.a4Barg.R;
import com.example.a4Barg.common.BaseActivity;
import com.example.a4Barg.scene.room.RoomActivity;
import com.example.a4Barg.scene.roomlist.RoomListActivity;

public class LobbyActivity extends BaseActivity {

    private LobbyViewModel viewModel;
    private Button createRoomButton, joinRoomButton;
    private ProgressBar progressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_lobby);

        viewModel = new ViewModelProvider(this).get(LobbyViewModel.class);
        String userId = getIntent().getStringExtra("userId");
        viewModel.setUserId(userId);

        createRoomButton = findViewById(R.id.createRoomButton);
        joinRoomButton = findViewById(R.id.joinRoomButton);
        progressBar = findViewById(R.id.progressBar);

        // Observer برای استاتوس لودینگ
        viewModel.getIsLoading().observe(this, isLoading -> {
            progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
            createRoomButton.setEnabled(!isLoading);
            joinRoomButton.setEnabled(!isLoading);
        });

        // Observer برای پیام‌ها
        viewModel.getMessage().observe(this, msg -> {
            if (msg != null) {
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
            }
        });

        createRoomButton.setOnClickListener(v -> showCreateRoomDialog());
        joinRoomButton.setOnClickListener(v -> {
            Intent intent = new Intent(LobbyActivity.this, RoomListActivity.class);
            intent.putExtra("userId", userId);
            startActivity(intent);
        });
    }

    private void showCreateRoomDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_create_room, null);
        builder.setView(dialogView);

        EditText minExperienceEditText = dialogView.findViewById(R.id.minExperienceEditText);
        EditText minCoinsEditText = dialogView.findViewById(R.id.minCoinsEditText);
        EditText maxPlayersEditText = dialogView.findViewById(R.id.maxPlayersEditText);
        Button createRoomDialogButton = dialogView.findViewById(R.id.createRoomDialogButton);

        AlertDialog dialog = builder.create();

        createRoomDialogButton.setOnClickListener(v -> {
            String minExperienceStr = minExperienceEditText.getText().toString().trim();
            String minCoinsStr = minCoinsEditText.getText().toString().trim();
            String maxPlayersStr = maxPlayersEditText.getText().toString().trim();

            if (minExperienceStr.isEmpty() || minCoinsStr.isEmpty() || maxPlayersStr.isEmpty()) {
                Toast.makeText(this, "لطفاً همه فیلدها را پر کنید", Toast.LENGTH_SHORT).show();
                return;
            }

            int minExperience = Integer.parseInt(minExperienceStr);
            int minCoins = Integer.parseInt(minCoinsStr);
            int maxPlayers = Integer.parseInt(maxPlayersStr);

            viewModel.createRoom(minExperience, minCoins, maxPlayers);
            dialog.dismiss();
        });

        dialog.show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // SocketManager.disconnect(); // کامنت نگه داشته شده
    }
}