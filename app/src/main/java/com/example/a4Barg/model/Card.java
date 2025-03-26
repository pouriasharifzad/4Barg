package com.example.a4Barg.model;

public class Card {
    private String suit;
    private String rank;

    public Card(String suit, String rank) {
        this.suit = suit;
        this.rank = rank;
    }

    public String getSuit() {
        return suit;
    }

    public void setSuit(String suit) {
        this.suit = suit;
    }

    public String getRank() {
        return rank;
    }

    public void setRank(String rank) {
        this.rank = rank;
    }

    public String getImageResourceName() {
        // فرض می‌کنیم تصاویر کارت‌ها در drawable به‌صورت card_suit_rank ذخیره شدن
        // مثلاً: card_hearts_ace، card_spades_7 و غیره
        return  suit.toLowerCase() + "_" + rank.toLowerCase();
    }

    @Override
    public String toString() {
        return rank + " of " + suit;
    }
}