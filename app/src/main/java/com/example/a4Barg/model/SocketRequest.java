package com.example.a4Barg.model;

import android.app.Activity;

import com.example.a4Barg.networking.SocketManager;

import org.json.JSONObject;

public class SocketRequest {
    Activity activity;
    JSONObject jsonObject;
    SocketManager.Response response;

    public SocketRequest(Activity activity, JSONObject jsonObject, SocketManager.Response response) {
        this.activity = activity;
        this.jsonObject = jsonObject;
        this.response = response;
    }

    public void setActivity(Activity activity) {
        this.activity = activity;
    }

    public void setJsonObject(JSONObject jsonObject) {
        this.jsonObject = jsonObject;
    }

    public void setResponse(SocketManager.Response response) {
        this.response = response;
    }

    public Activity requireActivity() {
        return activity;
    }

    public JSONObject getJsonObject() {
        return jsonObject;
    }

    public SocketManager.Response getResponse() {
        return response;
    }
}