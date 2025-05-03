package com.example.a4Barg.scene.friends;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.a4Barg.R;
import com.example.a4Barg.model.Player;

import java.util.List;
import java.util.function.Consumer;

public class SearchAdapter extends RecyclerView.Adapter<SearchAdapter.ViewHolder> {

    private List<Player> users;
    private Consumer<String> onAddFriendClick;

    public SearchAdapter(List<Player> users, Consumer<String> onAddFriendClick) {
        this.users = users;
        this.onAddFriendClick = onAddFriendClick;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_search_result, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Player user = users.get(position);
        holder.usernameTextView.setText(user.getUsername());
        holder.experienceTextView.setText("تجربه: " + user.getExperience());
        if (user.getAvatar() != null && !user.getAvatar().isEmpty()) {
            Glide.with(holder.itemView.getContext())
                    .load(user.getAvatar())
                    .into(holder.avatarImageView);
        } else {
            holder.avatarImageView.setImageResource(R.drawable.user_icon);
        }
        holder.addFriendButton.setOnClickListener(v -> onAddFriendClick.accept(user.getUserId()));
    }

    @Override
    public int getItemCount() {
        return users.size();
    }

    public void updateUsers(List<Player> newUsers) {
        this.users.clear();
        this.users.addAll(newUsers);
        notifyDataSetChanged();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView avatarImageView;
        TextView usernameTextView;
        TextView experienceTextView;
        Button addFriendButton;

        ViewHolder(View itemView) {
            super(itemView);
            avatarImageView = itemView.findViewById(R.id.avatarImageView);
            usernameTextView = itemView.findViewById(R.id.usernameTextView);
            experienceTextView = itemView.findViewById(R.id.experienceTextView);
            addFriendButton = itemView.findViewById(R.id.addFriendButton);
        }
    }
}