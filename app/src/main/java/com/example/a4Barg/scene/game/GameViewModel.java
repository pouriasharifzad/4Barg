package com.example.a4Barg.scene.game;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.a4Barg.model.Card;
import com.example.a4Barg.model.InGameMessage;
import com.example.a4Barg.model.SocketRequest;
import com.example.a4Barg.networking.SocketManager;
import com.example.a4Barg.utils.CardContainerView;

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
    private final MutableLiveData<List<List<Card>>> possibleOptions = new MutableLiveData<>();
    private final MutableLiveData<Boolean> gameOver = new MutableLiveData<>(false);
    private final MutableLiveData<Integer> userScore = new MutableLiveData<>(0);
    private final MutableLiveData<Integer> opponentScore = new MutableLiveData<>(0);
    private final MutableLiveData<String> winner = new MutableLiveData<>();
    private final MutableLiveData<String> gameResultText = new MutableLiveData<>("");
    private final MutableLiveData<InGameMessage> inGameMessage = new MutableLiveData<>();
    private Card pendingCard;
    private boolean playersInfoRequested = false;
    private float lastDropX = 0f;
    private float lastDropY = 0f;
    private float lastDropRotation = 0f;
    private GameActivity activity;
    private boolean isAnimating = false;
    private List<Card> pendingTableCardsUpdate = null;

    public GameViewModel(Application application) {
        super(application);
    }

    public void setActivity(GameActivity activity) {
        this.activity = activity;
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
        this.activity = activity;
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
                        if (isAnimating) {
                            pendingTableCardsUpdate = cards;
                        } else {
                            Log.d("GameViewModel", "Updating table cards: " + cards.size() + " cards");
                            tableCards.setValue(cards);
                        }
                    }
                } catch (JSONException e) {
                }
            }

            @Override
            public void onPlayerCardsError(Throwable t) {
                activity.runOnUiThread(() -> Log.e("GameViewModel", "Error receiving player cards: " + t.getMessage()));
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
                            gameResultText.setValue(resultText);
                        }
                    }
                    if (data.has("tableCards")) {
                        JSONArray tableCardsArray = data.getJSONArray("tableCards");
                        List<Card> cards = new ArrayList<>();
                        for (int i = 0; i < tableCardsArray.length(); i++) {
                            JSONObject cardObj = tableCardsArray.getJSONObject(i);
                            cards.add(new Card(cardObj.getString("suit"), cardObj.getString("value")));
                        }
                        if (isAnimating) {
                            pendingTableCardsUpdate = cards;
                        } else {
                            Log.d("GameViewModel", "Updating table cards from game state: " + cards.size() + " cards");
                            tableCards.setValue(cards);
                        }
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
                        activity.runOnUiThread(() -> Log.d("GameViewModel", "Sur event triggered!"));
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
                                    activity.runOnUiThread(() -> Log.e("GameViewModel", "Error receiving player info: " + t.getMessage()));
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
                        setPossibleOptions(combinations);
                        activity.runOnUiThread(() -> {
                            Log.d("GameViewModel", "Select combination triggered with options: " + combinations.size());
                            activity.showOptions(combinations);
                        });
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
                        setPossibleOptions(options);
                        activity.runOnUiThread(() -> {
                            Log.d("GameViewModel", "Select king or queen triggered with options: " + options.size());
                            activity.showOptions(options);
                        });
                    }
                } catch (JSONException e) {
                }
            }
        });

        SocketManager.addCustomListener("receive_in_game_message", new SocketManager.CustomListener() {
            @Override
            public void onEvent(JSONObject data) {
                try {
                    String senderUserId = data.getString("userId");
                    String message = data.getString("message");
                    inGameMessage.setValue(new InGameMessage(senderUserId, message));
                } catch (JSONException e) {
                    Log.e("GameViewModel", "Error parsing in-game message: " + e.getMessage());
                }
            }
        });

        SocketManager.addCustomListener("played_card", new SocketManager.CustomListener() {
            @Override
            public void onEvent(JSONObject data) {
                try {
                    String playerId = data.getString("userId");
                    JSONObject cardObj = data.getJSONObject("card");
                    String suit = cardObj.getString("suit");
                    String value = cardObj.getString("value");
                    Card playedCard = new Card(suit, value);
                    boolean isUser = playerId.equals(userId);
                    boolean isCollected = data.optBoolean("isCollected", false);
                    List<Card> tableCardsToCollect = new ArrayList<>();
                    if (data.has("tableCards")) {
                        JSONArray tableCardsArray = data.getJSONArray("tableCards");
                        tableCardsToCollect = parseCards(tableCardsArray);
                        Log.d("GameViewModel", "Received tableCardsToCollect from server: " + tableCardsToCollect.size() + " cards - " + tableCardsToCollect.toString());
                    } else {
                        Log.w("GameViewModel", "No tableCards found in played_card event, assuming empty");
                    }

                    if (isCollected) {
                        Log.d("GameViewModel", String.format("Card %s of %s collected by %s", value, suit, isUser ? "user" : "opponent"));
                        isAnimating = true;
                        List<Card> finalTableCardsToCollect = tableCardsToCollect;
                        List<Card> finalTableCardsToCollect1 = tableCardsToCollect;
                        activity.runOnUiThread(() -> {
                            float startX, startY, startRotation;
                            if (isUser) {
                                startX = lastDropX;
                                startY = lastDropY;
                                startRotation = lastDropRotation;
                                activity.getUserHandView().removeCardFromHand(playedCard);
                            } else {
                                startX = activity.getOpponentHandView().getX() + (activity.getOpponentHandView().getWidth() / 2f);
                                startY = activity.getOpponentHandView().getY() + (activity.getOpponentHandView().getHeight() / 2f);
                                startRotation = 0f;
                            }
                            activity.animateCard(playedCard, isUser, startX, startY, startRotation, finalTableCardsToCollect, () -> {
                                isAnimating = false;
                                List<Card> currentTableCards = tableCards.getValue();
                                if (currentTableCards != null) {
                                    List<Card> updatedTableCards = new ArrayList<>(currentTableCards);
                                    updatedTableCards.removeAll(finalTableCardsToCollect1);
                                    updatedTableCards.remove(playedCard);
                                    tableCards.setValue(updatedTableCards);
                                    Log.d("GameViewModel", "Table cards updated after collection: " + updatedTableCards.size() + " cards remaining");
                                }
                                if (pendingTableCardsUpdate != null) {
                                    Log.d("GameViewModel", "Applying pending table cards update: " + pendingTableCardsUpdate.size() + " cards");
                                    tableCards.setValue(pendingTableCardsUpdate);
                                    pendingTableCardsUpdate = null;
                                }
                            });
                        });
                    } else {
                        isAnimating = true;
                        float[] lastCardPosition = activity.getTableView().getLastCardPosition();
                        float endX = lastCardPosition[0];
                        float endY = lastCardPosition[1];
                        Log.d("GameViewModel", String.format("Card %s of %s played by %s to position (%.2f, %.2f)", value, suit, isUser ? "user" : "opponent", endX, endY));
                        List<Card> finalTableCardsToCollect2 = tableCardsToCollect;
                        activity.runOnUiThread(() -> {
                            float startX, startY, startRotation;
                            if (isUser) {
                                startX = lastDropX;
                                startY = lastDropY;
                                startRotation = lastDropRotation;
                                activity.getUserHandView().removeCardFromHand(playedCard);
                            } else {
                                startX = activity.getOpponentHandView().getX() + (activity.getOpponentHandView().getWidth() / 2f);
                                startY = activity.getOpponentHandView().getY() + (activity.getOpponentHandView().getHeight() / 2f);
                                startRotation = 0f;
                            }
                            activity.animateCard(playedCard, isUser, startX, startY, startRotation, finalTableCardsToCollect2, () -> {
                                isAnimating = false;
                                if (pendingTableCardsUpdate != null) {
                                    Log.d("GameViewModel", "Applying pending table cards update: " + pendingTableCardsUpdate.size() + " cards");
                                    tableCards.setValue(pendingTableCardsUpdate);
                                    pendingTableCardsUpdate = null;
                                }
                            });
                        });
                    }
                } catch (JSONException e) {
                    Log.e("GameViewModel", "Error parsing played_card event: " + e.getMessage());
                }
            }
        });
    }

    private List<Card> parseCards(JSONArray cardsArray) throws JSONException {
        List<Card> cards = new ArrayList<>();
        for (int i = 0; i < cardsArray.length(); i++) {
            JSONObject cardObj = cardsArray.getJSONObject(i);
            cards.add(new Card(cardObj.getString("suit"), cardObj.getString("value")));
        }
        return cards;
    }

    public void playCard(Card card, List<Card> tableCardsToCollect) {
        if (card == null) {
            Log.e("GameViewModel", "Attempted to play a null card");
            return;
        }
        if (gameId == null || gameId.isEmpty()) {
            Log.e("GameViewModel", "gameId is null or empty, cannot play card");
            activity.showError("بازی پیدا نشد. لطفاً دوباره تلاش کنید.");
            return;
        }
        StringBuilder collectLog = new StringBuilder("Collecting cards: ");
        for (Card c : tableCardsToCollect) {
            collectLog.append(c.toString()).append(", ");
        }
        Log.d("GameViewModel", "Playing card: " + card.toString() + ", " + collectLog);
        JSONObject data = new JSONObject();
        try {
            data.put("event", "play_card");
            data.put("gameId", gameId);
            data.put("userId", userId);
            data.put("card", new JSONObject().put("suit", card.getSuit()).put("value", card.getRank()));
            if (tableCardsToCollect == null || tableCardsToCollect.isEmpty()) {
                data.put("isAddToTable", true);
            } else {
                JSONArray tableCardsJson = new JSONArray();
                for (Card c : tableCardsToCollect) {
                    tableCardsJson.put(new JSONObject().put("suit", c.getSuit()).put("value", c.getRank()));
                }
                data.put("tableCards", tableCardsJson);
            }
            SocketRequest request = new SocketRequest(null, data, new SocketManager.Response() {
                @Override
                public void onResponse(JSONObject object, Boolean isError) throws JSONException {
                    if (!isError && object.getBoolean("success")) {
                        Log.d("GameViewModel", "Play card successful, resetting pendingCard and options");
                        new Handler(Looper.getMainLooper()).post(() -> {
                            pendingCard = null;
                            setPossibleOptions(null);
                        });
                    } else {
                        Log.e("GameViewModel", "Play card failed: " + (isError ? "Error" : object.optString("message", "Unknown")));
                        new Handler(Looper.getMainLooper()).post(() -> {
                            pendingCard = null;
                            setPossibleOptions(null);
                            activity.showError("خطا در ارسال درخواست به سرور: " + object.optString("message", "لطفاً دوباره تلاش کنید."));
                        });
                    }
                }

                @Override
                public void onError(Throwable t) {
                    Log.e("GameViewModel", "Play card error: " + t.getMessage());
                    new Handler(Looper.getMainLooper()).post(() -> {
                        pendingCard = null;
                        setPossibleOptions(null);
                        activity.showError("خطا در ارسال درخواست به سرور. لطفاً دوباره تلاش کنید.");
                    });
                }
            });
            SocketManager.sendRequest(getApplication(), request);
        } catch (JSONException e) {
            Log.e("GameViewModel", "Error creating play card JSON: " + e.getMessage());
            new Handler(Looper.getMainLooper()).post(() -> {
                pendingCard = null;
                setPossibleOptions(null);
                activity.showError("خطا در پردازش درخواست. لطفاً دوباره تلاش کنید.");
            });
        }
    }

    public void sendInGameMessage(String message) {
        JSONObject data = new JSONObject();
        try {
            data.put("event", "send_in_game_message");
            data.put("gameId", gameId);
            data.put("userId", userId);
            data.put("message", message);
            SocketRequest request = new SocketRequest(null, data, new SocketManager.Response() {
                @Override
                public void onResponse(JSONObject object, Boolean isError) throws JSONException {
                    if (!isError && object.getBoolean("success")) {
                        inGameMessage.setValue(new InGameMessage(userId, message));
                    }
                }

                @Override
                public void onError(Throwable t) {
                    Log.e("GameViewModel", "Error sending in-game message: " + t.getMessage());
                }
            });
            SocketManager.sendRequest(getApplication(), request);
        } catch (JSONException e) {
            Log.e("GameViewModel", "Error creating message JSON: " + e.getMessage());
        }
    }

    public void setLastDropPosition(float x, float y, float rotation) {
        this.lastDropX = x;
        this.lastDropY = y;
        this.lastDropRotation = rotation;
    }

    public void setPendingCard(Card card) {
        this.pendingCard = card;
        Log.d("GameViewModel", "Pending card set to: " + (card != null ? card.toString() : "null"));
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
    public LiveData<List<List<Card>>> getPossibleOptions() { return possibleOptions; }
    public LiveData<Boolean> getGameOver() { return gameOver; }
    public LiveData<Integer> getUserScore() { return userScore; }
    public LiveData<Integer> getOpponentScore() { return opponentScore; }
    public LiveData<String> getWinner() { return winner; }
    public LiveData<String> getGameResultText() { return gameResultText; }
    public LiveData<InGameMessage> getInGameMessage() { return inGameMessage; }
    public Card getPendingCard() { return pendingCard; }

    public void setPossibleOptions(List<List<Card>> options) {
        if (options == null) {
            pendingCard = null;
        }
        possibleOptions.setValue(options);
    }

    public void selectOption(List<Card> selectedOption) {
        if (pendingCard == null) {
            Log.e("GameViewModel", "Pending card is null, cannot select option. Current options: " + (possibleOptions.getValue() != null ? possibleOptions.getValue().size() : 0));
            setPossibleOptions(null);
            return;
        }
        Log.d("GameViewModel", "Selecting option with pending card: " + pendingCard.toString() + ", selected cards: " + selectedOption.size());
        playCard(pendingCard, selectedOption);
    }

    public CardContainerView getUserHandView() {
        return activity.getUserHandView();
    }

    public CardContainerView getOpponentHandView() {
        return activity.getOpponentHandView();
    }
}