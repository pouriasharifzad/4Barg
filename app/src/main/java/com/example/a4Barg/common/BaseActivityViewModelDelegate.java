package com.example.a4Barg.common;

import android.content.Intent;

import io.socket.client.Socket;



public interface BaseActivityViewModelDelegate {
    void startIntent(Intent intent);

    void finishActivity();

    void showToast(String message);

    void showLoading();

    void hideBottomNavigation();

    void showLoading(Socket socket);

    boolean checkConnection();


    void hideLoading();

    void recreateActivity();
}
