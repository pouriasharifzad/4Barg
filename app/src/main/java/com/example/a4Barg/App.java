package com.example.a4Barg;

import android.app.Application;
import android.util.Log;

import com.example.a4Barg.networking.SocketManager;

import java.net.URISyntaxException;

import io.socket.client.IO;
import io.socket.client.Socket;

public class App extends Application {
    public static App shared;
    private Socket socket;

    @Override
    public void onCreate() {
        super.onCreate();
        shared = this; // مقداردهی اولیه singleton
        initializeSocket(); // مقداردهی اولیه سوکت
        SocketManager.initializeGlobalListeners();
    }

    private void initializeSocket() {
        try {
            socket = IO.socket("http://85.9.97.109:3000");
            socket.on(Socket.EVENT_CONNECT, args -> {
                Log.d("TEST", "Socket connected on init");
            });
            socket.on(Socket.EVENT_DISCONNECT, args -> {
                Log.d("TEST", "Socket disconnected on init: " + (args.length > 0 ? args[0].toString() : "unknown"));
            });
            socket.on(Socket.EVENT_CONNECT_ERROR, args -> {
                Log.d("TEST", "Socket connect error on init: " + (args.length > 0 ? args[0].toString() : "unknown"));
            });
            socket.connect();
            Log.d("TEST", "Socket initialization attempted - socket.connected(): " + socket.connected());
        } catch (URISyntaxException e) {
            Log.e("TEST", "Socket URI error: " + e.getMessage());
        }
    }

    public Socket getSocket() {
        return socket;
    }
}