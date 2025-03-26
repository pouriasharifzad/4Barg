package com.example.a4Barg.scene.lobby;

import android.app.Application;
import android.content.Intent;
import android.util.Log;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;

import com.example.a4Barg.model.SocketRequest;
import com.example.a4Barg.networking.SocketManager;
import com.example.a4Barg.scene.room.RoomActivity;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

public class LobbyViewModel extends AndroidViewModel {

    private String userId;
    private MutableLiveData<String> message = new MutableLiveData<>();
    private MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);

    public LobbyViewModel(Application application) {
        super(application);
        // گوش دادن به آپدیت‌های لیست روم‌ها
        SocketManager.listenForRoomListUpdates(new SocketManager.RoomListUpdateListener() {
            @Override
            public void onRoomListUpdate(JSONObject roomListData) {
                Log.d("TEST", "Room list updated: " + roomListData.toString());
                // اینجا می‌تونی لیست روم‌ها رو توی یه LiveData ذخیره کنی و به UI اطلاع بدی
                message.postValue("لیست روم‌ها آپدیت شد: " + roomListData.toString());
            }

            @Override
            public void onRoomListError(Throwable t) {
                Log.d("TEST", "Room list update error: " + t.getMessage());
                message.postValue("خطا در آپدیت لیست روم‌ها: " + t.getMessage());
            }
        });
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public MutableLiveData<String> getMessage() {
        return message;
    }

    public MutableLiveData<Boolean> getIsLoading() {
        return isLoading;
    }

    public void createRoom(int minExperience, int minCoins, int maxPlayers) {
        isLoading.setValue(true);
        Map<String, Object> dataMap = new HashMap<>();
        dataMap.put("minExperience", minExperience);
        dataMap.put("minCoins", minCoins);
        dataMap.put("maxPlayers", maxPlayers);
        dataMap.put("userId", userId);
        dataMap.put("event", "create_room");

        try {
            JSONObject data = new JSONObject(dataMap);
            SocketRequest request = new SocketRequest(null, data, new SocketManager.Response() {
                @Override
                public void onResponse(JSONObject object, Boolean isError) throws JSONException {
                    isLoading.postValue(false);
                    if (object.getBoolean("success")) {
                        message.postValue("روم شماره: " + object.getString("roomNumber"));
                        // انتقال به RoomActivity و پاس دادن مشخصات روم
                        Intent intent = new Intent(getApplication(), RoomActivity.class);
                        intent.putExtra("userId", userId);
                        intent.putExtra("roomNumber", object.getString("roomNumber"));
                        // فرض می‌کنیم این فیلدها توی پاسخ سرور وجود دارن
                        intent.putExtra("minExperience", object.optInt("minExperience", minExperience));
                        intent.putExtra("minCoins", object.optInt("minCoins", minCoins));
                        intent.putExtra("maxPlayers", object.optInt("maxPlayers", maxPlayers));
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        getApplication().startActivity(intent);
                    } else {
                        message.postValue(object.getString("message"));
                    }
                }

                @Override
                public void onError(Throwable t) {
                    isLoading.postValue(false);
                    message.postValue(t != null ? t.getMessage() : "خطا در ارتباط");
                }
            });
            SocketManager.sendRequest(getApplication(), request);
        } catch (JSONException e) {
            isLoading.postValue(false);
            message.postValue("خطا در پردازش داده‌ها");
        }
    }


}