package com.example.a4Barg.scene.chat;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.a4Barg.R;
import com.example.a4Barg.model.Message;
import com.example.a4Barg.model.Player;
import com.example.a4Barg.model.SocketRequest;
import com.example.a4Barg.networking.SocketManager;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class ChatActivity extends AppCompatActivity {

    private ChatViewModel viewModel;
    private RecyclerView recyclerView;
    private ChatAdapter chatAdapter;
    private EditText textContent;
    private ImageView sendButton;
    private TextView usernameTextView, statusTextView;
    private ImageView avatarImageView;
    private String targetUserId;
    private String currentUserId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        // دریافت اطلاعات از Intent
        targetUserId = getIntent().getStringExtra("userId");
        currentUserId = getIntent().getStringExtra("currentUserId");
        Player targetPlayer = (Player) getIntent().getSerializableExtra("player");
        if (targetUserId == null || targetPlayer == null || currentUserId == null) {
            Toast.makeText(this, "کاربر مقصد نامعتبر است", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        viewModel = new ViewModelProvider(this).get(ChatViewModel.class);
        viewModel.setUserId(currentUserId);
        viewModel.setTargetUserId(targetUserId);

        // بایند کردن ویوها
        recyclerView = findViewById(R.id.recyclerView);
        textContent = findViewById(R.id.text_content);
        sendButton = findViewById(R.id.btn_send);
        usernameTextView = findViewById(R.id.usernameTextView);
        statusTextView = findViewById(R.id.statusTextView);
        avatarImageView = findViewById(R.id.avatarImageView);

        // تنظیم RecyclerView
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        chatAdapter = new ChatAdapter(new ArrayList<>());
        chatAdapter.setCurrentUserId(currentUserId); // پاس دادن currentUserId به ChatAdapter
        recyclerView.setAdapter(chatAdapter);

        // نمایش اطلاعات کاربر مقابل
        usernameTextView.setText(targetPlayer.getUsername());
        if (targetPlayer.getAvatar() != null && !targetPlayer.getAvatar().isEmpty()) {
            Glide.with(this).load(targetPlayer.getAvatar()).into(avatarImageView);
        } else {
            avatarImageView.setImageResource(R.drawable.user_icon);
        }

        // مشاهده تغییرات
        viewModel.getMessages().observe(this, messages -> {
            chatAdapter.updateMessages(messages);
            // اسکرول به انتهای لیست بعد از به‌روزرسانی پیام‌ها
            if (!messages.isEmpty()) {
                recyclerView.scrollToPosition(messages.size() - 1);
            }
        });
        viewModel.getStatus().observe(this, status -> statusTextView.setText(status));

        // کلیک دکمه ارسال
        sendButton.setOnClickListener(v -> {
            String message = textContent.getText().toString().trim();
            if (!message.isEmpty()) {
                sendMessage(message);
                textContent.setText("");
            }
        });

        // بارگذاری پیام‌ها (وضعیت هم در این مرحله دریافت می‌شود)
        viewModel.loadMessages(this);
    }

    private void sendMessage(String message) {
        try {
            JSONObject data = new JSONObject();
            data.put("event", "send_private_message");
            data.put("toUserId", targetUserId);
            data.put("message", message);

            SocketRequest request = new SocketRequest(this, data, new SocketManager.Response() {
                @Override
                public void onResponse(JSONObject object, Boolean isError) throws JSONException {
                    if (object.getBoolean("success")) {
                        Log.d("ChatActivity2", "Message sent successfully");
                        // اضافه کردن پیام ارسالی به لیست پیام‌ها
                        List<Message> currentMessages = viewModel.getMessages().getValue();
                        if (currentMessages == null) {
                            currentMessages = new ArrayList<>();
                        }
                        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");
                        String timestamp = sdf.format(new Date());
                        Message newMessage = new Message(currentUserId, targetUserId, message, timestamp);
                        currentMessages.add(newMessage);
                        viewModel.getMessages().postValue(currentMessages);
                    } else {
                        runOnUiThread(() -> {
                            try {
                                Toast.makeText(ChatActivity.this, object.getString("message"), Toast.LENGTH_SHORT).show();
                            } catch (JSONException e) {
                                throw new RuntimeException(e);
                            }
                        });
                    }
                }

                @Override
                public void onError(Throwable t) {
                    runOnUiThread(() -> Toast.makeText(ChatActivity.this, "خطا در ارسال پیام", Toast.LENGTH_SHORT).show());
                }
            });
            SocketManager.sendRequest(this, request);
        } catch (JSONException e) {
            runOnUiThread(() -> Toast.makeText(this, "خطا در پردازش درخواست", Toast.LENGTH_SHORT).show());
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        viewModel.cleanup();
    }
}