package com.example.a4Barg.utils;

import android.content.Context;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.widget.FrameLayout;
import android.widget.ImageView;

import com.example.a4Barg.R;
import com.example.a4Barg.model.Card;

import java.util.ArrayList;
import java.util.List;

public class CollectedCardsView extends FrameLayout {
    private List<Card> cards = new ArrayList<>();
    private ImageView stackView;
    private final int cardHeight = 80;
    private final int padding = 10;

    public CollectedCardsView(Context context, AttributeSet attrs) {
        super(context, attrs);
        stackView = new ImageView(context);
        DisplayMetrics displayMetrics = getContext().getResources().getDisplayMetrics();
        int cardWidth = (int) (120 * displayMetrics.density);
        int cardHeightPx = (int) (cardHeight * displayMetrics.density);
        stackView.setLayoutParams(new LayoutParams(cardWidth, cardHeightPx));
        stackView.setImageResource(R.drawable.card_back);
        addView(stackView);
    }

    public void setCards(List<Card> newCards) {
        cards.clear();
        cards.addAll(newCards);
        stackView.setVisibility(cards.isEmpty() ? GONE : VISIBLE);
        requestLayout();
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        if (stackView.getVisibility() == VISIBLE) {
            DisplayMetrics displayMetrics = getContext().getResources().getDisplayMetrics();
            int cardWidth = (int) (120 * displayMetrics.density);
            int cardHeightPx = (int) (cardHeight * displayMetrics.density);
            int left = (getWidth() - cardWidth) / 2;
            int top = getHeight() - cardHeightPx - padding;
            stackView.layout(left, top, left + cardWidth, top + cardHeightPx);
        }
    }

    public int getCardCount() {
        return cards.size();
    }
}