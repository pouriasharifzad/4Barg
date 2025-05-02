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
import com.example.a4Barg.networking.SocketManager;
import com.example.a4Barg.scene.room.RoomAdapter;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import io.socket.emitter.Emitter;

public class RoomListActivity extends BaseActivity {

    private RoomListViewModel viewModel;
    private RecyclerView roomRecyclerView;
    private RoomAdapter roomAdapter;
    private SocketManager.RoomListUpdateListener roomListUpdateListener;
    private Emitter.Listener roomListEmitterListener; // متغیر جدید برای ذخیره Emitter.Listener

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_room_list);

        viewModel = new ViewModelProvider(this).get(RoomListViewModel.class);
        String userId = getIntent().getStringExtra("userId");
        viewModel.setUserId(userId);
        viewModel.requestRoomList(); // درخواست اولیه لیست روم‌ها

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

        // تنظیم لیسنر room_list_update
        roomListUpdateListener = new SocketManager.RoomListUpdateListener() {
            @Override
            public void onRoomListUpdate(JSONObject roomListData) {
                try {
                    JSONArray roomsArray = roomListData.getJSONArray("rooms");
                    List<Room> rooms = new ArrayList<>();
                    for (int i = 0; i < roomsArray.length(); i++) {
                        JSONObject roomObj = roomsArray.getJSONObject(i);
                        Room room = new Room(
                                roomObj.getString("roomNumber"),
                                roomObj.getInt("minExperience"),
                                roomObj.getInt("minCoins"),
                                roomObj.getInt("maxPlayers"),
                                roomObj.getInt("currentPlayers")
                        );
                        rooms.add(room);
                    }
                    runOnUiThread(() -> roomAdapter.updateRooms(rooms));
                } catch (JSONException e) {
                    Toast.makeText(RoomListActivity.this, "خطا در پردازش لیست روم‌ها", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onRoomListError(Throwable t) {
                Toast.makeText(RoomListActivity.this, "خطا در دریافت آپدیت لیست روم‌ها: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        };
        roomListEmitterListener = SocketManager.listenForRoomListUpdates(roomListUpdateListener); // ذخیره Emitter.Listener
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // حذف لیسنر room_list_update با استفاده از Emitter.Listener
        if (roomListEmitterListener != null) {
            SocketManager.getSocket().off("room_list_update", roomListEmitterListener);
        }
    }
}