package com.example.a4Barg.scene.chat;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.a4Barg.R;
import com.example.a4Barg.model.Message;

import java.util.List;

public class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.ViewHolder> {

    private List<Message> messages;
    private String currentUserId;

    public ChatAdapter(List<Message> messages) {
        this.messages = messages;
    }

    public void setCurrentUserId(String currentUserId) {
        this.currentUserId = currentUserId;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_message, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Message message = messages.get(position);
        holder.messageTextView.setText(message.getMessage());
        holder.timestampTextView.setText(message.getTimestamp());

        // تنظیم جهت پیام بر اساس فرستنده
        if (message.getSender().equals(currentUserId)) {
            // پیام ارسالی (سمت راست)
            holder.messageTextView.setBackgroundResource(R.drawable.sent_message_background);
            holder.itemView.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
            holder.messageTextView.setGravity(android.view.Gravity.END);
            holder.timestampTextView.setGravity(android.view.Gravity.END);
        } else {
            // پیام دریافتی (سمت چپ)
            holder.messageTextView.setBackgroundResource(R.drawable.received_message_background);
            holder.itemView.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
            holder.messageTextView.setGravity(android.view.Gravity.START);
            holder.timestampTextView.setGravity(android.view.Gravity.START);
        }
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    public void updateMessages(List<Message> newMessages) {
        this.messages.clear();
        this.messages.addAll(newMessages);
        notifyDataSetChanged();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView messageTextView;
        TextView timestampTextView;

        ViewHolder(View itemView) {
            super(itemView);
            messageTextView = itemView.findViewById(R.id.messageTextView);
            timestampTextView = itemView.findViewById(R.id.timestampTextView);
        }
    }
}