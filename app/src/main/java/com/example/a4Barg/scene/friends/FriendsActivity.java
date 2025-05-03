package com.example.a4Barg.scene.friends;

import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.a4Barg.R;
import com.example.a4Barg.model.FriendRequest;
import com.example.a4Barg.model.Player;
import com.example.a4Barg.networking.SocketManager;

import java.util.ArrayList;

public class FriendsActivity extends AppCompatActivity {

    private FriendsViewModel viewModel;
    private RecyclerView searchRecyclerView;
    private RecyclerView requestsRecyclerView;
    private RecyclerView friendsRecyclerView;
    private EditText searchEditText;
    private Button searchButton;
    private SearchAdapter searchAdapter;
    private RequestsAdapter requestsAdapter;
    private FriendsAdapter friendsAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_friends);
        Log.d("FriendsActivity2", "FriendsActivity started");

        viewModel = new ViewModelProvider(this).get(FriendsViewModel.class);
        String userId = getIntent().getStringExtra("userId");
        Log.d("FriendsActivity2", "Received userId from Intent: " + userId);
        viewModel.setUserId(userId);

        searchEditText = findViewById(R.id.searchEditText);
        searchButton = findViewById(R.id.searchButton);
        searchRecyclerView = findViewById(R.id.searchRecyclerView);
        requestsRecyclerView = findViewById(R.id.requestsRecyclerView);
        friendsRecyclerView = findViewById(R.id.friendsRecyclerView);

        searchRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        requestsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        friendsRecyclerView.setLayoutManager(new LinearLayoutManager(this));

        searchAdapter = new SearchAdapter(new ArrayList<>(), viewModel::sendFriendRequest);
        requestsAdapter = new RequestsAdapter(new ArrayList<>(), viewModel::acceptFriendRequest, viewModel::rejectFriendRequest);
        friendsAdapter = new FriendsAdapter(new ArrayList<>());

        searchRecyclerView.setAdapter(searchAdapter);
        requestsRecyclerView.setAdapter(requestsAdapter);
        friendsRecyclerView.setAdapter(friendsAdapter);

        viewModel.getSearchResults().observe(this, users -> searchAdapter.updateUsers(users));
        viewModel.getFriendRequests().observe(this, requests -> requestsAdapter.updateRequests(requests));
        viewModel.getFriends().observe(this, friends -> friendsAdapter.updateFriends(friends));

        searchButton.setOnClickListener(v -> {
            String query = searchEditText.getText().toString();
            Log.d("FriendsActivity2", "Search button clicked with query: " + query);
            viewModel.searchUsers(query);
        });

        viewModel.getErrorMessage().observe(this, msg -> {
            if (msg != null) {
                Log.d("FriendsActivity2", "Error message received: " + msg);
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
            }
        });

        viewModel.loadFriendRequests();
        viewModel.loadFriends();
    }
}