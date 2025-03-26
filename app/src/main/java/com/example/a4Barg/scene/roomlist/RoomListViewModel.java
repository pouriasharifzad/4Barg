package com.example.a4Barg.scene.roomlist;

import android.app.Application;
import android.content.Intent;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;

import com.example.a4Barg.model.Room;
import com.example.a4Barg.model.SocketRequest;
import com.example.a4Barg.networking.SocketManager;
import com.example.a4Barg.scene.room.RoomActivity;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RoomListViewModel extends AndroidViewModel {

    private MutableLiveData<List<Room>> roomList = new MutableLiveData<>(new ArrayList<>());
    private MutableLiveData<String> errorMessage = new MutableLiveData<>();
    private String userId;

    public RoomListViewModel(Application application) {
        super(application);
        setupRoomListListener();
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public MutableLiveData<List<Room>> getRoomList() {
        return roomList;
    }

    public MutableLiveData<String> getErrorMessage() {
        return errorMessage;
    }

    public void requestRoomList() {
        Map<String, Object> dataMap = new HashMap<>();
        dataMap.put("userId", userId);
        dataMap.put("event", "get_room_list");

        try {
            JSONObject data = new JSONObject(dataMap);
            SocketRequest request = new SocketRequest(null, data, new SocketManager.Response() {
                @Override
                public void onResponse(JSONObject object, Boolean isError) throws JSONException {
                    if (object.getBoolean("success")) {
                        JSONArray roomsArray = object.getJSONArray("rooms");
                        List<Room> rooms = new ArrayList<>();
                        for (int i = 0; i < roomsArray.length(); i++) {
                            JSONObject roomObj = roomsArray.getJSONObject(i);
                            Room room = new Room(
                                    roomObj.getString("roomNumber"),
                                    roomObj.getInt("minExperience"),
                                    roomObj.getInt("minCoins"),
                                    roomObj.getInt("maxPlayers"),
                                    roomObj.getInt("currentPlayers")
                            );
                            rooms.add(room);
                        }
                        roomList.postValue(rooms);
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

    private void loadRoomList() {
        // این متد فقط برای listener استفاده می‌شه و تغییر نمی‌کنه
        SocketManager.listenForRoomListUpdates(new SocketManager.RoomListUpdateListener() {
            @Override
            public void onRoomListUpdate(JSONObject roomListData) {
                try {
                    JSONArray roomsArray = roomListData.getJSONArray("rooms");
                    List<Room> rooms = new ArrayList<>();
                    for (int i = 0; i < roomsArray.length(); i++) {
                        JSONObject roomObj = roomsArray.getJSONObject(i);
                        Room room = new Room(
                                roomObj.getString("roomNumber"),
                                roomObj.getInt("minExperience"),
                                roomObj.getInt("minCoins"),
                                roomObj.getInt("maxPlayers"),
                                roomObj.getInt("currentPlayers")
                        );
                        rooms.add(room);
                    }
                    roomList.postValue(rooms);
                } catch (JSONException e) {
                    errorMessage.postValue("خطا در پردازش لیست روم‌ها");
                }
            }

            @Override
            public void onRoomListError(Throwable t) {
                errorMessage.postValue(t.getMessage());
            }
        });
    }

    private void setupRoomListListener() {
        loadRoomList();
    }

    public void joinRoom(String roomNumber) {
        Map<String, Object> dataMap = new HashMap<>();
        dataMap.put("roomNumber", roomNumber);
        dataMap.put("userId", userId);
        dataMap.put("event", "join_room");

        try {
            JSONObject data = new JSONObject(dataMap);
            SocketRequest request = new SocketRequest(null, data, new SocketManager.Response() {
                @Override
                public void onResponse(JSONObject object, Boolean isError) throws JSONException {
                    if (object.getBoolean("success")) {
                        Intent intent = new Intent(getApplication(), RoomActivity.class);
                        intent.putExtra("userId", userId);
                        intent.putExtra("roomNumber", roomNumber);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        getApplication().startActivity(intent);
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
}