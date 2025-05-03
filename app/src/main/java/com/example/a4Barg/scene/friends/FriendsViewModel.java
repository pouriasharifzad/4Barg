package com.example.a4Barg.scene.friends;

import android.app.Application;
import android.util.Log;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;

import com.example.a4Barg.model.FriendRequest;
import com.example.a4Barg.model.Player;
import com.example.a4Barg.model.SocketRequest;
import com.example.a4Barg.networking.SocketManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FriendsViewModel extends AndroidViewModel {

    private String userId;
    private MutableLiveData<List<Player>> searchResults = new MutableLiveData<>(new ArrayList<>());
    private MutableLiveData<List<FriendRequest>> friendRequests = new MutableLiveData<>(new ArrayList<>());
    private MutableLiveData<List<Player>> friends = new MutableLiveData<>(new ArrayList<>());
    private MutableLiveData<String> errorMessage = new MutableLiveData<>();

    public FriendsViewModel(Application application) {
        super(application);
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public MutableLiveData<List<Player>> getSearchResults() {
        return searchResults;
    }

    public MutableLiveData<List<FriendRequest>> getFriendRequests() {
        return friendRequests;
    }

    public MutableLiveData<List<Player>> getFriends() {
        return friends;
    }

    public MutableLiveData<String> getErrorMessage() {
        return errorMessage;
    }

    public void searchUsers(String query) {
        Map<String, Object> dataMap = new HashMap<>();
        dataMap.put("query", query);
        dataMap.put("event", "search_users");

        try {
            JSONObject data = new JSONObject(dataMap);
            Log.d("FriendsViewModel", "Sending searchUsers request: " + data.toString());
            SocketRequest request = new SocketRequest(null, data, new SocketManager.Response() {
                @Override
                public void onResponse(JSONObject object, Boolean isError) throws JSONException {
                    Log.d("FriendsViewModel", "Received searchUsers response: " + object.toString());
                    if (object.getBoolean("success")) {
                        JSONArray usersArray = object.getJSONArray("users");
                        List<Player> users = new ArrayList<>();
                        for (int i = 0; i < usersArray.length(); i++) {
                            JSONObject userObj = usersArray.getJSONObject(i);
                            Player user = new Player(
                                    userObj.getString("_id"),
                                    userObj.getString("username"),
                                    userObj.getInt("experience"),
                                    userObj.optString("avatar", null)
                            );
                            users.add(user);
                        }
                        searchResults.postValue(users);
                    } else {
                        errorMessage.postValue(object.getString("message"));
                    }
                }

                @Override
                public void onError(Throwable t) {
                    Log.e("FriendsViewModel", "Error in searchUsers: " + (t != null ? t.getMessage() : "Unknown error"));
                    errorMessage.postValue(t != null ? t.getMessage() : "خطا در ارتباط");
                }
            });
            SocketManager.sendRequest(getApplication(), request);
        } catch (JSONException e) {
            Log.e("FriendsViewModel", "JSON Exception in searchUsers: " + e.getMessage());
            errorMessage.postValue("خطا در پردازش داده‌ها");
        }
    }

    public void sendFriendRequest(String toUserId) {
        Map<String, Object> dataMap = new HashMap<>();
        dataMap.put("toUserId", toUserId);
        dataMap.put("event", "send_friend_request");

        try {
            JSONObject data = new JSONObject(dataMap);
            Log.d("FriendsViewModel", "Sending sendFriendRequest request: " + data.toString());
            SocketRequest request = new SocketRequest(null, data, new SocketManager.Response() {
                @Override
                public void onResponse(JSONObject object, Boolean isError) throws JSONException {
                    Log.d("FriendsViewModel", "Received sendFriendRequest response: " + object.toString());
                    if (object.getBoolean("success")) {
                        errorMessage.postValue("درخواست دوستی ارسال شد");
                    } else {
                        errorMessage.postValue(object.getString("message"));
                    }
                }

                @Override
                public void onError(Throwable t) {
                    Log.e("FriendsViewModel", "Error in sendFriendRequest: " + (t != null ? t.getMessage() : "Unknown error"));
                    errorMessage.postValue(t != null ? t.getMessage() : "خطا در ارتباط");
                }
            });
            SocketManager.sendRequest(getApplication(), request);
        } catch (JSONException e) {
            Log.e("FriendsViewModel", "JSON Exception in sendFriendRequest: " + e.getMessage());
            errorMessage.postValue("خطا در پردازش داده‌ها");
        }
    }

    public void loadFriendRequests() {
        Map<String, Object> dataMap = new HashMap<>();
        dataMap.put("event", "get_friend_requests");

        try {
            JSONObject data = new JSONObject(dataMap);
            Log.d("FriendsViewModel", "Sending loadFriendRequests request: " + data.toString());
            SocketRequest request = new SocketRequest(null, data, new SocketManager.Response() {
                @Override
                public void onResponse(JSONObject object, Boolean isError) throws JSONException {
                    Log.d("FriendsViewModel", "Received loadFriendRequests response: " + object.toString());
                    if (object.getBoolean("success")) {
                        JSONArray requestsArray = object.getJSONArray("requests");
                        List<FriendRequest> requests = new ArrayList<>();
                        for (int i = 0; i < requestsArray.length(); i++) {
                            JSONObject requestObj = requestsArray.getJSONObject(i);
                            FriendRequest request = new FriendRequest(
                                    requestObj.getString("_id"),
                                    requestObj.getJSONObject("from").getString("_id"),
                                    requestObj.getJSONObject("from").getString("username"),
                                    requestObj.getJSONObject("from").optString("avatar", null)
                            );
                            requests.add(request);
                        }
                        friendRequests.postValue(requests);
                    } else {
                        errorMessage.postValue(object.getString("message"));
                    }
                }

                @Override
                public void onError(Throwable t) {
                    Log.e("FriendsViewModel", "Error in loadFriendRequests: " + (t != null ? t.getMessage() : "Unknown error"));
                    errorMessage.postValue(t != null ? t.getMessage() : "خطا در ارتباط");
                }
            });
            SocketManager.sendRequest(getApplication(), request);
        } catch (JSONException e) {
            Log.e("FriendsViewModel", "JSON Exception in loadFriendRequests: " + e.getMessage());
            errorMessage.postValue("خطا در پردازش داده‌ها");
        }
    }

    public void acceptFriendRequest(String requestId) {
        Map<String, Object> dataMap = new HashMap<>();
        dataMap.put("requestId", requestId);
        dataMap.put("event", "accept_friend_request");

        try {
            JSONObject data = new JSONObject(dataMap);
            Log.d("FriendsViewModel", "Sending acceptFriendRequest request: " + data.toString());
            SocketRequest request = new SocketRequest(null, data, new SocketManager.Response() {
                @Override
                public void onResponse(JSONObject object, Boolean isError) throws JSONException {
                    Log.d("FriendsViewModel", "Received acceptFriendRequest response: " + object.toString());
                    if (object.getBoolean("success")) {
                        errorMessage.postValue("درخواست دوستی پذیرفته شد");
                        loadFriendRequests();
                        loadFriends();
                    } else {
                        errorMessage.postValue(object.getString("message"));
                    }
                }

                @Override
                public void onError(Throwable t) {
                    Log.e("FriendsViewModel", "Error in acceptFriendRequest: " + (t != null ? t.getMessage() : "Unknown error"));
                    errorMessage.postValue(t != null ? t.getMessage() : "خطا در ارتباط");
                }
            });
            SocketManager.sendRequest(getApplication(), request);
        } catch (JSONException e) {
            Log.e("FriendsViewModel", "JSON Exception in acceptFriendRequest: " + e.getMessage());
            errorMessage.postValue("خطا در پردازش داده‌ها");
        }
    }

    public void rejectFriendRequest(String requestId) {
        Map<String, Object> dataMap = new HashMap<>();
        dataMap.put("requestId", requestId);
        dataMap.put("event", "reject_friend_request");

        try {
            JSONObject data = new JSONObject(dataMap);
            Log.d("FriendsViewModel", "Sending rejectFriendRequest request: " + data.toString());
            SocketRequest request = new SocketRequest(null, data, new SocketManager.Response() {
                @Override
                public void onResponse(JSONObject object, Boolean isError) throws JSONException {
                    Log.d("FriendsViewModel", "Received rejectFriendRequest response: " + object.toString());
                    if (object.getBoolean("success")) {
                        errorMessage.postValue("درخواست دوستی رد شد");
                        loadFriendRequests();
                    } else {
                        errorMessage.postValue(object.getString("message"));
                    }
                }

                @Override
                public void onError(Throwable t) {
                    Log.e("FriendsViewModel", "Error in rejectFriendRequest: " + (t != null ? t.getMessage() : "Unknown error"));
                    errorMessage.postValue(t != null ? t.getMessage() : "خطا در ارتباط");
                }
            });
            SocketManager.sendRequest(getApplication(), request);
        } catch (JSONException e) {
            Log.e("FriendsViewModel", "JSON Exception in rejectFriendRequest: " + e.getMessage());
            errorMessage.postValue("خطا در پردازش داده‌ها");
        }
    }

    public void loadFriends() {
        Map<String, Object> dataMap = new HashMap<>();
        dataMap.put("event", "get_friends");

        try {
            JSONObject data = new JSONObject(dataMap);
            Log.d("FriendsViewModel", "Sending loadFriends request: " + data.toString());
            SocketRequest request = new SocketRequest(null, data, new SocketManager.Response() {
                @Override
                public void onResponse(JSONObject object, Boolean isError) throws JSONException {
                    Log.d("FriendsViewModel", "Received loadFriends response: " + object.toString());
                    if (object.getBoolean("success")) {
                        JSONArray friendsArray = object.getJSONArray("friends");
                        List<Player> friendList = new ArrayList<>();
                        for (int i = 0; i < friendsArray.length(); i++) {
                            JSONObject friendObj = friendsArray.getJSONObject(i);
                            Player friend = new Player(
                                    friendObj.getString("_id"),
                                    friendObj.getString("username"),
                                    friendObj.getInt("experience"),
                                    friendObj.optString("avatar", null)
                            );
                            friendList.add(friend);
                        }
                        friends.postValue(friendList);
                    } else {
                        errorMessage.postValue(object.getString("message"));
                    }
                }

                @Override
                public void onError(Throwable t) {
                    Log.e("FriendsViewModel", "Error in loadFriends: " + (t != null ? t.getMessage() : "Unknown error"));
                    errorMessage.postValue(t != null ? t.getMessage() : "خطا در ارتباط");
                }
            });
            SocketManager.sendRequest(getApplication(), request);
        } catch (JSONException e) {
            Log.e("FriendsViewModel", "JSON Exception in loadFriends: " + e.getMessage());
            errorMessage.postValue("خطا در پردازش داده‌ها");
        }
    }
}