package com.example.a4Barg.scene.chat;

import android.app.Activity;
import android.app.Application;
import android.util.Log;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;

import com.example.a4Barg.model.Message;
import com.example.a4Barg.model.SocketRequest;
import com.example.a4Barg.networking.SocketManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class ChatViewModel extends AndroidViewModel {

    private String userId;
    private String targetUserId;
    private MutableLiveData<List<Message>> messages = new MutableLiveData<>(new ArrayList<>());
    private MutableLiveData<String> status = new MutableLiveData<>("آنلاین");
    private SocketManager.CustomListener messageListener;

    public ChatViewModel(Application application) {
        super(application);
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public void setTargetUserId(String targetUserId) {
        this.targetUserId = targetUserId;
    }

    public String getUserId() {
        return userId;
    }

    public MutableLiveData<List<Message>> getMessages() {
        return messages;
    }

    public MutableLiveData<String> getStatus() {
        return status;
    }

    public void loadMessages(Activity activity) {
        try {
            JSONObject data = new JSONObject();
            data.put("event", "load_messages");
            data.put("userId", userId);
            data.put("targetUserId", targetUserId);

            SocketRequest request = new SocketRequest(activity, data, new SocketManager.Response() {
                @Override
                public void onResponse(JSONObject object, Boolean isError) throws JSONException {
                    if (object.getBoolean("success")) {
                        JSONArray jsonMessages = object.getJSONArray("messages");
                        List<Message> messageList = new ArrayList<>();
                        for (int i = 0; i < jsonMessages.length(); i++) {
                            JSONObject jsonMessage = jsonMessages.getJSONObject(i);
                            // استخراج sender و receiver از ObjectId یا رشته مستقیم
                            String sender = jsonMessage.has("sender") && jsonMessage.get("sender") instanceof JSONObject
                                    ? jsonMessage.getJSONObject("sender").getString("_id")
                                    : jsonMessage.getString("sender");
                            String receiver = jsonMessage.has("receiver") && jsonMessage.get("receiver") instanceof JSONObject
                                    ? jsonMessage.getJSONObject("receiver").getString("_id")
                                    : jsonMessage.getString("receiver");
                            String messageText = jsonMessage.getString("message");
                            String timestamp = jsonMessage.getString("timestamp");
                            Message message = new Message(sender, receiver, messageText, timestamp);
                            messageList.add(message);
                        }
                        messages.postValue(messageList);
                    } else {
                        Log.e("ChatViewModel", "Failed to load messages: " + object.getString("message"));
                    }
                }

                @Override
                public void onError(Throwable t) {
                    Log.e("ChatViewModel", "Error loading messages: " + t.getMessage());
                }
            });
            SocketManager.sendRequest(activity, request);
        } catch (JSONException e) {
            Log.e("ChatViewModel", "Error preparing loadMessages request: " + e.getMessage());
        }

        setupMessageListener();
    }

    public void checkStatus() {
        // اینجا می‌تونیم از سرور وضعیت رو چک کنیم (بعداً پیاده‌سازی می‌شه)
        // فعلاً فقط یه مقدار پیش‌فرض می‌دیم
    }

    private void setupMessageListener() {
        messageListener = data -> {
            try {
                JSONObject messageData = data.getJSONObject("message");
                String sender = messageData.getString("sender");
                String receiver = messageData.getString("receiver");
                if ((sender.equals(userId) && receiver.equals(targetUserId)) || (sender.equals(targetUserId) && receiver.equals(userId))) {
                    Message message = new Message(
                            sender,
                            receiver,
                            messageData.getString("message"),
                            messageData.getString("timestamp")
                    );
                    List<Message> currentMessages = messages.getValue();
                    if (currentMessages == null) {
                        currentMessages = new ArrayList<>();
                    }
                    currentMessages.add(message);
                    messages.postValue(currentMessages);
                }
            } catch (JSONException e) {
                Log.e("ChatViewModel", "Error parsing message: " + e.getMessage());
            }
        };
        SocketManager.addCustomListener("receive_private_message", messageListener);
    }

    public void cleanup() {
        if (messageListener != null) {
            SocketManager.addCustomListener("receive_private_message", messageListener);
        }
    }
}