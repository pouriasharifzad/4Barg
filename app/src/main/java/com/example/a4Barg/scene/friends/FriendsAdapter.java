package com.example.a4Barg.scene.friends;

import android.content.Intent;
import android.content.SharedPreferences;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.a4Barg.R;
import com.example.a4Barg.model.Player;
import com.example.a4Barg.scene.chat.ChatActivity;

import java.util.List;

public class FriendsAdapter extends RecyclerView.Adapter<FriendsAdapter.ViewHolder> {

    private List<Player> friends;

    public FriendsAdapter(List<Player> friends) {
        this.friends = friends;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_friend, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Player friend = friends.get(position);
        holder.usernameTextView.setText(friend.getUsername());
        holder.experienceTextView.setText("تجربه: " + friend.getExperience());
        if (friend.getAvatar() != null && !friend.getAvatar().isEmpty()) {
            Glide.with(holder.itemView.getContext())
                    .load(friend.getAvatar())
                    .into(holder.avatarImageView);
        } else {
            holder.avatarImageView.setImageResource(R.drawable.user_icon);
        }
        // تنظیم کلیک دکمه چت
        holder.chatButton.setOnClickListener(v -> {
            Log.d("FriendsAdapter", "Chat button clicked for user: " + friend.getUsername());
            // دریافت currentUserId از SharedPreferences
            SharedPreferences prefs = holder.itemView.getContext().getSharedPreferences("auth", holder.itemView.getContext().MODE_PRIVATE);
            String currentUserId = prefs.getString("userId", null);
            if (currentUserId == null) {
                Log.e("FriendsAdapter", "currentUserId is null, user might not be logged in");
                return;
            }
            Intent intent = new Intent(holder.itemView.getContext(), ChatActivity.class);
            intent.putExtra("userId", friend.getUserId());
            intent.putExtra("player", friend);
            intent.putExtra("currentUserId", currentUserId);
            holder.itemView.getContext().startActivity(intent);
        });
    }

    @Override
    public int getItemCount() {
        return friends.size();
    }

    public void updateFriends(List<Player> newFriends) {
        this.friends.clear();
        this.friends.addAll(newFriends);
        notifyDataSetChanged();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView avatarImageView;
        TextView usernameTextView;
        TextView experienceTextView;
        Button chatButton;

        ViewHolder(View itemView) {
            super(itemView);
            avatarImageView = itemView.findViewById(R.id.avatarImageView);
            usernameTextView = itemView.findViewById(R.id.usernameTextView);
            experienceTextView = itemView.findViewById(R.id.experienceTextView);
            chatButton = itemView.findViewById(R.id.chatButton);
        }
    }
}