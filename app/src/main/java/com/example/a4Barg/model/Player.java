package com.example.a4Barg.model;

import java.io.Serializable;

public class Player implements Serializable {
    private String userId;
    private String username;
    private int cardCount;
    private int experience;
    private int coins;
    private String avatar;

    public Player(String userId, String username, int cardCount, int experience, int coins) {
        this.userId = userId;
        this.username = username;
        this.cardCount = cardCount;
        this.experience = experience;
        this.coins = coins;
    }

    public Player(String userId, String username) {
        this.userId = userId;
        this.username = username;
    }

    public Player(String userId, String username, int experience, String avatar) {
        this.userId = userId;
        this.username = username;
        this.experience = experience;
        this.avatar = avatar;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public int getCardCount() {
        return cardCount;
    }

    public void setCardCount(int cardCount) {
        this.cardCount = cardCount;
    }

    public int getExperience() {
        return experience;
    }

    public void setExperience(int experience) {
        this.experience = experience;
    }

    public int getCoins() {
        return coins;
    }

    public void setCoins(int coins) {
        this.coins = coins;
    }

    public String getAvatar() {
        return avatar;
    }

    public void setAvatar(String avatar) {
        this.avatar = avatar;
    }
}