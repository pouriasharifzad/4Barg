package com.example.a4Barg.scene.game;

import android.os.Bundle;
import android.util.Log;
import android.widget.ImageView;
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
    private String gameId; // متغیر جدید برای gameId

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game);

        viewModel = new ViewModelProvider(this).get(GameViewModel.class);
        userId = getIntent().getStringExtra("userId");
        roomNumber = getIntent().getStringExtra("roomNumber");
        viewModel.setUserId(userId);

        userHandView = findViewById(R.id.user_handView);
        opponentHandView = findViewById(R.id.opponent_handView);
        tableCardViews = new ImageView[] {
                findViewById(R.id.table_card_1),
                findViewById(R.id.table_card_2),
                findViewById(R.id.table_card_3),
                findViewById(R.id.table_card_4)
        };

        // تنظیمات اولیه HandView
        userHandView.setShowCards(true);
        opponentHandView.setShowCards(false);

        // گوش دادن به آپدیت‌های اولیه بازی
        setupGameListeners();

        // درخواست شروع بازی و گرفتن کارت‌ها
        viewModel.startGame(roomNumber);
    }

    private void setupGameListeners() {
        SocketManager.listenForPlayerCards(new SocketManager.PlayerCardsListener() {
            @Override
            public void onPlayerCards(JSONObject data) {
                Log.d("TEST", "Received player_cards: " + data.toString());
                try {
                    String receivedUserId = data.getString("userId");
                    if (receivedUserId.equals(userId)) {
                        JSONArray cardsArray = data.getJSONArray("cards");
                        List<Card> userCards = new ArrayList<>();
                        for (int i = 0; i < cardsArray.length(); i++) {
                            JSONObject cardObj = cardsArray.getJSONObject(i);
                            userCards.add(new Card(cardObj.getString("suit"), cardObj.getString("value")));
                        }
                        userHandView.setCards(userCards);
                        Log.d("TEST", "User cards set: " + userCards.toString());
                    }
                    if (data.has("tableCards")) {
                        JSONArray tableCardsArray = data.getJSONArray("tableCards");
                        List<Card> tableCards = new ArrayList<>();
                        for (int i = 0; i < tableCardsArray.length(); i++) {
                            JSONObject cardObj = tableCardsArray.getJSONObject(i);
                            tableCards.add(new Card(cardObj.getString("suit"), cardObj.getString("value")));
                        }
                        updateTableCards(tableCards);
                        Log.d("TEST", "Table cards set: " + tableCards.toString());
                    }
                } catch (JSONException e) {
                    Log.e("TEST", "Error parsing player cards: " + e.getMessage());
                }
            }

            @Override
            public void onPlayerCardsError(Throwable t) {
                Log.e("TEST", "Player cards error: " + t.getMessage());
                Toast.makeText(GameActivity.this, "خطا در دریافت کارت‌ها: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
        // حذف یکی از دو listener تکراری برای game_started و اصلاح درخواست کارت‌ها
        SocketManager.listenForGameStart(new SocketManager.GameStartListener() {
            @Override
            public void onGameStart(JSONObject data) {
                try {
                    String receivedRoomNumber = data.getString("roomNumber");
                    if (receivedRoomNumber.equals(roomNumber)) {
                        gameId = data.getString("gameId"); // ذخیره gameId
                        Log.d("TEST", "Game started with gameId: " + gameId + ", data: " + data.toString());
                        // درخواست کارت‌ها با gameId به جای roomNumber
                        viewModel.requestPlayerCards(gameId);
                    }
                } catch (JSONException e) {
                    Log.e("TEST", "Error parsing game start: " + e.getMessage());
                }
            }

            @Override
            public void onGameStartError(Throwable t) {
                Log.e("TEST", "Game start error: " + t.getMessage());
                Toast.makeText(GameActivity.this, "خطا در شروع بازی: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });



        SocketManager.listenForGameStateUpdates(new SocketManager.GameStateUpdateListener() {
            @Override
            public void onGameStateUpdate(JSONObject data) {
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
                            opponentHandView.setCards(opponentCards);
                            Log.d("TEST", "Opponent card count updated: " + cardCount);
                        }
                    }
                    // آپدیت کارت‌های روی زمین
                    if (data.has("tableCards")) {
                        JSONArray tableCardsArray = data.getJSONArray("tableCards");
                        List<Card> tableCards = new ArrayList<>();
                        for (int i = 0; i < tableCardsArray.length(); i++) {
                            JSONObject cardObj = tableCardsArray.getJSONObject(i);
                            tableCards.add(new Card(cardObj.getString("suit"), cardObj.getString("value")));
                        }
                        updateTableCards(tableCards);
                        Log.d("TEST", "Table cards updated: " + tableCards.toString());
                    }
                } catch (JSONException e) {
                    Log.e("TEST", "Error parsing game state: " + e.getMessage());
                }
            }

            @Override
            public void onGameStateUpdateError(Throwable t) {
                Log.e("TEST", "Game state update error: " + t.getMessage());
            }
        });
    }

    private void updateTableCards(List<Card> tableCards) {
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