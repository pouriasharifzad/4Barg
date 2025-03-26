package com.example.a4Barg.scene.room;

import android.app.Application;
import android.content.Intent;
import android.util.Log;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;

import com.example.a4Barg.model.Player;
import com.example.a4Barg.model.SocketRequest;
import com.example.a4Barg.networking.SocketManager;
import com.example.a4Barg.scene.lobby.LobbyActivity;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RoomViewModel extends AndroidViewModel {

    private String userId;
    private MutableLiveData<List<Player>> players = new MutableLiveData<>(new ArrayList<>());
    private MutableLiveData<String> errorMessage = new MutableLiveData<>();
    private MutableLiveData<String> roomNumber = new MutableLiveData<>();
    private MutableLiveData<JSONObject> roomDetails = new MutableLiveData<>();
    private boolean isLoadingRoomDetails = false;
    private boolean isRoomDetailsLoaded = false;
    private boolean isRoomPlayersLoaded = false;

    public RoomViewModel(Application application) {
        super(application);
        setupGameLoadingListener();
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public MutableLiveData<List<Player>> getPlayers() {
        return players;
    }

    public MutableLiveData<String> getErrorMessage() {
        return errorMessage;
    }

    public MutableLiveData<String> getRoomNumber() {
        return roomNumber;
    }

    public MutableLiveData<JSONObject> getRoomDetails() {
        return roomDetails;
    }

    public void loadRoomDetails(String roomNumber) {
        if (isLoadingRoomDetails || isRoomDetailsLoaded || !SocketManager.isConnect) {
            Log.d("TEST", "loadRoomDetails already loaded or in progress or not connected, skipping for roomNumber: " + roomNumber);
            return;
        }
        isLoadingRoomDetails = true;
        Map<String, Object> dataMap = new HashMap<>();
        dataMap.put("roomNumber", roomNumber);
        dataMap.put("event", "get_room_details");

        try {
            JSONObject data = new JSONObject(dataMap);
            SocketRequest request = new SocketRequest(null, data, new SocketManager.Response() {
                @Override
                public void onResponse(JSONObject object, Boolean isError) throws JSONException {
                    isLoadingRoomDetails = false;
                    if (!isError && object.has("success") && object.getBoolean("success")) {
                        roomDetails.postValue(object);
                        isRoomDetailsLoaded = true;
                        if (object.has("room") && object.getJSONObject("room").has("roomNumber")) {
                            String updatedRoomNumber = object.getJSONObject("room").getString("roomNumber");
                            RoomViewModel.this.roomNumber.postValue(updatedRoomNumber);
                            Log.d("TEST", "Room details loaded for roomNumber: " + updatedRoomNumber + ", full response: " + object.toString());
                        } else {
                            Log.w("TEST", "roomNumber not found in response: " + object.toString());
                        }
                    } else {
                        String errorMsg = object.has("message") ? object.getString("message") : "خطا در دریافت جزئیات روم";
                        errorMessage.postValue(errorMsg);
                    }
                }

                @Override
                public void onError(Throwable t) {
                    isLoadingRoomDetails = false;
                    errorMessage.postValue(t != null ? t.getMessage() : "خطا در ارتباط");
                }
            });
            SocketManager.sendRequest(getApplication(), request);
            Log.d("TEST", "loadRoomDetails called for roomNumber: " + roomNumber);
        } catch (JSONException e) {
            isLoadingRoomDetails = false;
            errorMessage.postValue("خطا در پردازش داده‌ها");
        }
    }

    public void loadRoomPlayers(String roomNumber) {
        if (isRoomPlayersLoaded || !SocketManager.isConnect) {
            Log.d("TEST", "loadRoomPlayers already loaded or not connected, skipping for roomNumber: " + roomNumber);
            return;
        }
        this.roomNumber.setValue(roomNumber);
        SocketManager.getRoomPlayers(getApplication(), roomNumber, new SocketManager.RoomPlayersResponseListener() {
            @Override
            public void onRoomPlayersResponse(JSONObject playersData) {
                try {
                    String receivedRoomNumber = playersData.getString("roomNumber");
                    if (receivedRoomNumber.equals(roomNumber)) {
                        JSONArray playersArray = playersData.getJSONArray("players");
                        List<Player> playerList = new ArrayList<>();
                        for (int i = 0; i < playersArray.length(); i++) {
                            JSONObject playerObj = playersArray.getJSONObject(i);
                            Player player = new Player(playerObj.getString("userId"), playerObj.getString("username"));
                            playerList.add(player);
                        }
                        players.postValue(playerList);
                        isRoomPlayersLoaded = true;
                        Log.d("TEST", "Initial players fetched for room " + roomNumber + ": " + playerList.toString());
                    }
                } catch (JSONException e) {
                    errorMessage.postValue("خطا در دریافت لیست بازیکن‌ها");
                    Log.e("TEST", "Error parsing initial players data: " + e.getMessage());
                }
            }

            @Override
            public void onRoomPlayersResponseError(Throwable t) {
                errorMessage.postValue(t.getMessage());
                Log.e("TEST", "Error fetching initial room players: " + t.getMessage());
            }
        });

        SocketManager.listenForRoomPlayersUpdates(new SocketManager.RoomPlayersUpdateListener() {
            @Override
            public void onRoomPlayersUpdate(JSONObject playersData) {
                try {
                    String receivedRoomNumber = playersData.getString("roomNumber");
                    if (receivedRoomNumber.equals(roomNumber)) {
                        JSONArray playersArray = playersData.getJSONArray("players");
                        List<Player> playerList = new ArrayList<>();
                        for (int i = 0; i < playersArray.length(); i++) {
                            JSONObject playerObj = playersArray.getJSONObject(i);
                            Player player = new Player(playerObj.getString("userId"), playerObj.getString("username"));
                            playerList.add(player);
                        }
                        players.postValue(playerList);
                        Log.d("TEST", "Players updated for room " + roomNumber + ": " + playerList.toString());
                    }
                } catch (JSONException e) {
                    errorMessage.postValue("خطا در پردازش لیست بازیکن‌ها");
                    Log.e("TEST", "Error parsing players data: " + e.getMessage());
                }
            }

            @Override
            public void onRoomPlayersError(Throwable t) {
                errorMessage.postValue(t.getMessage());
                Log.e("TEST", "Error in room players update: " + t.getMessage());
            }
        });

        SocketManager.listenForRoomDeleted(new SocketManager.RoomDeletedListener() {
            @Override
            public void onRoomDeleted(JSONObject data) {
                try {
                    String deletedRoomNumber = data.getString("roomNumber");
                    if (deletedRoomNumber.equals(roomNumber)) {
                        errorMessage.postValue("میزبان روم را بست");
                    }
                } catch (JSONException e) {
                    errorMessage.postValue("خطا در پردازش حذف روم");
                }
            }

            @Override
            public void onRoomDeletedError(Throwable t) {
                errorMessage.postValue(t.getMessage());
            }
        });
    }

    public void leaveRoom() {
        if (!SocketManager.isConnect) {
            errorMessage.postValue("اتصال برقرار نیست");
            return;
        }
        Map<String, Object> dataMap = new HashMap<>();
        dataMap.put("roomNumber", roomNumber.getValue());
        dataMap.put("userId", userId);
        dataMap.put("event", "leave_room");

        try {
            JSONObject data = new JSONObject(dataMap);
            SocketRequest request = new SocketRequest(null, data, new SocketManager.Response() {
                @Override
                public void onResponse(JSONObject object, Boolean isError) throws JSONException {
                    if (object.getBoolean("success")) {
                        errorMessage.postValue("با موفقیت از روم خارج شدید");
                    } else {
                        errorMessage.postValue(object.getString("message"));
                    }
                }

                @Override
                public void onError(Throwable t) {
                    errorMessage.postValue(t != null ? t.getMessage() : "خطا در ارتباط");
                }
            });
            SocketManager.sendRequest(getApplication(), request);
        } catch (JSONException e) {
            errorMessage.postValue("خطا در پردازش داده‌ها");
        }
    }

    public void gameLoading(String roomNumber) {
        if (!SocketManager.isConnect) {
            errorMessage.postValue("اتصال برقرار نیست");
            return;
        }
        Map<String, Object> dataMap = new HashMap<>();
        dataMap.put("roomNumber", roomNumber);
        dataMap.put("event", "game_loading");

        try {
            JSONObject data = new JSONObject(dataMap);
            SocketRequest request = new SocketRequest(null, data, new SocketManager.Response() {
                @Override
                public void onResponse(JSONObject object, Boolean isError) throws JSONException {
                    if (object.getBoolean("success")) {
                        Log.d("TEST", "Game loading request successful for room " + roomNumber);
                    } else {
                        errorMessage.postValue(object.getString("message"));
                    }
                }

                @Override
                public void onError(Throwable t) {
                    errorMessage.postValue(t != null ? t.getMessage() : "خطا در ارتباط");
                }
            });
            SocketManager.sendGameLoadingRequest(getApplication(), request, new SocketManager.Response() {
                @Override
                public void onResponse(JSONObject object, Boolean isError) throws JSONException {
                    if (object.getBoolean("success")) {
                        Log.d("TEST", "Game loading request successful for room " + roomNumber);
                    } else {
                        errorMessage.postValue(object.getString("message"));
                    }
                }

                @Override
                public void onError(Throwable t) {
                    errorMessage.postValue(t != null ? t.getMessage() : "خطا در ارتباط");
                }
            });
        } catch (JSONException e) {
            errorMessage.postValue("خطا در پردازش داده‌ها");
        }
    }

    private void setupGameLoadingListener() {
        SocketManager.listenForGameLoading(new SocketManager.GameLoadingListener() {
            @Override
            public void onGameLoading(JSONObject data) {
                try {
                    String receivedRoomNumber = data.getString("roomNumber");
                    if (receivedRoomNumber.equals(roomNumber.getValue())) {
                        errorMessage.postValue("در حال بارگذاری بازی...");
                    }
                } catch (JSONException e) {
                    errorMessage.postValue("خطا در پردازش پیام بارگذاری بازی");
                }
            }

            @Override
            public void onGameLoadingError(Throwable t) {
                errorMessage.postValue(t.getMessage());
            }
        });
    }

    public void resetLoadFlags() {
        isRoomDetailsLoaded = false;
        isRoomPlayersLoaded = false;
    }
}