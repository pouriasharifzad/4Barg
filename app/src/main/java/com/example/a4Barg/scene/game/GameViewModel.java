package com.example.a4Barg.scene.game;

import android.app.Application;
import android.util.Log;

import androidx.lifecycle.AndroidViewModel;

import com.example.a4Barg.model.SocketRequest;
import com.example.a4Barg.networking.SocketManager;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

public class GameViewModel extends AndroidViewModel {

    private String userId;
    private String gameId;

    public GameViewModel(Application application) {
        super(application);
    }

    public void setUserId(String userId) {
        this.userId = userId;
        Log.d("TEST", "GameViewModel - userId set to: " + userId);
    }

    public void startGame(String roomNumber) {
        Map<String, Object> dataMap = new HashMap<>();
        dataMap.put("roomNumber", roomNumber);
        dataMap.put("event", "start_game");

        try {
            JSONObject data = new JSONObject(dataMap);
            Log.d("TEST", "startGame - Sending request: " + data.toString());
            SocketRequest request = new SocketRequest(null, data, new SocketManager.Response() {
                @Override
                public void onResponse(JSONObject object, Boolean isError) throws JSONException {
                    Log.d("TEST", "startGame response received: " + object.toString());
                    if (object.getBoolean("success")) {
                        gameId = object.getString("gameId");
                        Log.d("TEST", "Game started successfully - gameId: " + gameId);
                    } else {
                        Log.e("TEST", "Failed to start game: " + object.getString("message"));
                    }
                }

                @Override
                public void onError(Throwable t) {
                    Log.e("TEST", "startGame error: " + t.getMessage());
                }
            });
            SocketManager.sendRequest(getApplication(), request);
            Log.d("TEST", "startGame request sent");
        } catch (JSONException e) {
            Log.e("TEST", "Error preparing start_game request: " + e.getMessage());
        }
    }

    public void requestPlayerCards(String gameId) {
        Map<String, Object> dataMap = new HashMap<>();
        dataMap.put("gameId", gameId);
        dataMap.put("userId", userId);
        dataMap.put("event", "get_player_cards");

        try {
            JSONObject data = new JSONObject(dataMap);
            Log.d("TEST", "requestPlayerCards - Sending request: " + data.toString());
            SocketRequest request = new SocketRequest(null, data, new SocketManager.Response() {
                @Override
                public void onResponse(JSONObject object, Boolean isError) throws JSONException {
                    Log.d("TEST", "requestPlayerCards response received: " + object.toString());
                    if (object.getBoolean("success")) {
                        Log.d("TEST", "Player cards requested successfully");
                    } else {
                        Log.e("TEST", "Failed to request player cards: " + object.getString("message"));
                    }
                }

                @Override
                public void onError(Throwable t) {
                    Log.e("TEST", "requestPlayerCards error: " + t.getMessage());
                }
            });
            SocketManager.sendRequest(getApplication(), request);
            Log.d("TEST", "requestPlayerCards request sent");
        } catch (JSONException e) {
            Log.e("TEST", "Error preparing get_player_cards request: " + e.getMessage());
        }
    }

    // متد جدید برای درخواست اطلاعات بازیکن‌ها
    public void requestGamePlayersInfo(String gameId) {
        SocketManager.getGamePlayersInfo(getApplication(), gameId, userId, new SocketManager.GamePlayersInfoListener() {
            @Override
            public void onGamePlayersInfo(JSONObject data) {
                Log.d("TEST", "requestGamePlayersInfo - Received data: " + data.toString());
            }

            @Override
            public void onGamePlayersInfoError(Throwable t) {
                Log.e("TEST", "requestGamePlayersInfo error: " + t.getMessage());
            }
        });
    }
}