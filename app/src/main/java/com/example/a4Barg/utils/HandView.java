package com.example.a4Barg.utils;

import android.content.Context;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;

import com.example.a4Barg.R;
import com.example.a4Barg.model.Card;

import java.util.ArrayList;
import java.util.List;

public class HandView extends FrameLayout {
    private List<ImageView> cards = new ArrayList<>();
    private List<Card> cardModels = new ArrayList<>();
    private boolean showCards = true; // برای کنترل نمایش رو یا پشت کارت‌ها

    private final int overlap = 60;
    private final int angleFactor = 12;
    private final int cardHeight = 180;
    private final int padding = 10;
    private final int curveFactor = 65;

    public HandView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    // تنظیم لیست کارت‌ها
    public void setCards(List<Card> newCards) {
        removeAllViews();
        cards.clear();
        cardModels.clear();

        DisplayMetrics displayMetrics = getContext().getResources().getDisplayMetrics();
        int cardWidth = (int) (120 * displayMetrics.density);
        int cardHeightPx = (int) (cardHeight * displayMetrics.density);

        for (Card card : newCards) {
            ImageView cardView = new ImageView(getContext());
            cardView.setLayoutParams(new LayoutParams(cardWidth, cardHeightPx));
            if (showCards) {
                int resId = getResources().getIdentifier(card.getImageResourceName(), "drawable", getContext().getPackageName());
                cardView.setImageResource(resId != 0 ? resId : R.drawable.card_back);
            } else {
                cardView.setImageResource(R.drawable.card_back); // پشت کارت برای حریف
            }
            cards.add(cardView);
            cardModels.add(card);
            addView(cardView);
        }
        requestLayout();
    }

    // تنظیم اینکه کارت‌ها رو نشون بده یا پشت باشه
    public void setShowCards(boolean show) {
        this.showCards = show;
        updateCardViews();
    }

    private void updateCardViews() {
        for (int i = 0; i < cards.size(); i++) {
            ImageView cardView = cards.get(i);
            if (showCards && i < cardModels.size()) {
                Card card = cardModels.get(i);
                int resId = getResources().getIdentifier(card.getImageResourceName(), "drawable", getContext().getPackageName());
                cardView.setImageResource(resId != 0 ? resId : R.drawable.card_back);
            } else {
                cardView.setImageResource(R.drawable.card_back);
            }
        }
    }

    public void addCard(Card card) {
        ImageView cardView = new ImageView(getContext());
        DisplayMetrics displayMetrics = getContext().getResources().getDisplayMetrics();

        int cardWidth = (int) (120 * displayMetrics.density);
        int cardHeightPx = (int) (cardHeight * displayMetrics.density);

        cardView.setLayoutParams(new LayoutParams(cardWidth, cardHeightPx));
        if (showCards) {
            int resId = getResources().getIdentifier(card.getImageResourceName(), "drawable", getContext().getPackageName());
            cardView.setImageResource(resId != 0 ? resId : R.drawable.card_back);
        } else {
            cardView.setImageResource(R.drawable.card_back);
        }

        cards.add(cardView);
        cardModels.add(card);
        addView(cardView);
        requestLayout();
    }

    public void removeLastCard() {
        if (!cards.isEmpty()) {
            removeView(cards.get(cards.size() - 1));
            cards.remove(cards.size() - 1);
            cardModels.remove(cardModels.size() - 1);
            requestLayout();
        }
    }

    public int getCardCount() {
        return cards.size();
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int count = cards.size();
        if (count == 0) return;

        DisplayMetrics displayMetrics = getContext().getResources().getDisplayMetrics();
        int cardWidth = (int) (120 * displayMetrics.density);
        int cardHeightPx = (int) (cardHeight * displayMetrics.density);
        int screenWidth = getWidth();

        int maxAllowedWidth = screenWidth - (2 * padding);
        int totalWidth = (count - 1) * overlap + cardWidth;

        if (totalWidth > maxAllowedWidth) {
            float reductionFactor = (float) maxAllowedWidth / totalWidth;
            totalWidth = (count - 1) * (int) (overlap * reductionFactor) + cardWidth;
        }

        int startX = (screenWidth - totalWidth) / 2;
        int baseY = getHeight() / 2 - (cardHeightPx / 2);

        for (int i = 0; i < count; i++) {
            ImageView card = cards.get(i);
            int left = startX + (i * overlap);
            int angle = (i - count / 2) * angleFactor;

            double normalizedPosition = (i - (count - 1) / 2.0) / ((count - 1) / 2.0);
            int adjustedY = (int) (baseY - curveFactor * Math.cos(Math.toRadians(normalizedPosition * 90)));

            card.layout(left, adjustedY, left + cardWidth, adjustedY + cardHeightPx);
            card.setRotation(angle);
        }
    }
}