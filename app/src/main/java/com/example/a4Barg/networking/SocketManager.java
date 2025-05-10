package com.example.a4Barg.networking;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.example.a4Barg.App;
import com.example.a4Barg.model.SocketRequest;
import com.example.a4Barg.utils.ConsValue;
import com.example.a4Barg.utils.RandomInteger;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import io.socket.client.Socket;
import io.socket.emitter.Emitter;

public class SocketManager {

    public static Socket socket = App.shared.getSocket();
    public static Boolean isConnect = false;
    public static Boolean isRequestCompleted = false;
    static Context context;

    private static final List<PlayerCardsListener> playerCardsListeners = new ArrayList<>();
    private static final List<GamePlayersInfoListener> gamePlayersInfoListeners = new ArrayList<>();
    private static final List<TurnUpdateListener> turnUpdateListeners = new ArrayList<>();
    private static final List<UserStatusUpdateListener> userStatusUpdateListeners = new ArrayList<>();
    private static final Map<String, List<CustomListener>> customListeners = new HashMap<>();
    private static final Set<String> pendingRequests = new HashSet<>();

    public static void initialize(Context context, String userId) {
        SocketManager.context = context;
        socket = App.shared.getSocket();
        Log.d("TEST", "Socket initialization attempted - socket.connected(): " + socket.connected());
        socket.connect();
        socket.emit("set_user_id", userId);
        socket.on(Socket.EVENT_CONNECT, args -> {
            Log.d("TEST", "Socket connected on init");
            isConnect = true;
            initializeGlobalListeners();
            addCustomListener("avatar_status", data -> {
                try {
                    Log.d("TEST", "Received avatar_status: " + data.toString());
                    Handler mainHandler = new Handler(Looper.getMainLooper());
                    mainHandler.post(() -> {
                    });
                } catch (Exception e) {
                    Log.e("TEST", "Error in avatar_status listener: " + e.getMessage());
                }
            });
            if (context instanceof Activity) {
                requestMissedMessages((Activity) context, userId);
            } else {
                Log.e("SocketManager", "Context is not an Activity, cannot request missed messages");
            }
        });
        socket.on(Socket.EVENT_CONNECT_ERROR, args -> {
            Log.e("TEST", "Socket connection error: " + (args[0] != null ? args[0].toString() : "unknown error"));
            isConnect = false;
        });
        socket.on(Socket.EVENT_DISCONNECT, args -> {
            Log.d("TEST", "Socket disconnected: " + (args.length > 0 ? args[0].toString() : "unknown reason"));
            isConnect = false;
        });
    }

    public static void addPlayerCardsListener(PlayerCardsListener listener) {
        playerCardsListeners.add(listener);
    }

    public static void removePlayerCardsListener(PlayerCardsListener listener) {
        playerCardsListeners.remove(listener);
    }

    public static void addGamePlayersInfoListener(GamePlayersInfoListener listener) {
        gamePlayersInfoListeners.add(listener);
    }

    public static void addTurnUpdateListener(TurnUpdateListener listener) {
        turnUpdateListeners.add(listener);
    }

    public static void addUserStatusUpdateListener(UserStatusUpdateListener listener) {
        userStatusUpdateListeners.add(listener);
    }

    public static void removeUserStatusUpdateListener(UserStatusUpdateListener listener) {
        userStatusUpdateListeners.remove(listener);
    }

    public static void addCustomListener(String eventName, CustomListener listener) {
        customListeners.computeIfAbsent(eventName, k -> new ArrayList<>()).add(listener);
        socket.on(eventName, args -> {
            try {
                JSONObject data = (JSONObject) args[0];
                Log.d("TEST", "Received custom event: " + eventName + ", Data: " + data.toString());
                Handler mainHandler = new Handler(Looper.getMainLooper());
                mainHandler.post(() -> {
                    List<CustomListener> listeners = customListeners.get(eventName);
                    if (listeners != null) {
                        for (CustomListener l : listeners) {
                            try {
                                l.onEvent(data);
                            } catch (Exception e) {
                                Log.e("SocketManager", "Error in custom listener for " + eventName + ": " + e.getMessage());
                            }
                        }
                    } else {
                        Log.w("SocketManager", "No listeners registered for event: " + eventName);
                    }
                });
            } catch (Exception e) {
                Log.e("SocketManager", "Error parsing custom event " + eventName + ": " + e.getMessage());
            }
        });
    }

