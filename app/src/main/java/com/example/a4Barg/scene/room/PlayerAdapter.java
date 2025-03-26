package com.example.a4Barg.scene.room;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.a4Barg.R;
import com.example.a4Barg.model.Player;

import java.util.List;

public class PlayerAdapter extends RecyclerView.Adapter<PlayerAdapter.PlayerViewHolder> {

    private List<Player> playerList;

    public PlayerAdapter(List<Player> playerList) {
        this.playerList = playerList;
    }

    @NonNull
    @Override
    public PlayerViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_player, parent, false);
        return new PlayerViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PlayerViewHolder holder, int position) {
        Player player = playerList.get(position);
        holder.usernameTextView.setText(player.getUsername());
    }

    @Override
    public int getItemCount() {
        return playerList != null ? playerList.size() : 0;
    }

    // متد برای آپدیت لیست بازیکن‌ها
    public void updatePlayers(List<Player> newPlayerList) {
        this.playerList = newPlayerList;
        notifyDataSetChanged();
    }

    public static class PlayerViewHolder extends RecyclerView.ViewHolder {
        TextView usernameTextView;

        public PlayerViewHolder(@NonNull View itemView) {
            super(itemView);
            usernameTextView = itemView.findViewById(R.id.usernameTextView);
        }
    }
}