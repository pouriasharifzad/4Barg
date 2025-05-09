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
        return suit.toLowerCase() + "_" + rank.toLowerCase();
    }

    @Override
    public String toString() {
        return rank + " of " + suit;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Card card = (Card) o;
        return suit != null && suit.equals(card.suit) && rank != null && rank.equals(card.rank);
    }

    @Override
    public int hashCode() {
        int result = suit != null ? suit.hashCode() : 0;
        result = 31 * result + (rank != null ? rank.hashCode() : 0);
        return result;
    }
}