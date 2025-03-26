package com.example.a4Barg.model;


import java.util.List;

public class GameState {
    private String roomNumber;
    private List<Player> players;
    private String currentPlayerId;
    private Card currentCard;
    private String currentSuit;
    private int penaltyCount;
    private int direction;

    public GameState(String roomNumber, List<Player> players, String currentPlayerId, Card currentCard,
                     String currentSuit, int penaltyCount, int direction) {
        this.roomNumber = roomNumber;
        this.players = players;
        this.currentPlayerId = currentPlayerId;
        this.currentCard = currentCard;
        this.currentSuit = currentSuit;
        this.penaltyCount = penaltyCount;
        this.direction = direction;
    }

    // Getters and Setters
    public String getRoomNumber() {
        return roomNumber;
    }

    public void setRoomNumber(String roomNumber) {
        this.roomNumber = roomNumber;
    }

    public List<Player> getPlayers() {
        return players;
    }

    public void setPlayers(List<Player> players) {
        this.players = players;
    }

    public String getCurrentPlayerId() {
        return currentPlayerId;
    }

    public void setCurrentPlayerId(String currentPlayerId) {
        this.currentPlayerId = currentPlayerId;
    }

    public Card getCurrentCard() {
        return currentCard;
    }

    public void setCurrentCard(Card currentCard) {
        this.currentCard = currentCard;
    }

    public String getCurrentSuit() {
        return currentSuit;
    }

    public void setCurrentSuit(String currentSuit) {
        this.currentSuit = currentSuit;
    }

    public int getPenaltyCount() {
        return penaltyCount;
    }

    public void setPenaltyCount(int penaltyCount) {
        this.penaltyCount = penaltyCount;
    }

    public int getDirection() {
        return direction;
    }

    public void setDirection(int direction) {
        this.direction = direction;
    }
}