    public static void initializeGlobalListeners() {
        socket.on("player_cards", args -> {
            try {
                JSONObject data = (JSONObject) args[0];
                Log.d("TEST", "Received player_cards: " + data.toString());
                Handler mainHandler = new Handler(Looper.getMainLooper());
                mainHandler.post(() -> {
                    for (PlayerCardsListener listener : playerCardsListeners) {
                        listener.onPlayerCards(data);
                    }
                });
            } catch (Exception e) {
                Handler mainHandler = new Handler(Looper.getMainLooper());
                mainHandler.post(() -> {
                    for (PlayerCardsListener listener : playerCardsListeners) {
                        listener.onPlayerCardsError(e);
                    }
                });
            }
        });

        socket.on("get_game_players_info_response", args -> {
            try {
                JSONObject data = (JSONObject) args[0];
                Log.d("TEST", "Received get_game_players_info_response: " + data.toString());
                Handler mainHandler = new Handler(Looper.getMainLooper());
                mainHandler.post(() -> {
                    for (GamePlayersInfoListener listener : gamePlayersInfoListeners) {
                        listener.onGamePlayersInfo(data);
                    }
                });
            } catch (Exception e) {
                Handler mainHandler = new Handler(Looper.getMainLooper());
                mainHandler.post(() -> {
                    for (GamePlayersInfoListener listener : gamePlayersInfoListeners) {
                        listener.onGamePlayersInfoError(e);
                    }
                });
            }
        });

        socket.on("turn_update", args -> {
            try {
                JSONObject data = (JSONObject) args[0];
                Log.d("TEST", "Received turn_update: " + data.toString());
                Handler mainHandler = new Handler(Looper.getMainLooper());
                mainHandler.post(() -> {
                    for (TurnUpdateListener listener : turnUpdateListeners) {
                        listener.onTurnUpdate(data);
                    }
                });
            } catch (Exception e) {
                Handler mainHandler = new Handler(Looper.getMainLooper());
                mainHandler.post(() -> {
                    for (TurnUpdateListener listener : turnUpdateListeners) {
                        listener.onTurnUpdateError(e);
                    }
                });
            }
        });

        socket.on("user_status_update", args -> {
            try {
                JSONObject data = (JSONObject) args[0];
                String userId = data.getString("userId");
                String status = data.getString("status");
                Log.d("TEST", "Received user_status_update: userId=" + userId + ", status=" + status);
                Handler mainHandler = new Handler(Looper.getMainLooper());
                mainHandler.post(() -> {
                    for (UserStatusUpdateListener listener : userStatusUpdateListeners) {
                        listener.onUserStatusUpdate(userId, status);
                    }
                });
            } catch (Exception e) {
                Log.e("SocketManager", "Error in user_status_update listener: " + e.getMessage());
            }
        });

        socket.onAnyIncoming(new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                String eventName = args[0].toString();
                String data = args.length > 1 ? args[1].toString() : "No data";
                Log.d("TEST", "[Client] Received event: " + eventName + ", Data: " + data);
            }
        });
    }

    public static void testSocketConnection(Context context, String userId) {
        reconnectIfNeeded();
        if (!socket.connected() || !isConnect) {
            Log.e("TEST", "Socket not connected during test");
            return;
        }
        JSONObject testData = new JSONObject();
        try {
            testData.put("event", "test_connection");
            testData.put("userId", userId);
            testData.put("id", String.valueOf(RandomInteger.getRandomId()));
            SocketRequest request = new SocketRequest(null, testData, new Response() {
                @Override
                public void onResponse(JSONObject object, Boolean isError) throws JSONException {
                    Log.d("TEST", "Test connection response: " + object.toString());
                }

                @Override
                public void onError(Throwable t) {
                    Log.e("TEST", "Test connection error: " + t.getMessage());
                }
            });
            sendRequest(context, request);
        } catch (JSONException e) {
            Log.e("TEST", "Error preparing test connection request: " + e.getMessage());
        }
    }

    public static void requestMissedMessages(Activity activity, String userId) {
        if (!socket.connected() || !isConnect) {
            Log.e("SocketManager", "Cannot request missed messages: Socket not connected");
            return;
        }
        try {
            JSONObject data = new JSONObject();
            data.put("event", "get_missed_messages");
            data.put("userId", userId);
            SocketRequest request = new SocketRequest(activity, data, new Response() {
                @Override
                public void onResponse(JSONObject object, Boolean isError) throws JSONException {
                    if (!isError && object.getBoolean("success")) {
                        Log.d("SocketManager", "Missed messages received: " + object.toString());
                    } else {
                        Log.e("SocketManager", "Failed to load missed messages: " + object.getString("message"));
                    }
                }

                @Override
                public void onError(Throwable t) {
                    Log.e("SocketManager", "Error loading missed messages: " + t.getMessage());
                }
            });
            sendRequest(activity, request);
        } catch (JSONException e) {
            Log.e("SocketManager", "Error preparing missed messages request: " + e.getMessage());
        }
    }

    public interface Response {
        void onResponse(JSONObject object, Boolean isError) throws JSONException;
        void onError(Throwable t);
    }

    public interface RoomListUpdateListener {
        void onRoomListUpdate(JSONObject roomListData);
        void onRoomListError(Throwable t);
    }

    public interface RoomPlayersUpdateListener {
        void onRoomPlayersUpdate(JSONObject playersData);
        void onRoomPlayersError(Throwable t);
    }

    public interface RoomPlayersResponseListener {
        void onRoomPlayersResponse(JSONObject playersData);
        void onRoomPlayersResponseError(Throwable t);
    }

    public interface RoomDeletedListener {
        void onRoomDeleted(JSONObject data);
        void onRoomDeletedError(Throwable t);
    }

    public interface GamePlayersInfoListener {
        void onGamePlayersInfo(JSONObject data);
        void onGamePlayersInfoError(Throwable t);
    }

    public interface GameStartListener {
        void onGameStart(JSONObject data);
        void onGameStartError(Throwable t);
    }

    public interface GameLoadingListener {
        void onGameLoading(JSONObject data);
        void onGameLoadingError(Throwable t);
    }

    public interface GameStateUpdateListener {
        void onGameStateUpdate(JSONObject data);
        void onGameStateUpdateError(Throwable t);
    }

    public interface PlayerCardsListener {
        void onPlayerCards(JSONObject data);
        void onPlayerCardsError(Throwable t);
    }

    public interface ChooseSuitListener {
        void onChooseSuit(JSONObject data);
        void onChooseSuitError(Throwable t);
    }

    public interface GameEndedListener {
        void onGameEnded(JSONObject data);
        void onGameEndedError(Throwable t);
    }

    public interface TurnUpdateListener {
        void onTurnUpdate(JSONObject data);
        void onTurnUpdateError(Throwable t);
    }

    public interface CustomListener {
        void onEvent(JSONObject data);
    }

    public interface UserStatusUpdateListener {
        void onUserStatusUpdate(String userId, String status);
    }

    public interface UploadCallback {
        void onSuccess();
        void onError(String error);
    }

    public static void addRequest(SocketRequest request) {
        ConsValue.socketRequestList.add(request);
        Log.d("TEST", "Request added to queue: " + request.getJsonObject().toString());
    }

    public static void sendRequest(Context context, SocketRequest request) throws JSONException {
        SocketManager.context = context;
        reconnectIfNeeded();
        if (!socket.connected() || !isConnect) {
            request.getResponse().onError(new IllegalArgumentException("اتصال برقرار نشد"));
            return;
        }

        SharedPreferences prefs = context.getSharedPreferences("auth", Context.MODE_PRIVATE);
        String token = prefs.getString("token", null);
        String event = request.getJsonObject().getString("event");

        String requestId = request.getJsonObject().optString("id", UUID.randomUUID().toString());
        String requestKey = event + "_" + requestId;

        synchronized (pendingRequests) {
            if (pendingRequests.contains(requestKey)) {
                Log.d("TEST", "Duplicate request detected, skipping: " + requestKey);
                request.getResponse().onError(new IllegalArgumentException("درخواست تکراری: " + event));
                return;
            }
            pendingRequests.add(requestKey);
        }

        if (token == null && !event.equals("login") && !event.equals("register")) {
            synchronized (pendingRequests) {
                pendingRequests.remove(requestKey);
            }
            request.getResponse().onError(new IllegalArgumentException("توکن احراز هویت موجود نیست"));
            return;
        }

        final long TIMEOUT_MS = 15000; // افزایش تایم‌اوت به 15 ثانیه برای اطمینان بیشتر
        final Handler timeoutHandler = new Handler(Looper.getMainLooper());
        final Runnable timeoutRunnable = () -> {
            synchronized (pendingRequests) {
                pendingRequests.remove(requestKey);
            }
            request.getResponse().onError(new IllegalArgumentException("درخواست منقضی شد: پاسخی از سرور دریافت نشد"));
            socket.off("message");
            socket.off("login_response");
            socket.off("register_response");
            socket.off("create_room_response");
            socket.off("join_room_response");
            socket.off("leave_room_response");
            socket.off("get_room_list_response");
            socket.off("get_room_details_response");
            socket.off("get_room_players_response");
            socket.off("game_loading_response");
            socket.off("play_card_response");
        };
        timeoutHandler.postDelayed(timeoutRunnable, TIMEOUT_MS);

        socket.once(Socket.EVENT_CONNECT_ERROR, args -> {
            timeoutHandler.removeCallbacks(timeoutRunnable);
            synchronized (pendingRequests) {
                pendingRequests.remove(requestKey);
            }
            request.getResponse().onError(new IllegalArgumentException("خطای اتصال: " + (args[0] != null ? args[0].toString() : "unknown error")));
        });

        if (ConsValue.isRegistered) {
            String id = String.valueOf(RandomInteger.getRandomId());
            request.getJsonObject().put("id", id);
            isRequestCompleted = false;
            JSONObject requestData = new JSONObject();
            requestData.put("requestId", id);
            requestData.put("data", request.getJsonObject());
            if (!event.equals("login") && !event.equals("register")) {
                requestData.put("token", token);
            }
            emitRequest(event, requestData, request, timeoutHandler, timeoutRunnable, requestKey);
        } else {
            String id = String.valueOf(RandomInteger.getRandomId());
            request.getJsonObject().put("id", id);
            JSONObject requestData = new JSONObject();
            requestData.put("requestId", id);
            requestData.put("data", request.getJsonObject());
            if (token != null) {
                requestData.put("token", token);
            }
            emitRequest(event, requestData, request, timeoutHandler, timeoutRunnable, requestKey);
        }
    }

    public static void sendGameLoadingRequest(Context context, SocketRequest request, Response response) throws JSONException {
        SocketManager.context = context;
        reconnectIfNeeded();
        if (!socket.connected() || !isConnect) {
            response.onError(new IllegalArgumentException("اتصال برقرار نشد"));
            return;
        }

        SharedPreferences prefs = context.getSharedPreferences("auth", Context.MODE_PRIVATE);
        String token = prefs.getString("token", null);
        String event = request.getJsonObject().getString("event");

        if (token == null) {
            response.onError(new IllegalArgumentException("توکن احراز هویت موجود نیست"));
            return;
        }

        String requestId = request.getJsonObject().optString("id", UUID.randomUUID().toString());
        String requestKey = event + "_" + requestId;

        synchronized (pendingRequests) {
            if (pendingRequests.contains(requestKey)) {
                Log.d("TEST", "Duplicate game loading request detected, skipping: " + requestKey);
                response.onError(new IllegalArgumentException("درخواست تکراری: " + event));
                return;
            }
            pendingRequests.add(requestKey);
        }

        verifyToken(context, token, new Response() {
            @Override
            public void onResponse(JSONObject object, Boolean isError) throws JSONException {
                if (isError || !object.getBoolean("success")) {
                    refreshToken(context, new JSONObject(), new Response() {
                        @Override
                        public void onResponse(JSONObject refreshResponse, Boolean refreshError) throws JSONException {
                            if (!refreshError && refreshResponse.getBoolean("success")) {
                                String newToken = refreshResponse.getString("token");
                                SharedPreferences.Editor editor = prefs.edit();
                                editor.putString("token", newToken);
                                editor.apply();
                                proceedWithGameLoading(context, request, response, newToken, requestKey);
                            } else {
                                synchronized (pendingRequests) {
                                    pendingRequests.remove(requestKey);
                                }
                                response.onError(new IllegalArgumentException("خطا در رفرش توکن"));
                            }
                        }

                        @Override
                        public void onError(Throwable t) {
                            synchronized (pendingRequests) {
                                pendingRequests.remove(requestKey);
                            }
                            response.onError(t);
                        }
                    });
                } else {
                    proceedWithGameLoading(context, request, response, token, requestKey);
                }
            }

            @Override
            public void onError(Throwable t) {
                synchronized (pendingRequests) {
                    pendingRequests.remove(requestKey);
                }
                response.onError(t);
            }
        });
    }

    private static void proceedWithGameLoading(Context context, SocketRequest request, Response response, String token, String requestKey) throws JSONException {
        String event = request.getJsonObject().getString("event");
        final long TIMEOUT_MS = 15000; // افزایش تایم‌اوت به 15 ثانیه
        final Handler timeoutHandler = new Handler(Looper.getMainLooper());
        final Runnable timeoutRunnable = () -> {
            synchronized (pendingRequests) {
                pendingRequests.remove(requestKey);
            }
            response.onError(new IllegalArgumentException("درخواست منقضی شد: پاسخی از سرور دریافت نشد"));
            socket.off("game_loading_response");
        };
        timeoutHandler.postDelayed(timeoutRunnable, TIMEOUT_MS);

        socket.once(Socket.EVENT_CONNECT_ERROR, args -> {
            timeoutHandler.removeCallbacks(timeoutRunnable);
            synchronized (pendingRequests) {
                pendingRequests.remove(requestKey);
            }
            response.onError(new IllegalArgumentException("خطای اتصال: " + (args[0] != null ? args[0].toString() : "unknown error")));
        });

        String id = String.valueOf(RandomInteger.getRandomId());
        request.getJsonObject().put("id", id);
        isRequestCompleted = false;
        JSONObject requestData = new JSONObject();
        requestData.put("requestId", id);
        requestData.put("data", request.getJsonObject());
        requestData.put("token", token);

        socket.emit(event, requestData);
        socket.once("game_loading_response", args -> {
            timeoutHandler.removeCallbacks(timeoutRunnable);
            synchronized (pendingRequests) {
                pendingRequests.remove(requestKey);
            }
            JSONObject object = (JSONObject) args[0];
            try {
                boolean success = object.has("success") && object.getBoolean("success");
                if (success) {
                    isRequestCompleted = true;
                    response.onResponse(object, false);
                    ConsValue.socketRequestList.remove(request);
                } else {
                    response.onError(new IllegalArgumentException(object.has("message") ? object.getString("message") : "Unknown error"));
                }
            } catch (JSONException e) {
                response.onError(e);
            }
        });
    }

    private static void emitRequest(String event, JSONObject requestData, SocketRequest request, Handler timeoutHandler, Runnable timeoutRunnable, String requestKey) {
        socket.emit(event, requestData);
        String responseEvent = event + "_response";
        socket.once(responseEvent, args -> {
            timeoutHandler.removeCallbacks(timeoutRunnable);
            synchronized (pendingRequests) {
                pendingRequests.remove(requestKey);
            }
            Log.d("TEST", "Response received for event: " + responseEvent + ", Data: " + args[0].toString());
            handleResponse(responseEvent, args, request);
        });
    }

    private static void handleResponse(String event, Object[] args, SocketRequest request) {
        if (args[0] instanceof String) {
            if (((String) args[0]).equalsIgnoreCase("you are not in any room")) {
                try {
                    JSONObject registerData = new JSONObject();
                    String id = request.getJsonObject().getString("id");
                    registerData.put("requestId", id);
                    registerData.put("data", request.getJsonObject());
                    socket.emit("register", registerData);
                    socket.once("register_response", args1 -> handleResponse("register_response", args1, request));
                } catch (JSONException e) {
                    request.getResponse().onError(e);
                }
            } else {
                request.getResponse().onError(new IllegalArgumentException("خطای سرور: " + args[0].toString()));
            }
        } else {
            JSONObject object = (JSONObject) args[0];
            try {
                boolean success = object.has("success") && object.getBoolean("success");
                if (event.equals("login_response") || event.equals("register_response") ||
                        event.equals("create_room_response") || event.equals("join_room_response") ||
                        event.equals("leave_room_response") || event.equals("get_room_list_response") ||
                        event.equals("game_loading_response") || event.equals("play_card_response") ||
                        event.equals("get_profile_response") || event.equals("update_profile_response") ||
                        event.equals("check_pending_avatar_response") ||
                        event.equals("search_users_response") || event.equals("get_friend_requests_response") ||
                        event.equals("get_friends_response") || event.equals("send_friend_request_response") ||
                        event.equals("accept_friend_request_response") || event.equals("reject_friend_request_response") ||
                        event.equals("send_private_message_response") || event.equals("receive_private_message") ||
                        event.equals("get_missed_messages_response") || event.equals("load_messages_response")) {
                    if (success) {
                        if (event.equals("login_response") || event.equals("register_response")) {
                            ConsValue.isRegistered = true;
                        }
                        isRequestCompleted = true;
                        request.getResponse().onResponse(object, false);
                        ConsValue.socketRequestList.remove(request);
                    } else {
                        request.getResponse().onError(new IllegalArgumentException(object.has("message") ? object.getString("message") : "Unknown error"));
                    }
                } else if (event.equals("get_room_details_response") || event.equals("get_room_players_response")) {
                    if (success) {
                        isRequestCompleted = true;
                        request.getResponse().onResponse(object, false);
                        ConsValue.socketRequestList.remove(request);
                    } else {
                        request.getResponse().onError(new IllegalArgumentException(object.has("message") ? object.getString("message") : "Unknown error"));
                    }
                } else if (event.equals("message")) {
                    if (object.getJSONObject("Result").getString("Success").equalsIgnoreCase("False")) {
                        request.getResponse().onError(null);
                    } else {
                        int Rid = Integer.parseInt(object.getString("id"));
                        String id = request.getJsonObject().getString("id");
                        if (Integer.parseInt(id) == Rid) {
                            isRequestCompleted = true;
                            request.getResponse().onResponse(object, false);
                            ConsValue.socketRequestList.remove(request);
                        }
                    }
                } else if (event.equals("refresh_token_response")) {
                    if (success) {
                        isRequestCompleted = true;
                        request.getResponse().onResponse(object, false);
                        ConsValue.socketRequestList.remove(request);
                    } else {
                        request.getResponse().onError(new IllegalArgumentException(object.has("message") ? object.getString("message") : "Unknown error"));
                    }
                } else {
                    Log.e("SocketManager", "Unhandled response event: " + event);
                    request.getResponse().onError(new IllegalArgumentException("رویداد ناشناخته: " + event));
                }
            } catch (JSONException e) {
                request.getResponse().onError(e);
            }
        }
    }

    public static void verifyToken(Context context, String token, Response response) throws JSONException {
        reconnectIfNeeded();
        if (!socket.connected() || !isConnect) {
            response.onError(new IllegalArgumentException("اتصال برقرار نشد"));
            return;
        }

        String requestKey = "verify_token_" + token.hashCode();

        synchronized (pendingRequests) {
            if (pendingRequests.contains(requestKey)) {
                Log.d("TEST", "Duplicate verify_token request detected, skipping: " + requestKey);
                response.onError(new IllegalArgumentException("درخواست تکراری: verify_token"));
                return;
            }
            pendingRequests.add(requestKey);
        }

        JSONObject jsonObject = new JSONObject();
        jsonObject.put("token", token);
        jsonObject.put("requestId", String.valueOf(RandomInteger.getRandomId()));
        SocketRequest request = new SocketRequest(null, jsonObject, response);
        socket.emit("verify_token", jsonObject);
        socket.once("verify_token_response", args -> {
            synchronized (pendingRequests) {
                pendingRequests.remove(requestKey);
            }
            JSONObject object = (JSONObject) args[0];
            try {
                response.onResponse(object, false);
            } catch (JSONException e) {
                response.onError(e);
            }
        });
        socket.once(Socket.EVENT_CONNECT_ERROR, args -> {
            synchronized (pendingRequests) {
                pendingRequests.remove(requestKey);
            }
            response.onError(new IllegalArgumentException("اتصال برقرار نشد"));
        });
    }

    public static void refreshToken(Context context, JSONObject requestData, Response response) {
        reconnectIfNeeded();
        if (!socket.connected() || !isConnect) {
            response.onError(new IllegalArgumentException("اتصال برقرار نشد"));
            return;
        }

        String requestKey = "refresh_token_" + requestData.hashCode();

        synchronized (pendingRequests) {
            if (pendingRequests.contains(requestKey)) {
                Log.d("TEST", "Duplicate refresh_token request detected, skipping: " + requestKey);
                response.onError(new IllegalArgumentException("درخواست تکراری: refresh_token"));
                return;
            }
            pendingRequests.add(requestKey);
        }

        if (!requestData.has("token")) {
            try {
                SharedPreferences prefs = context.getSharedPreferences("auth", Context.MODE_PRIVATE);
                String token = prefs.getString("token", null);
                if (token != null) {
                    requestData.put("token", token);
                } else {
                    response.onError(new IllegalArgumentException("توکن موجود نیست"));
                    synchronized (pendingRequests) {
                        pendingRequests.remove(requestKey);
                    }
                    return;
                }
            } catch (JSONException e) {
                response.onError(e);
                synchronized (pendingRequests) {
                    pendingRequests.remove(requestKey);
                }
                return;
            }
        }

        socket.emit("refresh_token", requestData);
        socket.once("refresh_token_response", args -> {
            synchronized (pendingRequests) {
                pendingRequests.remove(requestKey);
            }
            JSONObject object = (JSONObject) args[0];
            try {
                response.onResponse(object, false);
            } catch (JSONException e) {
                response.onError(e);
            }
        });
        socket.once(Socket.EVENT_CONNECT_ERROR, args -> {
            synchronized (pendingRequests) {
                pendingRequests.remove(requestKey);
            }
            response.onError(new IllegalArgumentException("اتصال برقرار نشد"));
        });
    }

    public static void disconnect() {
        if (socket != null && socket.connected()) {
            socket.off("message");
            socket.off("register");
            socket.off("room_list_update");
            socket.off("room_players_update");
            socket.off("room_deleted");
            socket.off("game_started");
            socket.off("game_loading");
            socket.off("game_state_update");
            socket.off("player_cards");
            socket.off("choose_suit");
            socket.off("game_ended");
            socket.disconnect();
        }
    }

    public static Socket getSocket() {
        return socket;
    }

    public static void reconnectIfNeeded() {
        isConnect = socket.connected();
        if (!socket.connected()) {
            Log.d("TEST", "Reconnecting socket...");
            socket.connect();
            socket.once(Socket.EVENT_CONNECT, args -> {
                Log.d("TEST", "Socket reconnected successfully");
                isConnect = true;
            });
            socket.once(Socket.EVENT_CONNECT_ERROR, args -> {
                Log.e("TEST", "Reconnection failed: " + (args[0] != null ? args[0].toString() : "unknown error"));
                isConnect = false;
            });
            socket.once(Socket.EVENT_DISCONNECT, args -> {
                Log.d("TEST", "Socket disconnected during reconnection: " + (args.length > 0 ? args[0].toString() : "unknown reason"));
                isConnect = false;
            });
        }
    }

    public static Emitter.Listener listenForRoomListUpdates(RoomListUpdateListener listener) {
        Emitter.Listener emitterListener = args -> {
            try {
                JSONObject roomListData = (JSONObject) args[0];
                Log.d("TEST", "Room list updated: " + roomListData.toString());
                listener.onRoomListUpdate(roomListData);
            } catch (Exception e) {
                listener.onRoomListError(e);
            }
        };
        socket.on("room_list_update", emitterListener);
        return emitterListener;
    }

    public static void listenForRoomPlayersUpdates(RoomPlayersUpdateListener listener) {
        socket.on("room_players_update", args -> {
            try {
                JSONObject playersData = (JSONObject) args[0];
                if (playersData.has("roomNumber") && playersData.has("players")) {
                    Log.d("TEST", "Players updated for room: " + playersData.toString());
                    listener.onRoomPlayersUpdate(playersData);
                } else {
                    listener.onRoomPlayersError(new JSONException("Missing required fields in playersData"));
                }
            } catch (Exception e) {
                listener.onRoomPlayersError(e);
            }
        });
    }

    public static void getRoomPlayers(Context context, String roomNumber, RoomPlayersResponseListener listener) {
        JSONObject data = new JSONObject();
        try {
            data.put("event", "get_room_players");
            data.put("roomNumber", roomNumber);
        } catch (JSONException e) {
            listener.onRoomPlayersResponseError(e);
            return;
        }

        try {
            SocketRequest request = new SocketRequest(null, data, new Response() {
                @Override
                public void onResponse(JSONObject object, Boolean isError) throws JSONException {
                    if (!isError && object.getBoolean("success")) {
                        listener.onRoomPlayersResponse(object);
                    } else {
                        listener.onRoomPlayersResponseError(new IllegalArgumentException(object.has("message") ? object.getString("message") : "خطا در دریافت لیست بازیکن‌ها"));
                    }
                }

                @Override
                public void onError(Throwable t) {
                    listener.onRoomPlayersResponseError(t);
                }
            });
            sendRequest(context, request);
        } catch (JSONException e) {
            listener.onRoomPlayersResponseError(e);
        }
    }

    public static void listenForRoomDeleted(RoomDeletedListener listener) {
        socket.on("room_deleted", args -> {
            try {
                JSONObject data = (JSONObject) args[0];
                listener.onRoomDeleted(data);
            } catch (Exception e) {
                listener.onRoomDeletedError(e);
            }
        });
    }

    public static void listenForGameStart(GameStartListener listener) {
        socket.on("game_started", args -> {
            try {
                JSONObject data = (JSONObject) args[0];
                Log.d("TEST", "Game started: " + data.toString());
                listener.onGameStart(data);
            } catch (Exception e) {
                listener.onGameStartError(e);
            }
        });
    }

    public static void listenForGameLoading(GameLoadingListener listener) {
        socket.on("game_loading", args -> {
            try {
                JSONObject data = (JSONObject) args[0];
                listener.onGameLoading(data);
            } catch (Exception e) {
                listener.onGameLoadingError(e);
            }
        });
    }

    public static void listenForGameStateUpdates(GameStateUpdateListener listener) {
        Handler mainHandler = new Handler(Looper.getMainLooper());
        socket.on("game_state_update", args -> {
            try {
                JSONObject data = (JSONObject) args[0];
                Log.d("TEST", "Received game_state_update: " + data.toString());
                mainHandler.post(() -> listener.onGameStateUpdate(data));
            } catch (Exception e) {
                mainHandler.post(() -> listener.onGameStateUpdateError(e));
            }
        });
    }

    public static void getGamePlayersInfo(Context context, String gameId, String userId, GamePlayersInfoListener listener) {
        JSONObject data = new JSONObject();
        try {
            data.put("event", "get_game_players_info");
            data.put("gameId", gameId);
            data.put("userId", userId);
        } catch (JSONException e) {
            if (listener != null) listener.onGamePlayersInfoError(e);
            return;
        }

        try {
            SocketRequest request = new SocketRequest(null, data, new Response() {
                @Override
                public void onResponse(JSONObject object, Boolean isError) throws JSONException {
                    if (!isError && object.getBoolean("success") && listener != null) {
                        listener.onGamePlayersInfo(object);
                    } else if (listener != null) {
                        listener.onGamePlayersInfoError(new IllegalArgumentException(object.has("message") ? object.getString("message") : "خطا در دریافت اطلاعات بازیکن‌ها"));
                    }
                }

                @Override
                public void onError(Throwable t) {
                    if (listener != null) listener.onGamePlayersInfoError(t);
                }
            });
            sendRequest(context, request);
        } catch (JSONException e) {
            if (listener != null) listener.onGamePlayersInfoError(e);
        }
    }

    public static void uploadAvatar(Context context, String userId, Uri imageUri, UploadCallback callback) {
        reconnectIfNeeded();
        if (!socket.connected() || !isConnect) {
            callback.onError("اتصال برقرار نشد");
            return;
        }

        if (userId == null || userId.trim().isEmpty()) {
            callback.onError("شناسه کاربر نامعتبر است");
            return;
        }

        try {
            InputStream inputStream = context.getContentResolver().openInputStream(imageUri);
            if (inputStream == null) {
                callback.onError("خطا در خواندن تصویر");
                return;
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                baos.write(buffer, 0, bytesRead);
            }
            inputStream.close();
            byte[] imageBytes = baos.toByteArray();

            final int CHUNK_SIZE = 16 * 1024;
            int totalChunks = (int) Math.ceil((double) imageBytes.length / CHUNK_SIZE);
            String uploadId = UUID.randomUUID().toString();

            JSONObject initData = new JSONObject();
            initData.put("uploadId", uploadId);
            initData.put("userId", userId);
            initData.put("totalChunks", totalChunks);
            socket.emit("upload_avatar_init", initData);

            for (int i = 0; i < totalChunks; i++) {
                int start = i * CHUNK_SIZE;
                int end = Math.min(start + CHUNK_SIZE, imageBytes.length);
                byte[] chunk = new byte[end - start];
                System.arraycopy(imageBytes, start, chunk, 0, chunk.length);

                JSONObject chunkData = new JSONObject();
                chunkData.put("uploadId", uploadId);
                chunkData.put("chunkIndex", i);
                chunkData.put("totalChunks", totalChunks);
                chunkData.put("userId", userId);
                socket.emit("upload_avatar_chunk", chunkData, chunk);
            }

            socket.once("upload_avatar_complete_" + uploadId, args -> {
                try {
                    JSONObject response = (JSONObject) args[0];
                    if (response.getBoolean("success")) {
                        callback.onSuccess();
                    } else {
                        callback.onError(response.getString("message"));
                    }
                } catch (JSONException e) {
                    callback.onError("خطا در پردازش پاسخ سرور");
                }
            });

            socket.once("upload_avatar_error_" + uploadId, args -> {
                String errorMessage = args[0] != null ? args[0].toString() : "خطای ناشناخته";
                callback.onError(errorMessage);
            });

        } catch (Exception e) {
            callback.onError("خطا در آپلود تصویر: " + e.getMessage());
            e.printStackTrace();
        }
    }
}