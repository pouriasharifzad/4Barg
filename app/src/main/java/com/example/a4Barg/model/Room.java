package com.example.a4Barg.model;

public class Room {
    private String roomNumber;
    private int minExperience;
    private int minCoins;
    private int maxPlayers;
    private int currentPlayers;

    public Room(String roomNumber, int minExperience, int minCoins, int maxPlayers, int currentPlayers) {
        this.roomNumber = roomNumber;
        this.minExperience = minExperience;
        this.minCoins = minCoins;
        this.maxPlayers = maxPlayers;
        this.currentPlayers = currentPlayers;
    }

    // Getters and Setters
    public String getRoomNumber() { return roomNumber; }
    public void setRoomNumber(String roomNumber) { this.roomNumber = roomNumber; }
    public int getMinExperience() { return minExperience; }
    public void setMinExperience(int minExperience) { this.minExperience = minExperience; }
    public int getMinCoins() { return minCoins; }
    public void setMinCoins(int minCoins) { this.minCoins = minCoins; }
    public int getMaxPlayers() { return maxPlayers; }
    public void setMaxPlayers(int maxPlayers) { this.maxPlayers = maxPlayers; }
    public int getCurrentPlayers() { return currentPlayers; }
    public void setCurrentPlayers(int currentPlayers) { this.currentPlayers = currentPlayers; }
}