package com.example.a4Barg.scene.room;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.a4Barg.R;
import com.example.a4Barg.common.BaseActivity;
import com.example.a4Barg.networking.SocketManager;
import com.example.a4Barg.scene.game.GameActivity;
import com.example.a4Barg.scene.lobby.LobbyActivity;
import com.example.a4Barg.scene.roomlist.RoomListActivity;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

public class RoomActivity extends BaseActivity {

    private RoomViewModel viewModel;
    private RecyclerView playerRecyclerView;
    private PlayerAdapter playerAdapter;
    private Button exitRoomButton;
    private Button startGameButton;
    private TextView roomNumberTextView;
    private TextView minExperienceTextView;
    private TextView minCoinsTextView;
    private TextView countdownTextView;
    private String roomNumber;
    private String userId;
    private boolean isHost = false;
    private CountDownTimer countDownTimer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_room);

        viewModel = new ViewModelProvider(this).get(RoomViewModel.class);
        userId = getIntent().getStringExtra("userId");
        roomNumber = getIntent().getStringExtra("roomNumber");
        viewModel.setUserId(userId);

        if (savedInstanceState == null) {
            viewModel.loadRoomPlayers(roomNumber);
            viewModel.loadRoomDetails(roomNumber);
        } else {
            roomNumber = savedInstanceState.getString("roomNumber");
            if (viewModel.getRoomDetails().getValue() == null) {
                viewModel.loadRoomDetails(roomNumber);
            }
            if (viewModel.getPlayers().getValue() == null || viewModel.getPlayers().getValue().isEmpty()) {
                viewModel.loadRoomPlayers(roomNumber);
            }
        }

        roomNumberTextView = findViewById(R.id.roomNumberTextView);
        minExperienceTextView = findViewById(R.id.minExperienceTextView);
        minCoinsTextView = findViewById(R.id.minCoinsTextView);
        countdownTextView = findViewById(R.id.countdownTextView);
        startGameButton = findViewById(R.id.startGameButton);

        viewModel.getRoomDetails().observe(this, roomDetails -> {
            if (roomDetails != null) {
                try {
                    JSONObject room = roomDetails.has("room") ? roomDetails.getJSONObject("room") : roomDetails;
                    String roomNum = room.has("roomNumber") ? room.getString("roomNumber") : "نامشخص";
                    int minExp = room.has("minExperience") ? room.getInt("minExperience") : 0;
                    int minCoin = room.has("minCoins") ? room.getInt("minCoins") : 0;

                    if (room.has("hostId")) {
                        String hostId = room.getString("hostId");
                        isHost = userId.equals(hostId);
                        Log.d("TEST", "User isHost: " + isHost + ", userId: " + userId + ", hostId: " + hostId);
                        startGameButton.setVisibility(isHost ? View.VISIBLE : View.GONE);
                    }

                    roomNumberTextView.setText("شماره روم: " + roomNum);
                    minExperienceTextView.setText("حداقل تجربه: " + minExp);
                    minCoinsTextView.setText("حداقل سکه: " + minCoin);
                    Log.d("TEST", "Room details updated: roomNumber=" + roomNum + ", minExperience=" + minExp + ", minCoins=" + minCoin + ", gameId=" + viewModel.getGameId());

                    updateStartGameButtonState();
                } catch (JSONException e) {
                    Toast.makeText(this, "خطا در نمایش مشخصات روم", Toast.LENGTH_SHORT).show();
                    Log.e("TEST", "Error parsing room details: " + e.getMessage());
                }
            }
        });

        playerRecyclerView = findViewById(R.id.playerRecyclerView);
        playerRecyclerView.setLayoutManager(new LinearLayoutManager(this));

        exitRoomButton = findViewById(R.id.exitRoomButton);

        playerAdapter = new PlayerAdapter(new ArrayList<>());
        playerRecyclerView.setAdapter(playerAdapter);

        viewModel.getPlayers().observe(this, players -> {
            if (players != null) {
                playerAdapter.updatePlayers(players);
                Log.d("TEST", "Player list updated in UI: " + players.toString());
                updateStartGameButtonState();
            }
        });

        viewModel.getErrorMessage().observe(this, msg -> {
            if (msg != null) {
                if (msg.equals("میزبان روم را بست")) {
                    new AlertDialog.Builder(this)
                            .setTitle("روم بسته شد")
                            .setMessage("میزبان روم را بست.")
                            .setPositiveButton("برو به لیست روم‌ها", (dialog, which) -> {
                                Intent intent = new Intent(RoomActivity.this, RoomListActivity.class);
                                intent.putExtra("userId", userId);
                                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                                startActivity(intent);
                                finish();
                            })
                            .setCancelable(false)
                            .show();
                } else if (msg.equals("در حال بارگذاری بازی...")) {
                    startCountdown();
                } else if (msg.equals("با موفقیت از روم خارج شدید")) {
                    Intent intent;
                    if (isHost) {
                        // میزبان به LobbyActivity منتقل می‌شود
                        intent = new Intent(RoomActivity.this, LobbyActivity.class);
                    } else {
                        // مهمان به RoomListActivity منتقل می‌شود
                        intent = new Intent(RoomActivity.this, RoomListActivity.class);
                    }
                    intent.putExtra("userId", userId);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    startActivity(intent);
                    finish();
                } else {
                    showToast(msg);
                }
            }
        });

        startGameButton.setOnClickListener(v -> {
            if (isHost) {
                viewModel.gameLoading(roomNumber);
            } else {
                Toast.makeText(this, "فقط میزبان می‌تواند بازی را شروع کند", Toast.LENGTH_SHORT).show();
            }
        });

        exitRoomButton.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle("خروج از روم")
                    .setMessage("آیا مطمئن هستید که می‌خواهید از روم خارج شوید؟")
                    .setPositiveButton("بله", (dialog, which) -> {
                        viewModel.leaveRoom();
                    })
                    .setNegativeButton("خیر", null)
                    .show();
        });

        setupGameLoadingListener();
    }

    private void updateStartGameButtonState() {
        if (viewModel.getPlayers().getValue() != null) {
            int currentPlayers = viewModel.getPlayers().getValue().size();
            startGameButton.setEnabled(currentPlayers == 2); // ظرفیت ثابت 2 نفر
            Log.d("TEST", "Start game button state updated: currentPlayers=" + currentPlayers + ", maxPlayers=2, enabled=" + startGameButton.isEnabled());
        }
    }

    private void startCountdown() {
        countdownTextView.setVisibility(View.VISIBLE);
        startGameButton.setEnabled(false);
        exitRoomButton.setEnabled(false);

        countDownTimer = new CountDownTimer(3000, 1000) {
            @SuppressLint("SetTextI18n")
            @Override
            public void onTick(long millisUntilFinished) {
                countdownTextView.setText("بازی در " + (millisUntilFinished / 1000 + 1) + " ثانیه شروع می‌شود...");
            }

            @Override
            public void onFinish() {
                countdownTextView.setVisibility(View.GONE);
                Intent intent = new Intent(RoomActivity.this, GameActivity.class);
                intent.putExtra("roomNumber", roomNumber);
                intent.putExtra("userId", userId);
                intent.putExtra("isHost", isHost);
                intent.putExtra("gameId", viewModel.getGameId()); // ارسال gameId به GameActivity
                startActivity(intent);
                finish();
            }
        }.start();
    }

    private void setupGameLoadingListener() {
        SocketManager.listenForGameLoading(new SocketManager.GameLoadingListener() {
            @Override
            public void onGameLoading(JSONObject data) {
                try {
                    String receivedRoomNumber = data.getString("roomNumber");
                    if (receivedRoomNumber.equals(roomNumber)) {
                        runOnUiThread(() -> startCountdown());
                    }
                } catch (JSONException e) {
                    runOnUiThread(() -> Toast.makeText(RoomActivity.this, "خطا در پردازش پیام بارگذاری بازی", Toast.LENGTH_SHORT).show());
                }
            }

            @Override
            public void onGameLoadingError(Throwable t) {
                runOnUiThread(() -> showToast(t.getMessage()));
            }
        });
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString("roomNumber", roomNumber);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (countDownTimer != null) {
            countDownTimer.cancel();
        }
        viewModel.resetLoadFlags();
    }

    @SuppressLint("MissingSuperCall")
    @Override
    public void onBackPressed() {
        new AlertDialog.Builder(this)
                .setTitle("خروج از روم")
                .setMessage("آیا مطمئن هستید که می‌خواهید از روم خارج شوید؟")
                .setPositiveButton("بله", (dialog, which) -> {
                    viewModel.leaveRoom();
                })
                .setNegativeButton("خیر", null)
                .show();
    }
}