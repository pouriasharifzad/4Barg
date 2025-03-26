package com.example.a4Barg.scene.room;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.a4Barg.R;
import com.example.a4Barg.model.Room;

import java.util.List;

public class RoomAdapter extends RecyclerView.Adapter<RoomAdapter.RoomViewHolder> {

    private List<Room> roomList;
    private OnRoomClickListener onRoomClickListener; // برای کلیک روی روم

    // Listener interface برای کلیک
    public interface OnRoomClickListener {
        void onRoomClick(Room room);
    }

    public RoomAdapter(List<Room> roomList, OnRoomClickListener onRoomClickListener) {
        this.roomList = roomList;
        this.onRoomClickListener = onRoomClickListener;
    }

    @NonNull
    @Override
    public RoomViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_room, parent, false);
        return new RoomViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RoomViewHolder holder, int position) {
        Room room = roomList.get(position);
        holder.roomNumberTextView.setText(room.getRoomNumber());
        holder.playerCountTextView.setText(room.getCurrentPlayers() + "/" + room.getMaxPlayers());

        // تنظیم کلیک روی روم
        holder.itemView.setOnClickListener(v -> {
            if (onRoomClickListener != null) {
                onRoomClickListener.onRoomClick(room);
            }
        });
    }

    @Override
    public int getItemCount() {
        return roomList != null ? roomList.size() : 0;
    }

    // متد برای آپدیت لیست روم‌ها
    public void updateRooms(List<Room> newRoomList) {
        this.roomList = newRoomList;
        notifyDataSetChanged();
    }

    public static class RoomViewHolder extends RecyclerView.ViewHolder {
        TextView roomNumberTextView, playerCountTextView;

        public RoomViewHolder(@NonNull View itemView) {
            super(itemView);
            roomNumberTextView = itemView.findViewById(R.id.roomNumberTextView);
            playerCountTextView = itemView.findViewById(R.id.playerCountTextView);
        }
    }
}