package com.example.a4Barg.networking;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.example.a4Barg.App;
import com.example.a4Barg.model.SocketRequest;
import com.example.a4Barg.utils.ConsValue;
import com.example.a4Barg.utils.RandomInteger;

import org.json.JSONException;
import org.json.JSONObject;

import io.socket.client.Socket;
import io.socket.emitter.Emitter;

import java.util.ArrayList;
import java.util.List;

public class SocketManager {

    public static Socket socket = App.shared.getSocket();
    public static Boolean isConnect = false;
    public static Boolean isRequestCompleted = false;
    static Context context;

    // لیست شنونده‌های سراسری برای player_cards
    private static final List<PlayerCardsListener> playerCardsListeners = new ArrayList<>();

    // متد جدید برای مقداردهی اولیه سوکت با userId
    public static void initialize(Context context, String userId) {
        SocketManager.context = context;
        socket = App.shared.getSocket();
        socket.connect();
        socket.emit("set_user_id", userId); // ارسال userId به سرور
        socket.on(Socket.EVENT_CONNECT, args -> {
            Log.d("Socket", "Connected with userId: " + userId);
            isConnect = true;
            initializeGlobalListeners(); // فراخوانی بعد از اتصال موفق
        });
        socket.on(Socket.EVENT_CONNECT_ERROR, args -> {
            Log.e("Socket", "Connection error: " + (args[0] != null ? args[0].toString() : "unknown error"));
            isConnect = false;
        });
        socket.on(Socket.EVENT_DISCONNECT, args -> {
            Log.d("Socket", "Disconnected: " + (args.length > 0 ? args[0].toString() : "unknown reason"));
            isConnect = false;
        });
    }

    // متد برای اضافه کردن شنونده‌ها
    public static void addPlayerCardsListener(PlayerCardsListener listener) {
        playerCardsListeners.add(listener);
    }

    // متد برای حذف شنونده‌ها (اختیاری، برای مدیریت بهتر)
    public static void removePlayerCardsListener(PlayerCardsListener listener) {
        playerCardsListeners.remove(listener);
    }

