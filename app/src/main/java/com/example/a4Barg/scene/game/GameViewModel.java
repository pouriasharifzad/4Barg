package com.example.a4Barg.scene.game;

import android.app.Application;
import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.a4Barg.model.Card;
import com.example.a4Barg.model.GameState;
import com.example.a4Barg.model.Player;
import com.example.a4Barg.model.SocketRequest;
import com.example.a4Barg.networking.SocketManager;
import com.example.a4Barg.utils.ConsValue;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class GameViewModel extends AndroidViewModel {
    private static final String TAG = "GameViewModel";
    private final Context context;
    private final MutableLiveData<GameState> gameState = new MutableLiveData<>();
    private final MutableLiveData<List<Card>> playerCards = new MutableLiveData<>();
    private final MutableLiveData<String> chooseSuitEvent = new MutableLiveData<>();
    private final MutableLiveData<String> gameEndedEvent = new MutableLiveData<>();
    private final String userId;
    private String gameId; // برای ذخیره gameId

    public GameViewModel(@NonNull Application application) {
        super(application);
        this.context = application.getApplicationContext();
        this.userId = context.getSharedPreferences("auth", Context.MODE_PRIVATE).getString("userId", null);
        Log.d(TAG, "GameViewModel initialized with userId: " + userId);
        setupSocketListeners();
    }

    private void setupSocketListeners() {
        // گوش دادن به شروع بازی
        SocketManager.listenForGameStart(new SocketManager.GameStartListener() {
            @Override
            public void onGameStart(JSONObject data) {
                try {
                    String roomNumber = data.getString("roomNumber");
                    gameId = data.getString("gameId"); // ذخیره gameId
                    Log.d(TAG, "Game started for room: " + roomNumber + ", gameId: " + gameId);
                } catch (JSONException e) {
                    Log.e(TAG, "Error in onGameStart: " + e.getMessage());
                }
            }

            @Override
            public void onGameStartError(Throwable t) {
                Log.e(TAG, "Game start error: " + t.getMessage());
            }
        });

        // گوش دادن به به‌روزرسانی وضعیت بازی
        SocketManager.listenForGameStateUpdates(new SocketManager.GameStateUpdateListener() {
            @Override
            public void onGameStateUpdate(JSONObject data) {
                try {
                    gameId = data.getString("gameId"); // به‌روزرسانی gameId
                    String roomNumber = data.getString("roomNumber");
                    String currentPlayerId = data.getString("currentPlayer");
                    JSONObject currentCardJson = data.getJSONObject("currentCard");
                    Card currentCard = new Card(currentCardJson.getString("suit"), currentCardJson.getString("rank"));
                    String currentSuit = data.has("currentSuit") && !data.isNull("currentSuit") ? data.getString("currentSuit") : null;
                    int penaltyCount = data.getInt("penaltyCount");
                    int direction = data.getInt("direction");

                    JSONArray playersJson = data.getJSONArray("players");
                    List<Player> players = new ArrayList<>();
                    for (int i = 0; i < playersJson.length(); i++) {
                        JSONObject playerJson = playersJson.getJSONObject(i);
                        String userId = playerJson.getString("userId");
                        String username = playerJson.getString("username");
                        int cardCount = playerJson.getInt("cardCount");
                        int experience = 500; // مقدار پیش‌فرض
                        int coins = 500; // مقدار پیش‌فرض
                        players.add(new Player(userId, username, cardCount, experience, coins));
                    }

                    GameState state = new GameState(roomNumber, players, currentPlayerId, currentCard, currentSuit, penaltyCount, direction);
                    gameState.postValue(state);
                    Log.d(TAG, "Game state updated: " + data.toString());
                } catch (JSONException e) {
                    Log.e(TAG, "Error in game_state_update: " + e.getMessage());
                }
            }

            @Override
            public void onGameStateUpdateError(Throwable t) {
                Log.e(TAG, "Error in game_state_update: " + t.getMessage());
            }
        });

        // گوش دادن به کارت‌های بازیکن
        SocketManager.listenForPlayerCards(new SocketManager.PlayerCardsListener() {
            @Override
            public void onPlayerCards(JSONObject data) {
                try {
                    Log.d(TAG, "Received player_cards event from server");
                    gameId = data.getString("gameId"); // به‌روزرسانی gameId
                    String receivedUserId = data.getString("userId");
                    Log.d(TAG, "Received cards for userId: " + receivedUserId);

                    // بررسی اینکه آیا کارت‌ها برای این کاربر هستند
                    if (!receivedUserId.equals(userId)) {
                        Log.d(TAG, "Ignoring cards for userId " + receivedUserId + " (not this user)");
                        return;
                    }

                    JSONArray cardsJson = data.getJSONArray("cards");
                    List<Card> cards = new ArrayList<>();
                    for (int i = 0; i < cardsJson.length(); i++) {
                        JSONObject cardJson = cardsJson.getJSONObject(i);
                        cards.add(new Card(cardJson.getString("suit"), cardJson.getString("rank")));
                    }
                    Log.d(TAG, "Parsed cards: " + cards.toString());
                    playerCards.postValue(cards);
                    Log.d(TAG, "Player cards updated: " + cards.size() + " cards");
                } catch (JSONException e) {
                    Log.e(TAG, "Error in player_cards: " + e.getMessage());
                }
            }

            @Override
            public void onPlayerCardsError(Throwable t) {
                Log.e(TAG, "Error in player_cards: " + t.getMessage());
            }
        });

        // گوش دادن به درخواست انتخاب خال (برای جک)
        SocketManager.listenForChooseSuit(new SocketManager.ChooseSuitListener() {
            @Override
            public void onChooseSuit(JSONObject data) {
                try {
                    String message = data.getString("message");
                    chooseSuitEvent.postValue(message);
                    Log.d(TAG, "Choose suit event received: " + message);
                } catch (JSONException e) {
                    Log.e(TAG, "Error in choose_suit: " + e.getMessage());
                }
            }

            @Override
            public void onChooseSuitError(Throwable t) {
                Log.e(TAG, "Error in choose_suit: " + t.getMessage());
            }
        });

        // گوش دادن به پایان بازی
        SocketManager.listenForGameEnded(new SocketManager.GameEndedListener() {
            @Override
            public void onGameEnded(JSONObject data) {
                try {
                    gameId = data.getString("gameId"); // به‌روزرسانی gameId
                    String message = data.getString("message");
                    gameEndedEvent.postValue(message);
                    Log.d(TAG, "Game ended: " + message);
                } catch (JSONException e) {
                    Log.e(TAG, "Error in game_ended: " + e.getMessage());
                }
            }

            @Override
            public void onGameEndedError(Throwable t) {
                Log.e(TAG, "Error in game_ended: " + t.getMessage());
            }
        });
    }

    public LiveData<GameState> getGameState() {
        return gameState;
    }

    public LiveData<List<Card>> getPlayerCards() {
        return playerCards;
    }

    public LiveData<String> getChooseSuitEvent() {
        return chooseSuitEvent;
    }

    public LiveData<String> getGameEndedEvent() {
        return gameEndedEvent;
    }

    public void playCard(Card card) { // حذف roomNumber از پارامترها
        try {
            JSONObject data = new JSONObject();
            data.put("event", "play_card");
            data.put("gameId", gameId); // استفاده از gameId
            JSONObject cardJson = new JSONObject();
            cardJson.put("suit", card.getSuit());
            cardJson.put("rank", card.getRank());
            data.put("card", cardJson);

            SocketRequest request = new SocketRequest(null, data, new SocketManager.Response() {
                @Override
                public void onResponse(JSONObject object, Boolean isError) throws JSONException {
                    if (!isError) {
                        Log.d(TAG, "Card played successfully: " + card);
                    } else {
                        Log.e(TAG, "Error playing card: " + object.getString("message"));
                    }
                }

                @Override
                public void onError(Throwable t) {
                    Log.e(TAG, "Error playing card: " + t.getMessage());
                }
            });
            SocketManager.sendRequest(context, request);
        } catch (JSONException e) {
            Log.e(TAG, "Error in playCard: " + e.getMessage());
        }
    }

    public void drawCard() { // حذف roomNumber از پارامترها
        try {
            JSONObject data = new JSONObject();
            data.put("event", "draw_card");
            data.put("gameId", gameId); // استفاده از gameId

            SocketRequest request = new SocketRequest(null, data, new SocketManager.Response() {
                @Override
                public void onResponse(JSONObject object, Boolean isError) throws JSONException {
                    if (!isError) {
                        Log.d(TAG, "Card drawn successfully");
                    } else {
                        Log.e(TAG, "Error drawing card: " + object.getString("message"));
                    }
                }

                @Override
                public void onError(Throwable t) {
                    Log.e(TAG, "Error drawing card: " + t.getMessage());
                }
            });
            SocketManager.sendRequest(context, request);
        } catch (JSONException e) {
            Log.e(TAG, "Error in drawCard: " + e.getMessage());
        }
    }

    public void chooseSuit(String suit) { // حذف roomNumber از پارامترها
        try {
            JSONObject data = new JSONObject();
            data.put("event", "choose_suit");
            data.put("gameId", gameId); // استفاده از gameId
            data.put("suit", suit);

            SocketRequest request = new SocketRequest(null, data, new SocketManager.Response() {
                @Override
                public void onResponse(JSONObject object, Boolean isError) throws JSONException {
                    if (!isError) {
                        Log.d(TAG, "Suit chosen successfully: " + suit);
                    } else {
                        Log.e(TAG, "Error choosing suit: " + object.getString("message"));
                    }
                }

                @Override
                public void onError(Throwable t) {
                    Log.e(TAG, "Error choosing suit: " + t.getMessage());
                }
            });
            SocketManager.sendRequest(context, request);
        } catch (JSONException e) {
            Log.e(TAG, "Error in chooseSuit: " + e.getMessage());
        }
    }

    public void startGame(String roomNumber) {
        try {
            JSONObject data = new JSONObject();
            data.put("event", "start_game");
            data.put("roomNumber", roomNumber);

            SocketRequest request = new SocketRequest(null, data, new SocketManager.Response() {
                @Override
                public void onResponse(JSONObject object, Boolean isError) throws JSONException {
                    if (!isError) {
                        gameId = object.getString("gameId"); // ذخیره gameId از پاسخ
                        Log.d(TAG, "Game started successfully for room: " + roomNumber + ", gameId: " + gameId);
                    } else {
                        Log.e(TAG, "Error starting game: " + object.getString("message"));
                    }
                }

                @Override
                public void onError(Throwable t) {
                    Log.e(TAG, "Error starting game: " + t.getMessage());
                }
            });
            SocketManager.sendRequest(context, request);
        } catch (JSONException e) {
            Log.e(TAG, "Error in startGame: " + e.getMessage());
        }
    }
}