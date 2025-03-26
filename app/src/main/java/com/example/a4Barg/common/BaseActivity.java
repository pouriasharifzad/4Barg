package com.example.a4Barg.common;

import android.app.Dialog;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.a4Barg.scene.room.RoomActivity;

import io.socket.client.Socket;


public class BaseActivity extends AppCompatActivity implements BaseActivityViewModelDelegate {
    Dialog dialog;
    Socket socket;
    IntentFilter intentFilter;
    public static Intent mReceiverRegisteringIntent;
    static boolean active = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        active = true;
    }


    @Override
    public void startIntent(Intent intent) {
        startActivity(intent);
    }


    @Override
    public void finishActivity() {
        finish();
    }

    @Override
    public void showToast(String message) {
        runOnUiThread(() -> {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        });
    }




    @Override
    public void showLoading() {
        if (!isFinishing()) {
            dialog.getWindow().setFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);

            dialog.show();
            //Set the dialog to immersive
            dialog.getWindow().getDecorView().setSystemUiVisibility(
                    getWindow().getDecorView().getSystemUiVisibility());

//Clear the not focusable flag from the window
            dialog.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
        }
    }

    @Override
    public void hideBottomNavigation() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    @Override
    public void showLoading(Socket socket) {

    }

    @Override
    public boolean checkConnection() {
        return false;
    }


    @Override
    public void hideLoading() {
        runOnUiThread(() -> dialog.hide());
    }


    @Override
    public void recreateActivity() {
        recreate();
    }

    @Override
    protected void onStart() {
        super.onStart();
        active = true;
    }

    @Override
    protected void onStop() {
        super.onStop();
        active = false;

    }

    @Override
    protected void onPause() {
        super.onPause();
        active = false;
    }


}







