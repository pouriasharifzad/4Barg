package com.example.a4Barg.scene.game;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.a4Barg.model.Card;
import com.example.a4Barg.model.SocketRequest;
import com.example.a4Barg.networking.SocketManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GameViewModel extends AndroidViewModel {

    private String userId;
    private String gameId;
    private String roomNumber;
    private final MutableLiveData<List<Card>> userCards = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<Integer> opponentCardCount = new MutableLiveData<>(0);
    private final MutableLiveData<List<Card>> tableCards = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<List<Card>> userCollectedCards = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<List<Card>> opponentCollectedCards = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<String> currentTurn = new MutableLiveData<>();
    private final MutableLiveData<String[]> userInfo = new MutableLiveData<>();
    private final MutableLiveData<String[]> opponentInfo = new MutableLiveData<>();
    private final MutableLiveData<Integer> userSurs = new MutableLiveData<>(0);
    private final MutableLiveData<Integer> opponentSurs = new MutableLiveData<>(0);
    private final MutableLiveData<List<List<Card>>> possibleCombinations = new MutableLiveData<>();
    private final MutableLiveData<Boolean> gameOver = new MutableLiveData<>(false);
    private final MutableLiveData<Integer> userScore = new MutableLiveData<>(0);
    private final MutableLiveData<Integer> opponentScore = new MutableLiveData<>(0);
    private final MutableLiveData<String> winner = new MutableLiveData<>();
    private final MutableLiveData<String> gameResultText = new MutableLiveData<>("");
    private Card pendingCard;
    private boolean playersInfoRequested = false;

    public GameViewModel(Application application) {
        super(application);
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public void startGame(String roomNumber) {
        this.roomNumber = roomNumber;
        Map<String, Object> dataMap = new HashMap<>();
        dataMap.put("roomNumber", roomNumber);
        dataMap.put("event", "start_game");

        try {
            JSONObject data = new JSONObject(dataMap);
            SocketRequest request = new SocketRequest(null, data, new SocketManager.Response() {
                @Override
                public void onResponse(JSONObject object, Boolean isError) throws JSONException {
                    if (object.getBoolean("success")) {
                        gameId = object.getString("gameId");
                    }
                }

                @Override
                public void onError(Throwable t) {
                }
            });
            SocketManager.sendRequest(getApplication(), request);
        } catch (JSONException e) {
        }
    }

    public void requestPlayerCards(String gameId) {
        Map<String, Object> dataMap = new HashMap<>();
        dataMap.put("gameId", gameId);
        dataMap.put("userId", userId);
        dataMap.put("event", "get_player_cards");

        try {
            JSONObject data = new JSONObject(dataMap);
            SocketRequest request = new SocketRequest(null, data, new SocketManager.Response() {
                @Override
                public void onResponse(JSONObject object, Boolean isError) throws JSONException {
                }

                @Override
                public void onError(Throwable t) {
                }
            });
            SocketManager.sendRequest(getApplication(), request);
        } catch (JSONException e) {
        }
    }

    public void setupGameListeners(GameActivity activity) {
        SocketManager.addPlayerCardsListener(new SocketManager.PlayerCardsListener() {
            @Override
            public void onPlayerCards(JSONObject data) {
                try {
                    String receivedUserId = data.getString("userId");
                    if (receivedUserId.equals(userId)) {
                        JSONArray cardsArray = data.getJSONArray("cards");
                        List<Card> cards = new ArrayList<>();
                        for (int i = 0; i < cardsArray.length(); i++) {
                            JSONObject cardObj = cardsArray.getJSONObject(i);
                            cards.add(new Card(cardObj.getString("suit"), cardObj.optString("value", "unknown")));
                        }
                        userCards.setValue(cards);
                    }
                    if (data.has("tableCards")) {
                        JSONArray tableCardsArray = data.getJSONArray("tableCards");
                        List<Card> cards = new ArrayList<>();
                        for (int i = 0; i < tableCardsArray.length(); i++) {
                            JSONObject cardObj = tableCardsArray.getJSONObject(i);
                            cards.add(new Card(cardObj.getString("suit"), cardObj.getString("value")));
                        }
                        tableCards.setValue(cards);
                    }
                } catch (JSONException e) {
                }
            }

            @Override
            public void onPlayerCardsError(Throwable t) {
                activity.runOnUiThread(() -> Toast.makeText(activity, "خطا در دریافت کارت‌ها: " + t.getMessage(), Toast.LENGTH_SHORT).show());
            }
        });

        SocketManager.listenForGameStateUpdates(new SocketManager.GameStateUpdateListener() {
            @Override
            public void onGameStateUpdate(JSONObject data) {
                try {
                    if (data.has("players")) {
                        JSONArray players = data.getJSONArray("players");
                        for (int i = 0; i < players.length(); i++) {
                            JSONObject player = players.getJSONObject(i);
                            String playerId = player.getString("userId");
                            if (!playerId.equals(userId)) {
                                opponentCardCount.setValue(player.getInt("cardCount"));
                            }
                        }
                    }
                    if (data.has("gameOver") && data.getBoolean("gameOver")) {
                        opponentCardCount.setValue(0);
                        if (!data.has("tableCards")) {
                            tableCards.setValue(new ArrayList<>());
                        }
                        gameOver.setValue(true);
                        // گرفتن امتیازات و برنده و تنظیم متن برای TextView
                        if (data.has("scores") && data.has("winner")) {
                            JSONArray scores = data.getJSONArray("scores");
                            int userScoreValue = 0, opponentScoreValue = 0;
                            for (int i = 0; i < scores.length(); i++) {
                                JSONObject scoreObj = scores.getJSONObject(i);
                                String playerId = scoreObj.getString("userId");
                                int score = scoreObj.getInt("score");
                                if (playerId.equals(userId)) {
                                    userScore.setValue(score);
                                    userScoreValue = score;
                                } else {
                                    opponentScore.setValue(score);
                                    opponentScoreValue = score;
                                }
                            }
                            String winnerId = data.getString("winner");
                            winner.setValue(winnerId);
                            String winnerMessage = winnerId.equals(userId) ? "شما برنده شدید!" : "حریف شما برنده شد!";
                            String resultText = String.format(
                                    "بازی تموم شد!\nامتیاز شما: %d\nامتیاز حریف: %d\n%s",
                                    userScoreValue, opponentScoreValue, winnerMessage
                            );
                            gameResultText.setValue(resultText); // تنظیم متن برای TextView

                            // لاگ کردن کارت‌های خاص با تگ "score"
                            List<Card> userCards = userCollectedCards.getValue();
                            List<Card> opponentCards = opponentCollectedCards.getValue();
                            int userSursCount = userSurs.getValue() != null ? userSurs.getValue() : 0;
                            int opponentSursCount = opponentSurs.getValue() != null ? opponentSurs.getValue() : 0;

                            StringBuilder userLog = new StringBuilder("User Collected Cards (Scoring):\n");
                            StringBuilder opponentLog = new StringBuilder("Opponent Collected Cards (Scoring):\n");

                            // کارت‌های خاج
                            if (userCards != null) {
                                userLog.append("Clubs: ");
                                for (Card card : userCards) {
                                    if ("Clubs".equals(card.getSuit())) {
                                        userLog.append(card.getSuit()).append(" ").append(card.getRank()).append(", ");
                                    }
                                }
                                userLog.append("\n");
                            }
                            if (opponentCards != null) {
                                opponentLog.append("Clubs: ");
                                for (Card card : opponentCards) {
                                    if ("Clubs".equals(card.getSuit())) {
                                        opponentLog.append(card.getSuit()).append(" ").append(card.getRank()).append(", ");
                                    }
                                }
                                opponentLog.append("\n");
                            }

                            // کارت 10 خشت
                            if (userCards != null) {
                                userLog.append("10 of Diamonds: ");
                                for (Card card : userCards) {
                                    if ("Diamonds".equals(card.getSuit()) && "10".equals(card.getRank())) {
                                        userLog.append(card.getSuit()).append(" ").append(card.getRank()).append(", ");
                                    }
                                }
                                userLog.append("\n");
                            }
                            if (opponentCards != null) {
                                opponentLog.append("10 of Diamonds: ");
                                for (Card card : opponentCards) {
                                    if ("Diamonds".equals(card.getSuit()) && "10".equals(card.getRank())) {
                                        opponentLog.append(card.getSuit()).append(" ").append(card.getRank()).append(", ");
                                    }
                                }
                                opponentLog.append("\n");
                            }

                            // کارت‌های آس
                            if (userCards != null) {
                                userLog.append("Aces: ");
                                for (Card card : userCards) {
                                    if ("Ace".equals(card.getRank())) {
                                        userLog.append(card.getSuit()).append(" ").append(card.getRank()).append(", ");
                                    }
                                }
                                userLog.append("\n");
                            }
                            if (opponentCards != null) {
                                opponentLog.append("Aces: ");
                                for (Card card : opponentCards) {
                                    if ("Ace".equals(card.getRank())) {
                                        opponentLog.append(card.getSuit()).append(" ").append(card.getRank()).append(", ");
                                    }
                                }
                                opponentLog.append("\n");
                            }

                            // کارت‌های سرباز
                            if (userCards != null) {
                                userLog.append("Jacks: ");
                                for (Card card : userCards) {
                                    if ("Jack".equals(card.getRank())) {
                                        userLog.append(card.getSuit()).append(" ").append(card.getRank()).append(", ");
                                    }
                                }
                                userLog.append("\n");
                            }
                            if (opponentCards != null) {
                                opponentLog.append("Jacks: ");
                                for (Card card : opponentCards) {
                                    if ("Jack".equals(card.getRank())) {
                                        opponentLog.append(card.getSuit()).append(" ").append(card.getRank()).append(", ");
                                    }
                                }
                                opponentLog.append("\n");
                            }

                            // تعداد سورها
                            userLog.append("Surs: ").append(userSursCount).append("\n");
                            opponentLog.append("Surs: ").append(opponentSursCount).append("\n");

                            Log.d("score", userLog.toString());
                            Log.d("score", opponentLog.toString());
                        }
                    }
                    if (data.has("tableCards")) {
                        JSONArray tableCardsArray = data.getJSONArray("tableCards");
                        List<Card> cards = new ArrayList<>();
                        for (int i = 0; i < tableCardsArray.length(); i++) {
                            JSONObject cardObj = tableCardsArray.getJSONObject(i);
                            cards.add(new Card(cardObj.getString("suit"), cardObj.getString("value")));
                        }
                        tableCards.setValue(cards);
                    }
                    if (data.has("currentTurn")) {
                        currentTurn.setValue(data.getString("currentTurn"));
                    }
                    if (data.has("collectedCards")) {
                        JSONArray collected = data.getJSONArray("collectedCards");
                        for (int i = 0; i < collected.length(); i++) {
                            JSONObject player = collected.getJSONObject(i);
                            String playerId = player.getString("userId");
                            JSONArray cardsArray = player.getJSONArray("cards");
                            List<Card> cards = new ArrayList<>();
                            for (int j = 0; j < cardsArray.length(); j++) {
                                JSONObject cardObj = cardsArray.getJSONObject(j);
                                cards.add(new Card(cardObj.getString("suit"), cardObj.getString("value")));
                            }
                            if (playerId.equals(userId)) {
                                userCollectedCards.setValue(cards);
                            } else {
                                opponentCollectedCards.setValue(cards);
                            }
                        }
                    }
                    if (data.has("surs")) {
                        JSONArray surs = data.getJSONArray("surs");
                        for (int i = 0; i < surs.length(); i++) {
                            JSONObject surObj = surs.getJSONObject(i);
                            String playerId = surObj.getString("userId");
                            int surCount = surObj.getInt("count");
                            if (playerId.equals(userId)) {
                                userSurs.setValue(surCount);
                            } else {
                                opponentSurs.setValue(surCount);
                            }
                        }
                    }
                    if (data.has("surEvent") && data.getBoolean("surEvent")) {
                        activity.runOnUiThread(() -> Toast.makeText(activity, "سور زده شد!", Toast.LENGTH_SHORT).show());
                    }
                } catch (JSONException e) {
                }
            }

            @Override
            public void onGameStateUpdateError(Throwable t) {
            }
        });

        SocketManager.listenForGameStart(new SocketManager.GameStartListener() {
            @Override
            public void onGameStart(JSONObject data) {
                try {
                    String receivedRoomNumber = data.getString("roomNumber");
                    if (receivedRoomNumber.equals(roomNumber)) {
                        gameId = data.getString("gameId");
                        requestPlayerCards(gameId);
                        if (!playersInfoRequested) {
                            SocketManager.getGamePlayersInfo(activity, gameId, userId, null);
                            SocketManager.addGamePlayersInfoListener(new SocketManager.GamePlayersInfoListener() {
                                @Override
                                public void onGamePlayersInfo(JSONObject data) {
                                    try {
                                        JSONArray players = data.getJSONArray("players");
                                        for (int i = 0; i < players.length(); i++) {
                                            JSONObject player = players.getJSONObject(i);
                                            String playerId = player.getString("userId");
                                            String[] info = new String[]{
                                                    player.getString("username"),
                                                    String.valueOf(player.getInt("exp")),
                                                    String.valueOf(player.getInt("coins"))
                                            };
                                            if (playerId.equals(userId)) {
                                                userInfo.setValue(info);
                                            } else {
                                                opponentInfo.setValue(info);
                                            }
                                        }
                                        playersInfoRequested = true;
                                    } catch (JSONException e) {
                                    }
                                }

                                @Override
                                public void onGamePlayersInfoError(Throwable t) {
                                    activity.runOnUiThread(() -> Toast.makeText(activity, "خطا در دریافت اطلاعات بازیکن‌ها: " + t.getMessage(), Toast.LENGTH_SHORT).show());
                                }
                            });
                        }
                    }
                } catch (JSONException e) {
                }
            }

            @Override
            public void onGameStartError(Throwable t) {
            }
        });

        SocketManager.addCustomListener("select_combination", new SocketManager.CustomListener() {
            @Override
            public void onEvent(JSONObject data) {
                try {
                    String receivedUserId = data.getString("userId");
                    if (receivedUserId.equals(userId)) {
                        JSONObject cardObj = data.getJSONObject("card");
                        pendingCard = new Card(cardObj.getString("suit"), cardObj.getString("value"));
                        JSONArray combinationsArray = data.getJSONArray("combinations");
                        List<List<Card>> combinations = new ArrayList<>();
                        for (int i = 0; i < combinationsArray.length(); i++) {
                            JSONArray comboArray = combinationsArray.getJSONArray(i);
                            List<Card> combo = new ArrayList<>();
                            for (int j = 0; j < comboArray.length(); j++) {
                                JSONObject comboCard = comboArray.getJSONObject(j);
                                combo.add(new Card(comboCard.getString("suit"), comboCard.getString("value")));
                            }
                            combinations.add(combo);
                        }
                        possibleCombinations.setValue(combinations);
                        activity.runOnUiThread(() -> Toast.makeText(activity, "لطفاً کارت‌های مورد نظرت رو انتخاب کن", Toast.LENGTH_LONG).show());
                    }
                } catch (JSONException e) {
                }
            }
        });

        SocketManager.addCustomListener("select_king_or_queen", new SocketManager.CustomListener() {
            @Override
            public void onEvent(JSONObject data) {
                try {
                    String receivedUserId = data.getString("userId");
                    if (receivedUserId.equals(userId)) {
                        JSONObject cardObj = data.getJSONObject("card");
                        pendingCard = new Card(cardObj.getString("suit"), cardObj.getString("value"));
                        JSONArray optionsArray = data.getJSONArray("options");
                        List<List<Card>> options = new ArrayList<>();
                        for (int i = 0; i < optionsArray.length(); i++) {
                            JSONObject option = optionsArray.getJSONObject(i);
                            List<Card> singleCardList = new ArrayList<>();
                            singleCardList.add(new Card(option.getString("suit"), option.getString("value")));
                            options.add(singleCardList);
                        }
                        possibleCombinations.setValue(options);
                        activity.runOnUiThread(() -> Toast.makeText(activity, "لطفاً " + pendingCard.getRank() + " مورد نظرت رو انتخاب کن", Toast.LENGTH_LONG).show());
                    }
                } catch (JSONException e) {
                }
            }
        });
    }

    public void playCard(Card card, List<Card> tableCardsToCollect) {
        JSONObject data = new JSONObject();
        try {
            data.put("event", "play_card");
            data.put("gameId", gameId);
            data.put("userId", userId);
            data.put("card", new JSONObject().put("suit", card.getSuit()).put("value", card.getRank()));
            JSONArray tableCardsJson = new JSONArray();
            for (Card c : tableCardsToCollect) {
                tableCardsJson.put(new JSONObject().put("suit", c.getSuit()).put("value", c.getRank()));
            }
            data.put("tableCards", tableCardsJson);
            SocketRequest request = new SocketRequest(null, data, new SocketManager.Response() {
                @Override
                public void onResponse(JSONObject object, Boolean isError) throws JSONException {
                    if (!isError && object.getBoolean("success")) {
                        new Handler(Looper.getMainLooper()).post(() -> {
                            pendingCard = null;
                            possibleCombinations.setValue(null);
                        });
                    }
                }

                @Override
                public void onError(Throwable t) {
                }
            });
            SocketManager.sendRequest(getApplication(), request);
        } catch (JSONException e) {
        }
    }

    public LiveData<List<Card>> getUserCards() { return userCards; }
    public LiveData<Integer> getOpponentCardCount() { return opponentCardCount; }
    public LiveData<List<Card>> getTableCards() { return tableCards; }
    public LiveData<List<Card>> getUserCollectedCards() { return userCollectedCards; }
    public LiveData<List<Card>> getOpponentCollectedCards() { return opponentCollectedCards; }
    public LiveData<String> getCurrentTurn() { return currentTurn; }
    public LiveData<String[]> getUserInfo() { return userInfo; }
    public LiveData<String[]> getOpponentInfo() { return opponentInfo; }
    public LiveData<Integer> getUserSurs() { return userSurs; }
    public LiveData<Integer> getOpponentSurs() { return opponentSurs; }
    public LiveData<List<List<Card>>> getPossibleCombinations() { return possibleCombinations; }
    public LiveData<Boolean> getGameOver() { return gameOver; }
    public LiveData<Integer> getUserScore() { return userScore; }
    public LiveData<Integer> getOpponentScore() { return opponentScore; }
    public LiveData<String> getWinner() { return winner; }
    public LiveData<String> getGameResultText() { return gameResultText; }
    public Card getPendingCard() { return pendingCard; }
}