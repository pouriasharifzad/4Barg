package com.example.a4Barg.model;

public class FriendRequest {
    private String requestId;
    private String fromUserId;
    private String fromUsername;
    private String fromAvatar;

    public FriendRequest(String requestId, String fromUserId, String fromUsername, String fromAvatar) {
        this.requestId = requestId;
        this.fromUserId = fromUserId;
        this.fromUsername = fromUsername;
        this.fromAvatar = fromAvatar;
    }

    public String getRequestId() {
        return requestId;
    }

    public String getFromUserId() {
        return fromUserId;
    }

    public String getFromUsername() {
        return fromUsername;
    }

    public String getFromAvatar() {
        return fromAvatar;
    }
}