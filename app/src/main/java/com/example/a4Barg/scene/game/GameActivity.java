package com.example.a4Barg.scene.game;

import android.os.Bundle;
import android.util.Log;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.a4Barg.R;
import com.example.a4Barg.model.Card;
import com.example.a4Barg.networking.SocketManager;
import com.example.a4Barg.utils.HandView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class GameActivity extends AppCompatActivity {

    private GameViewModel viewModel;
    private HandView userHandView;
    private HandView opponentHandView;
    private ImageView[] tableCardViews;
    private String userId;
    private String roomNumber;
    private String gameId;
    private TextView userUsername, userExp, userCoins;
    private TextView opponentUsername, opponentExp, opponentCoins;
    private boolean playersInfoRequested = false; // برای جلوگیری از درخواست تکراری

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game);

        viewModel = new ViewModelProvider(this).get(GameViewModel.class);
        userId = getIntent().getStringExtra("userId");
        roomNumber = getIntent().getStringExtra("roomNumber");
        Log.d("TEST", "GameActivity onCreate - userId: " + userId + ", roomNumber: " + roomNumber);
        viewModel.setUserId(userId);

        userHandView = findViewById(R.id.user_handView);
        opponentHandView = findViewById(R.id.opponent_handView);
        tableCardViews = new ImageView[] {
                findViewById(R.id.table_card_1),
                findViewById(R.id.table_card_2),
                findViewById(R.id.table_card_3),
                findViewById(R.id.table_card_4)
        };

        userUsername = findViewById(R.id.user_username);
        userExp = findViewById(R.id.user_exp);
        userCoins = findViewById(R.id.user_coins);
        opponentUsername = findViewById(R.id.opponent_username);
        opponentExp = findViewById(R.id.opponent_exp);
        opponentCoins = findViewById(R.id.opponent_coins);

        Log.d("TEST", "UI elements initialized - userHandView: " + (userHandView != null) + ", opponentHandView: " + (opponentHandView != null) + ", tableCardViews length: " + tableCardViews.length);

        userHandView.setShowCards(true);
        opponentHandView.setShowCards(false);

        SocketManager.initialize(this, userId);
        setupGameListeners();

        Log.d("TEST", "Starting game with roomNumber: " + roomNumber);
        viewModel.startGame(roomNumber);
    }

    private void setupGameListeners() {
        SocketManager.addPlayerCardsListener(new SocketManager.PlayerCardsListener() {
            @Override
            public void onPlayerCards(JSONObject data) {
                Log.d("TEST", "onPlayerCards called with data: " + data.toString());
                try {
                    String receivedUserId = data.getString("userId");
                    if (receivedUserId.equals(userId)) {
                        JSONArray cardsArray = data.getJSONArray("cards");
                        List<Card> userCards = new ArrayList<>();
                        for (int i = 0; i < cardsArray.length(); i++) {
                            JSONObject cardObj = cardsArray.getJSONObject(i);
                            String suit = cardObj.getString("suit");
                            String value = cardObj.optString("value", "unknown");
                            userCards.add(new Card(suit, value));
                        }
                        runOnUiThread(() -> userHandView.setCards(userCards));
                    }

                    if (data.has("tableCards")) {
                        JSONArray tableCardsArray = data.getJSONArray("tableCards");
                        List<Card> tableCards = new ArrayList<>();
                        for (int i = 0; i < tableCardsArray.length(); i++) {
                            JSONObject cardObj = tableCardsArray.getJSONObject(i);
                            String suit = cardObj.getString("suit");
                            String value = cardObj.getString("value");
                            tableCards.add(new Card(suit, value));
                        }
                        runOnUiThread(() -> updateTableCards(tableCards));
                    }
                } catch (JSONException e) {
                    Log.e("TEST", "Error parsing player_cards data: " + e.getMessage());
                }
            }

            @Override
            public void onPlayerCardsError(Throwable t) {
                Log.e("TEST", "PlayerCardsListener error: " + t.getMessage());
                runOnUiThread(() -> Toast.makeText(GameActivity.this, "خطا در دریافت کارت‌ها: " + t.getMessage(), Toast.LENGTH_SHORT).show());
            }
        });

        SocketManager.listenForGameStateUpdates(new SocketManager.GameStateUpdateListener() {
            @Override
            public void onGameStateUpdate(JSONObject data) {
                Log.d("TEST", "onGameStateUpdate called with data: " + data.toString());
                try {
                    JSONArray players = data.getJSONArray("players");
                    for (int i = 0; i < players.length(); i++) {
                        JSONObject player = players.getJSONObject(i);
                        String playerId = player.getString("userId");
                        if (!playerId.equals(userId)) {
                            int cardCount = player.getInt("cardCount");
                            List<Card> opponentCards = new ArrayList<>();
                            for (int j = 0; j < cardCount; j++) {
                                opponentCards.add(new Card("unknown", "unknown"));
                            }
                            runOnUiThread(() -> opponentHandView.setCards(opponentCards));
                        }
                    }

                    if (data.has("tableCards")) {
                        JSONArray tableCardsArray = data.getJSONArray("tableCards");
                        List<Card> tableCards = new ArrayList<>();
                        for (int i = 0; i < tableCardsArray.length(); i++) {
                            JSONObject cardObj = tableCardsArray.getJSONObject(i);
                            String suit = cardObj.getString("suit");
                            String value = cardObj.getString("value");
                            tableCards.add(new Card(suit, value));
                        }
                        runOnUiThread(() -> updateTableCards(tableCards));
                    }
                } catch (JSONException e) {
                    Log.e("TEST", "Error parsing game_state_update data: " + e.getMessage());
                }
            }

            @Override
            public void onGameStateUpdateError(Throwable t) {
                Log.e("TEST", "GameStateUpdateListener error: " + t.getMessage());
            }
        });

        SocketManager.listenForGameStart(new SocketManager.GameStartListener() {
            @Override
            public void onGameStart(JSONObject data) {
                Log.d("TEST", "onGameStart called with data: " + data.toString());
                try {
                    String receivedRoomNumber = data.getString("roomNumber");
                    if (receivedRoomNumber.equals(roomNumber)) {
                        gameId = data.getString("gameId");
                        Log.d("TEST", "Game started - gameId: " + gameId);
                        viewModel.requestPlayerCards(gameId);
                        if (!playersInfoRequested) {
                            Log.d("TEST", "Requesting game players info for gameId: " + gameId);
                            SocketManager.getGamePlayersInfo(GameActivity.this, gameId, userId, null); // درخواست رو بفرست
                            SocketManager.addGamePlayersInfoListener(new SocketManager.GamePlayersInfoListener() {
                                @Override
                                public void onGamePlayersInfo(JSONObject data) {
                                    Log.d("TEST", "onGamePlayersInfo called with data: " + data.toString());
                                    try {
                                        JSONArray players = data.getJSONArray("players");
                                        for (int i = 0; i < players.length(); i++) {
                                            JSONObject player = players.getJSONObject(i);
                                            String playerId = player.getString("userId");
                                            String username = player.getString("username");
                                            int exp = player.getInt("exp");
                                            int coins = player.getInt("coins");

                                            runOnUiThread(() -> {
                                                if (playerId.equals(userId)) {
                                                    userUsername.setText(username);
                                                    userExp.setText("EXP: " + exp);
                                                    userCoins.setText("Coins: " + coins);
                                                    Log.d("TEST", "User info set - username: " + username + ", exp: " + exp + ", coins: " + coins);
                                                } else {
                                                    opponentUsername.setText(username);
                                                    opponentExp.setText("EXP: " + exp);
                                                    opponentCoins.setText("Coins: " + coins);
                                                    Log.d("TEST", "Opponent info set - username: " + username + ", exp: " + exp + ", coins: " + coins);
                                                }
                                            });
                                        }
                                        playersInfoRequested = true;
                                    } catch (JSONException e) {
                                        Log.e("TEST", "Error parsing game_players_info data: " + e.getMessage());
                                    }
                                }

                                @Override
                                public void onGamePlayersInfoError(Throwable t) {
                                    Log.e("TEST", "GamePlayersInfoListener error: " + t.getMessage());
                                    runOnUiThread(() -> Toast.makeText(GameActivity.this, "خطا در دریافت اطلاعات بازیکن‌ها: " + t.getMessage(), Toast.LENGTH_SHORT).show());
                                }
                            });
                        }
                    }
                } catch (JSONException e) {
                    Log.e("TEST", "Error parsing game_start data: " + e.getMessage());
                }
            }

            @Override
            public void onGameStartError(Throwable t) {
                Log.e("TEST", "GameStartListener error: " + t.getMessage());
            }
        });
    }

    private void updateTableCards(List<Card> tableCards) {
        Log.d("TEST", "updateTableCards called with " + tableCards.size() + " cards: " + tableCards.toString());
        for (int i = 0; i < tableCardViews.length; i++) {
            if (i < tableCards.size()) {
                Card card = tableCards.get(i);
                int resId = getResources().getIdentifier(card.getImageResourceName(), "drawable", getPackageName());
                tableCardViews[i].setImageResource(resId != 0 ? resId : R.drawable.card_back);
            } else {
                tableCardViews[i].setImageResource(R.drawable.card_back);
            }
        }
    }
}