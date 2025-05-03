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
import com.example.a4Barg.model.FriendRequest;

import java.util.List;
import java.util.function.Consumer;

public class RequestsAdapter extends RecyclerView.Adapter<RequestsAdapter.ViewHolder> {

    private List<FriendRequest> requests;
    private Consumer<String> onAcceptClick;
    private Consumer<String> onRejectClick;

    public RequestsAdapter(List<FriendRequest> requests, Consumer<String> onAcceptClick, Consumer<String> onRejectClick) {
        this.requests = requests;
        this.onAcceptClick = onAcceptClick;
        this.onRejectClick = onRejectClick;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_friend_request, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        FriendRequest request = requests.get(position);
        holder.usernameTextView.setText(request.getFromUsername());
        if (request.getFromAvatar() != null && !request.getFromAvatar().isEmpty()) {
            Glide.with(holder.itemView.getContext())
                    .load(request.getFromAvatar())
                    .into(holder.avatarImageView);
        } else {
            holder.avatarImageView.setImageResource(R.drawable.user_icon);
        }
        holder.acceptButton.setOnClickListener(v -> onAcceptClick.accept(request.getRequestId()));
        holder.rejectButton.setOnClickListener(v -> onRejectClick.accept(request.getRequestId()));
    }

    @Override
    public int getItemCount() {
        return requests.size();
    }

    public void updateRequests(List<FriendRequest> newRequests) {
        this.requests.clear();
        this.requests.addAll(newRequests);
        notifyDataSetChanged();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView avatarImageView;
        TextView usernameTextView;
        Button acceptButton;
        Button rejectButton;

        ViewHolder(View itemView) {
            super(itemView);
            avatarImageView = itemView.findViewById(R.id.avatarImageView);
            usernameTextView = itemView.findViewById(R.id.usernameTextView);
            acceptButton = itemView.findViewById(R.id.acceptButton);
            rejectButton = itemView.findViewById(R.id.rejectButton);
        }
    }
}