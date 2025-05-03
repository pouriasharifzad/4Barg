package com.example.a4Barg.model;

public class Message {
    private String sender;
    private String receiver;
    private String message;
    private String timestamp;

    public Message(String sender, String receiver, String message, String timestamp) {
        this.sender = sender;
        this.receiver = receiver;
        this.message = message;
        this.timestamp = timestamp;
    }

    public String getSender() {
        return sender;
    }

    public String getReceiver() {
        return receiver;
    }

    public String getMessage() {
        return message;
    }

    public String getTimestamp() {
        return timestamp;
    }
}