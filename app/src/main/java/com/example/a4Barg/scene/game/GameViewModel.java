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
    private String gameId; // متغیر جدید برای gameId

    public GameViewModel(Application application) {
        super(application);
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public void startGame(String roomNumber) {
        Map<String, Object> dataMap = new HashMap<>();
        dataMap.put("roomNumber", roomNumber);
        dataMap.put("event", "start_game");

        try {
            JSONObject data = new JSONObject(dataMap);
            SocketRequest request = new SocketRequest(null, data, new SocketManager.Response() {
                @Override
                public void onResponse(JSONObject object, Boolean isError) throws JSONException {
                    if (object.getBoolean("success")) {
                        gameId = object.getString("gameId"); // ذخیره gameId
                        Log.d("TEST", "Game started successfully: " + object.toString());
                    } else {
                        Log.e("TEST", "Failed to start game: " + object.getString("message"));
                    }
                }

                @Override
                public void onError(Throwable t) {
                    Log.e("TEST", "Error starting game: " + t.getMessage());
                }
            });
            SocketManager.sendRequest(getApplication(), request);
        } catch (JSONException e) {
            Log.e("TEST", "Error preparing start game request: " + e.getMessage());
        }
    }

    public void requestPlayerCards(String gameId) { // تغییر پارامتر به gameId
        Map<String, Object> dataMap = new HashMap<>();
        dataMap.put("gameId", gameId); // استفاده از gameId دریافت‌شده
        dataMap.put("userId", userId);
        dataMap.put("event", "get_player_cards");

        try {
            JSONObject data = new JSONObject(dataMap);
            SocketRequest request = new SocketRequest(null, data, new SocketManager.Response() {
                @Override
                public void onResponse(JSONObject object, Boolean isError) throws JSONException {
                    if (object.getBoolean("success")) {
                        Log.d("TEST", "Player cards requested successfully: " + object.toString());
                    } else {
                        Log.e("TEST", "Failed to request player cards: " + object.getString("message"));
                    }
                }

                @Override
                public void onError(Throwable t) {
                    Log.e("TEST", "Error requesting player cards: " + t.getMessage());
                }
            });
            SocketManager.sendRequest(getApplication(), request);
        } catch (JSONException e) {
            Log.e("TEST", "Error preparing player cards request: " + e.getMessage());
        }
    }
}