    // ثبت شنونده سراسری در زمان راه‌اندازی
    public static void initializeGlobalListeners() {
        socket.on("player_cards", args -> {
            Log.d("TEST", "Received player_cards event globally: " + (args[0] != null ? args[0].toString() : "null"));
            try {
                JSONObject data = (JSONObject) args[0];
                Handler mainHandler = new Handler(Looper.getMainLooper());
                mainHandler.post(() -> {
                    for (PlayerCardsListener listener : playerCardsListeners) {
                        listener.onPlayerCards(data);
                    }
                });
            } catch (Exception e) {
                Log.e("TEST", "Error parsing player_cards globally: " + e.getMessage());
                Handler mainHandler = new Handler(Looper.getMainLooper());
                mainHandler.post(() -> {
                    for (PlayerCardsListener listener : playerCardsListeners) {
                        listener.onPlayerCardsError(e);
                    }
                });
            }
        });

        // اضافه کردن لاگ برای همه رویدادهای دریافتی با تگ "Socket"
        socket.onAnyIncoming(new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                String eventName = args[0].toString();
                String data = args.length > 1 ? args[1].toString() : "No data";
                Log.d("Socket", "[Client] Received event: " + eventName + ", Data: " + data);
            }
        });
    }

    // بقیه متدها بدون تغییر باقی می‌مونن
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

    // اضافه کردن اینترفیس جدید برای اطلاعات بازیکن‌ها
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

    public static void addRequest(SocketRequest request) {
        ConsValue.socketRequestList.add(request);
        Log.d("TEST", "Request added to queue: " + request.getJsonObject().toString());
    }

    public static void sendRequest(Context context, SocketRequest request) throws JSONException {
        SocketManager.context = context;

        Log.d("TEST", "Starting sendRequest - Initial socket.connected(): " + socket.connected() + ", isConnect: " + isConnect);
        reconnectIfNeeded();
        Log.d("TEST", "After reconnect attempt - socket.connected(): " + socket.connected() + ", isConnect: " + isConnect);
        if (!socket.connected() || !isConnect) {
            Log.d("TEST", "Connection check failed - socket.connected(): " + socket.connected() + ", isConnect: " + isConnect);
            request.getResponse().onError(new IllegalArgumentException("اتصال برقرار نشد"));
            return;
        }

        SharedPreferences prefs = context.getSharedPreferences("auth", Context.MODE_PRIVATE);
        String token = prefs.getString("token", null);
        String event = request.getJsonObject().getString("event");

        if (token == null && !event.equals("login") && !event.equals("register")) {
            Log.d("TEST", "No token available for request: " + request.getJsonObject().toString());
            request.getResponse().onError(new IllegalArgumentException("توکن احراز هویت موجود نیست"));
            return;
        }

        final long TIMEOUT_MS = 10000;
        final Handler timeoutHandler = new Handler(Looper.getMainLooper());
        final Runnable timeoutRunnable = () -> {
            Log.d("TEST", "Request timed out, no response received for event: " + event);
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
        };
        timeoutHandler.postDelayed(timeoutRunnable, TIMEOUT_MS);

        socket.once(Socket.EVENT_CONNECT_ERROR, args -> {
            Log.d("TEST", "Connection error during sendRequest: " + (args[0] != null ? args[0].toString() : "unknown error"));
            timeoutHandler.removeCallbacks(timeoutRunnable);
            request.getResponse().onError(new IllegalArgumentException("خطای اتصال: " + (args[0] != null ? args[0].toString() : "unknown error")));
        });

        if (ConsValue.isRegistered) {
            String id = String.valueOf(RandomInteger.getRandomId());
            request.getJsonObject().put("id", id);
            isRequestCompleted = false;
            Log.d("TEST", "Preparing request - isRegistered: true, requestData: " + request.getJsonObject().toString());
            JSONObject requestData = new JSONObject();
            requestData.put("requestId", id);
            requestData.put("data", request.getJsonObject());
            if (!event.equals("login") && !event.equals("register")) {
                requestData.put("token", token);
            }
            emitRequest(event, requestData, request, timeoutHandler, timeoutRunnable);
        } else {
            Log.d("TEST", "Not registered, preparing initial request");
            String id = String.valueOf(RandomInteger.getRandomId());
            request.getJsonObject().put("id", id);
            JSONObject requestData = new JSONObject();
            requestData.put("requestId", id);
            requestData.put("data", request.getJsonObject());
            if (token != null) {
                requestData.put("token", token);
            }
            emitRequest(event, requestData, request, timeoutHandler, timeoutRunnable);
        }
    }

    public static void sendGameLoadingRequest(Context context, SocketRequest request, Response response) throws JSONException {
        SocketManager.context = context;

        Log.d("TEST", "Starting sendGameLoadingRequest - Initial socket.connected(): " + socket.connected() + ", isConnect: " + isConnect);
        reconnectIfNeeded();
        Log.d("TEST", "After reconnect attempt - socket.connected(): " + socket.connected() + ", isConnect: " + isConnect);
        if (!socket.connected() || !isConnect) {
            Log.d("TEST", "Connection check failed - socket.connected(): " + socket.connected() + ", isConnect: " + isConnect);
            response.onError(new IllegalArgumentException("اتصال برقرار نشد"));
            return;
        }

        SharedPreferences prefs = context.getSharedPreferences("auth", Context.MODE_PRIVATE);
        String token = prefs.getString("token", null);
        String event = request.getJsonObject().getString("event");

        if (token == null) {
            Log.d("TEST", "No token available for game_loading request: " + request.getJsonObject().toString());
            response.onError(new IllegalArgumentException("توکن احراز هویت موجود نیست"));
            return;
        }

        verifyToken(context, token, new Response() {
            @Override
            public void onResponse(JSONObject object, Boolean isError) throws JSONException {
                if (isError || !object.getBoolean("success")) {
                    Log.d("TEST", "Token verification failed, attempting to refresh token");
                    refreshToken(context, new JSONObject(), new Response() {
                        @Override
                        public void onResponse(JSONObject refreshResponse, Boolean refreshError) throws JSONException {
                            if (!refreshError && refreshResponse.getBoolean("success")) {
                                String newToken = refreshResponse.getString("token");
                                SharedPreferences.Editor editor = prefs.edit();
                                editor.putString("token", newToken);
                                editor.apply();
                                Log.d("TEST", "Token refreshed successfully, retrying game_loading request");
                                proceedWithGameLoading(context, request, response, newToken);
                            } else {
                                Log.d("TEST", "Token refresh failed: " + refreshResponse.getString("message"));
                                response.onError(new IllegalArgumentException("خطا در رفرش توکن"));
                            }
                        }

                        @Override
                        public void onError(Throwable t) {
                            Log.d("TEST", "Error refreshing token: " + t.getMessage());
                            response.onError(t);
                        }
                    });
                } else {
                    proceedWithGameLoading(context, request, response, token);
                }
            }

            @Override
            public void onError(Throwable t) {
                Log.d("TEST", "Error verifying token: " + t.getMessage());
                response.onError(t);
            }
        });
    }

    private static void proceedWithGameLoading(Context context, SocketRequest request, Response response, String token) throws JSONException {
        String event = request.getJsonObject().getString("event");
        final long TIMEOUT_MS = 10000;
        final Handler timeoutHandler = new Handler(Looper.getMainLooper());
        final Runnable timeoutRunnable = () -> {
            Log.d("TEST", "Game loading request timed out, no response received for event: " + event);
            response.onError(new IllegalArgumentException("درخواست منقضی شد: پاسخی از سرور دریافت نشد"));
            socket.off("game_loading_response");
        };
        timeoutHandler.postDelayed(timeoutRunnable, TIMEOUT_MS);

        socket.once(Socket.EVENT_CONNECT_ERROR, args -> {
            Log.d("TEST", "Connection error during game_loading request: " + (args[0] != null ? args[0].toString() : "unknown error"));
            timeoutHandler.removeCallbacks(timeoutRunnable);
            response.onError(new IllegalArgumentException("خطای اتصال: " + (args[0] != null ? args[0].toString() : "unknown error")));
        });

        String id = String.valueOf(RandomInteger.getRandomId());
        request.getJsonObject().put("id", id);
        isRequestCompleted = false;
        Log.d("TEST", "Preparing game_loading request - requestData: " + request.getJsonObject().toString());
        JSONObject requestData = new JSONObject();
        requestData.put("requestId", id);
        requestData.put("data", request.getJsonObject());
        requestData.put("token", token);

        Log.d("TEST", "Emitting game_loading request - requestData: " + requestData.toString());
        socket.emit(event, requestData);
        Log.d("TEST", event + " request emitted");

        socket.once("game_loading_response", args -> {
            Log.d("TEST", "Received game_loading_response: " + (args[0] != null ? args[0].toString() : "null"));
            timeoutHandler.removeCallbacks(timeoutRunnable);
            JSONObject object = (JSONObject) args[0];
            try {
                boolean success = object.has("success") && object.getBoolean("success");
                Log.d("TEST", "Processing game_loading_response - Success: " + success);
                if (success) {
                    isRequestCompleted = true;
                    response.onResponse(object, false);
                    Log.d("TEST", "Game loading request completed, removing from queue");
                    ConsValue.socketRequestList.remove(request);
                } else {
                    Log.d("TEST", "Game loading request failed, error message: " + (object.has("message") ? object.getString("message") : "Unknown error"));
                    response.onError(new IllegalArgumentException(object.has("message") ? object.getString("message") : "Unknown error"));
                }
            } catch (JSONException e) {
                Log.e("TEST", "JSONException during game_loading response handling: " + e.getMessage());
                response.onError(e);
            }
        });
    }

    private static void emitRequest(String event, JSONObject requestData, SocketRequest request, Handler timeoutHandler, Runnable timeoutRunnable) {
        Log.d("TEST", "Emitting " + event + " request - requestData: " + requestData.toString());
        socket.emit(event, requestData);
        Log.d("TEST", event + " request emitted");

        String responseEvent = event + "_response";
        socket.once(responseEvent, args -> {
            Log.d("TEST", "Received " + responseEvent + ": " + (args[0] != null ? args[0].toString() : "null"));
            timeoutHandler.removeCallbacks(timeoutRunnable);
            handleResponse(responseEvent, args, request);
        });
    }

    private static void handleResponse(String event, Object[] args, SocketRequest request) {
        Log.d("TEST", "Handling response - Event: " + event + ", Response: " + (args[0] != null ? args[0].toString() : "null"));
        if (args[0] instanceof String) {
            if (((String) args[0]).equalsIgnoreCase("you are not in any room")) {
                Log.d("TEST", "Detected 'you are not in any room', attempting re-register");
                try {
                    JSONObject registerData = new JSONObject();
                    String id = request.getJsonObject().getString("id");
                    registerData.put("requestId", id);
                    registerData.put("data", request.getJsonObject());
                    Log.d("TEST", "Emitting re-register request: " + registerData.toString());
                    socket.emit("register", registerData);
                    socket.once("register_response", args1 -> {
                        Log.d("TEST", "Received re-register response: " + (args1[0] != null ? args1[0].toString() : "null"));
                        handleResponse("register_response", args1, request);
                    });
                } catch (JSONException e) {
                    Log.d("TEST", "JSONException during re-register: " + e.getMessage());
                    request.getResponse().onError(e);
                }
            } else {
                String serverMessage = args[0].toString();
                Log.d("TEST", "Unexpected string response from server: " + serverMessage);
                request.getResponse().onError(new IllegalArgumentException("خطای سرور: " + serverMessage));
            }
        } else {
            JSONObject object = (JSONObject) args[0];
            try {
                boolean success = object.has("success") && object.getBoolean("success");
                Log.d("TEST", "Processing JSON response - Event: " + event + ", Success: " + success);
                if (event.equals("login_response") || event.equals("register_response") ||
                        event.equals("create_room_response") || event.equals("join_room_response") ||
                        event.equals("leave_room_response") || event.equals("get_room_list_response") ||
                        event.equals("game_loading_response")) {
                    if (success) {
                        if (event.equals("login_response")) {
                            Log.d("TEST", "Login successful, setting isRegistered to true");
                            ConsValue.isRegistered = true;
                        } else if (event.equals("register_response")) {
                            Log.d("TEST", "Register successful, setting isRegistered to true");
                            ConsValue.isRegistered = true;
                        }
                        isRequestCompleted = true;
                        request.getResponse().onResponse(object, false);
                        Log.d("TEST", "Request completed, removing from queue");
                        ConsValue.socketRequestList.remove(request);
                    } else {
                        Log.d("TEST", "Request failed, error message: " + (object.has("message") ? object.getString("message") : "Unknown error"));
                        request.getResponse().onError(new IllegalArgumentException(object.has("message") ? object.getString("message") : "Unknown error"));
                    }
                } else if (event.equals("get_room_details_response")) {
                    if (success) {
                        isRequestCompleted = true;
                        request.getResponse().onResponse(object, false);
                        Log.d("TEST", "Request completed, removing from queue, full response: " + object.toString());
                        ConsValue.socketRequestList.remove(request);
                    } else {
                        Log.d("TEST", "Request failed, error message: " + (object.has("message") ? object.getString("message") : "Unknown error"));
                        request.getResponse().onError(new IllegalArgumentException(object.has("message") ? object.getString("message") : "Unknown error"));
                    }
                } else if (event.equals("get_room_players_response")) {
                    if (success) {
                        isRequestCompleted = true;
                        request.getResponse().onResponse(object, false);
                        Log.d("TEST", "Request completed, removing from queue");
                        ConsValue.socketRequestList.remove(request);
                    } else {
                        Log.d("TEST", "Request failed, error message: " + (object.has("message") ? object.getString("message") : "Unknown error"));
                        request.getResponse().onError(new IllegalArgumentException(object.has("message") ? object.getString("message") : "Unknown error"));
                    }
                } else if (event.equals("message")) {
                    if (object.getJSONObject("Result").getString("Success").equalsIgnoreCase("False")) {
                        Log.d("TEST", "Message request failed");
                        request.getResponse().onError(null);
                    } else {
                        int Rid = Integer.parseInt(object.getString("id"));
                        String id = request.getJsonObject().getString("id");
                        if (Integer.parseInt(id) == Rid) {
                            Log.d("TEST", "Message request matched, id: " + id);
                            isRequestCompleted = true;
                            request.getResponse().onResponse(object, false);
                            Log.d("TEST", "Message request completed, removing from queue");
                            ConsValue.socketRequestList.remove(request);
                        } else {
                            Log.d("TEST", "Message request id mismatch, expected: " + id + ", received: " + Rid);
                        }
                    }
                }
            } catch (JSONException e) {
                Log.e("TEST", "JSONException during response handling: " + e.getMessage());
                request.getResponse().onError(e);
            }
        }
    }

    public static void verifyToken(Context context, String token, Response response) throws JSONException {
        Log.d("TEST", "Starting verifyToken - Initial socket.connected(): " + socket.connected() + ", isConnect: " + isConnect);
        reconnectIfNeeded();
        Log.d("TEST", "After reconnect attempt - socket.connected(): " + socket.connected() + ", isConnect: " + isConnect);
        if (!socket.connected() || !isConnect) {
            Log.d("TEST", "Connection check failed - socket.connected(): " + socket.connected() + ", isConnect: " + isConnect);
            response.onError(new IllegalArgumentException("اتصال برقرار نشد"));
            return;
        }

        JSONObject jsonObject = new JSONObject();
        jsonObject.put("token", token);
        jsonObject.put("requestId", String.valueOf(RandomInteger.getRandomId()));
        SocketRequest request = new SocketRequest(null, jsonObject, response);
        Log.d("TEST", "Preparing verifyToken request: " + jsonObject.toString());
        socket.emit("verify_token", jsonObject);
        socket.once("verify_token_response", args -> {
            Log.d("TEST", "verifyToken response received: " + (args[0] != null ? args[0].toString() : "null"));
            JSONObject object = (JSONObject) args[0];
            try {
                response.onResponse(object, false);
            } catch (JSONException e) {
                Log.d("TEST", "JSONException during verifyToken response: " + e.getMessage());
                response.onError(e);
            }
        });
        socket.once(Socket.EVENT_CONNECT_ERROR, args -> {
            Log.d("TEST", "Connection error during verifyToken: " + (args[0] != null ? args[0].toString() : "unknown error"));
            response.onError(new IllegalArgumentException("اتصال برقرار نشد"));
        });
    }

    public static void refreshToken(Context context, JSONObject requestData, Response response) {
        Log.d("TEST", "Starting refreshToken - Initial socket.connected(): " + socket.connected() + ", isConnect: " + isConnect);
        reconnectIfNeeded();
        Log.d("TEST", "After reconnect attempt - socket.connected(): " + socket.connected() + ", isConnect: " + isConnect);
        if (!socket.connected() || !isConnect) {
            Log.d("TEST", "Connection check failed - socket.connected(): " + socket.connected() + ", isConnect: " + isConnect);
            response.onError(new IllegalArgumentException("اتصال برقرار نشد"));
            return;
        }

        socket.emit("refresh_token", requestData);
        Log.d("TEST", "refreshToken request emitted: " + requestData.toString());
        socket.once("refresh_token_response", args -> {
            Log.d("TEST", "refreshToken response received: " + (args[0] != null ? args[0].toString() : "null"));
            JSONObject object = (JSONObject) args[0];
            try {
                response.onResponse(object, false);
            } catch (JSONException e) {
                Log.d("TEST", "JSONException during refreshToken response: " + e.getMessage());
                response.onError(e);
            }
        });
        socket.once(Socket.EVENT_CONNECT_ERROR, args -> {
            Log.d("TEST", "Connection error during refreshToken: " + (args[0] != null ? args[0].toString() : "unknown error"));
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
            Log.d("TEST", "Socket disconnected manually");
        }
    }

    public static Socket getSocket() {
        return socket;
    }

    public static void reconnectIfNeeded() {
        isConnect = socket.connected();
        Log.d("TEST", "Initial sync - socket.connected(): " + socket.connected() + ", isConnect: " + isConnect);
        if (!socket.connected()) {
            Log.d("TEST", "Attempting to reconnect - Initial socket.connected(): " + socket.connected());
            socket.connect();
            socket.once(Socket.EVENT_CONNECT, args -> {
                Log.d("TEST", "Reconnection successful - socket.connected(): " + socket.connected());
                isConnect = true;
            });
            socket.once(Socket.EVENT_CONNECT_ERROR, args -> {
                Log.d("TEST", "Reconnection failed - Error: " + (args[0] != null ? args[0].toString() : "unknown error") + ", socket.connected(): " + socket.connected());
                isConnect = false;
            });
            socket.once(Socket.EVENT_DISCONNECT, args -> {
                Log.d("TEST", "Socket disconnected during reconnect - Reason: " + (args != null && args.length > 0 ? args[0].toString() : "unknown") + ", socket.connected(): " + socket.connected());
                isConnect = false;
            });
        } else {
            Log.d("TEST", "Socket already connected - No reconnect needed, socket.connected(): " + socket.connected());
        }
    }

    public static void listenForRoomListUpdates(RoomListUpdateListener listener) {
        socket.on("room_list_update", args -> {
            Log.d("TEST", "Received room_list_update: " + (args[0] != null ? args[0].toString() : "null"));
            try {
                JSONObject roomListData = (JSONObject) args[0];
                listener.onRoomListUpdate(roomListData);
            } catch (Exception e) {
                Log.d("TEST", "Error in room_list_update: " + e.getMessage());
                listener.onRoomListError(e);
            }
        });
    }

    public static void listenForRoomPlayersUpdates(RoomPlayersUpdateListener listener) {
        socket.on("room_players_update", args -> {
            Log.d("TEST", "Received room_players_update: " + (args[0] != null ? args[0].toString() : "null"));
            try {
                JSONObject playersData = (JSONObject) args[0];
                if (playersData.has("roomNumber") && playersData.has("players")) {
                    listener.onRoomPlayersUpdate(playersData);
                } else {
                    Log.w("TEST", "Invalid room_players_update data: " + playersData.toString());
                    listener.onRoomPlayersError(new JSONException("Missing required fields in playersData"));
                }
            } catch (Exception e) {
                Log.e("TEST", "Error in room_players_update: " + e.getMessage());
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
                        String errorMsg = object.has("message") ? object.getString("message") : "خطا در دریافت لیست بازیکن‌ها";
                        listener.onRoomPlayersResponseError(new IllegalArgumentException(errorMsg));
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
            Log.d("TEST", "Received room_deleted: " + (args[0] != null ? args[0].toString() : "null"));
            try {
                JSONObject data = (JSONObject) args[0];
                listener.onRoomDeleted(data);
            } catch (Exception e) {
                Log.d("TEST", "Error in room_deleted: " + e.getMessage());
                listener.onRoomDeletedError(e);
            }
        });
    }

    public static void listenForGameStart(GameStartListener listener) {
        socket.on("game_started", args -> {
            Log.d("TEST", "Received game_started: " + (args[0] != null ? args[0].toString() : "null"));
            try {
                JSONObject data = (JSONObject) args[0];
                Log.d("TEST", "Calling onGameStart with data: " + data.toString());
                listener.onGameStart(data);
            } catch (Exception e) {
                Log.e("TEST", "Error in game_started: " + e.getMessage());
                listener.onGameStartError(e);
            }
        });
    }

    public static void listenForGameLoading(GameLoadingListener listener) {
        socket.on("game_loading", args -> {
            Log.d("TEST", "Received game_loading: " + (args[0] != null ? args[0].toString() : "null"));
            try {
                JSONObject data = (JSONObject) args[0];
                listener.onGameLoading(data);
            } catch (Exception e) {
                Log.d("TEST", "Error in game_loading: " + e.getMessage());
                listener.onGameLoadingError(e);
            }
        });
    }

    public static void listenForGameStateUpdates(GameStateUpdateListener listener) {
        Handler mainHandler = new Handler(Looper.getMainLooper());
        socket.on("game_state_update", args -> {
            Log.d("TEST", "Received game_state_update event: " + (args[0] != null ? args[0].toString() : "null"));
            try {
                JSONObject data = (JSONObject) args[0];
                Log.d("TEST", "Parsed game_state_update data: " + data.toString());
                Log.d("TEST", "Calling onGameStateUpdate with data: " + data.toString());
                mainHandler.post(() -> listener.onGameStateUpdate(data));
            } catch (Exception e) {
                Log.e("TEST", "Error parsing game_state_update: " + e.getMessage());
                mainHandler.post(() -> listener.onGameStateUpdateError(e));
            }
        });
    }

    public static void listenForGameEnded(GameEndedListener listener) {
        socket.on("game_ended", args -> {
            Log.d("TEST", "Received game_ended: " + (args[0] != null ? args[0].toString() : "null"));
            try {
                JSONObject data = (JSONObject) args[0];
                listener.onGameEnded(data);
            } catch (Exception e) {
                Log.d("TEST", "Error in game_ended: " + e.getMessage());
                listener.onGameEndedError(e);
            }
        });
    }

    // متد جدید برای درخواست اطلاعات بازیکن‌ها
    public static void getGamePlayersInfo(Context context, String gameId, String userId, GamePlayersInfoListener listener) {
        JSONObject data = new JSONObject();
        try {
            data.put("event", "get_game_players_info");
            data.put("gameId", gameId);
            data.put("userId", userId);
        } catch (JSONException e) {
            listener.onGamePlayersInfoError(e);
            return;
        }

        try {
            SocketRequest request = new SocketRequest(null, data, new Response() {
                @Override
                public void onResponse(JSONObject object, Boolean isError) throws JSONException {
                    if (!isError && object.getBoolean("success")) {
                        listener.onGamePlayersInfo(object);
                    } else {
                        String errorMsg = object.has("message") ? object.getString("message") : "خطا در دریافت اطلاعات بازیکن‌ها";
                        listener.onGamePlayersInfoError(new IllegalArgumentException(errorMsg));
                    }
                }

                @Override
                public void onError(Throwable t) {
                    listener.onGamePlayersInfoError(t);
                }
            });
            sendRequest(context, request);
        } catch (JSONException e) {
            listener.onGamePlayersInfoError(e);
        }
    }
}