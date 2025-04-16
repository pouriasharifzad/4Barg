package com.example.a4Barg.model;

public class InGameMessage {
    private String userId;
    private String message;

    public InGameMessage(String userId, String message) {
        this.userId = userId;
        this.message = message;
    }

    public String getUserId() {
        return userId;
    }

    public String getMessage() {
        return message;
    }
}