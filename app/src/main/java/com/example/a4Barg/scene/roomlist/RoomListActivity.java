package com.example.a4Barg.scene.roomlist;

import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.a4Barg.R;
import com.example.a4Barg.common.BaseActivity;
import com.example.a4Barg.model.Room;
import com.example.a4Barg.scene.room.RoomAdapter;

import java.util.ArrayList;
import java.util.List;

public class RoomListActivity extends BaseActivity {

    private RoomListViewModel viewModel;
    private RecyclerView roomRecyclerView;
    private RoomAdapter roomAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_room_list);

        viewModel = new ViewModelProvider(this).get(RoomListViewModel.class);
        String userId = getIntent().getStringExtra("userId");
        viewModel.setUserId(userId);
        viewModel.requestRoomList(); // درخواست آپدیت لیست روم‌ها

        roomRecyclerView = findViewById(R.id.roomRecyclerView);
        roomRecyclerView.setLayoutManager(new LinearLayoutManager(this));

        roomAdapter = new RoomAdapter(new ArrayList<>(), room -> {
            viewModel.joinRoom(room.getRoomNumber());
        });
        roomRecyclerView.setAdapter(roomAdapter);

        viewModel.getRoomList().observe(this, rooms -> {
            roomAdapter.updateRooms(rooms);
        });

        viewModel.getErrorMessage().observe(this, msg -> {
            if (msg != null) {
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
            }
        });
    }
}