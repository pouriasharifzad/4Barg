package com.example.a4Barg.utils;

import android.content.Context;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.annotation.NonNull;

import com.example.a4Barg.R;
import com.example.a4Barg.model.Card;

import java.util.ArrayList;
import java.util.List;

public class TableView extends FrameLayout {
    private List<ImageView> cardViews = new ArrayList<>();
    private List<Card> cards = new ArrayList<>();
    private final int padding = 10;
    private boolean isSelectable = false;
    private List<Card> selectedCards = new ArrayList<>();
    private OnCardSelectedListener onCardSelectedListener;
    private float[] lastCardPosition = new float[]{0f, 0f}; // متغیر برای ذخیره مختصات آخرین کارت

    public TableView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void setCards(@NonNull List<Card> newCards) {
        removeAllViews();
        cardViews.clear();
        cards.clear();
        selectedCards.clear();

        DisplayMetrics displayMetrics = getContext().getResources().getDisplayMetrics();
        int screenWidth = getWidth() > 0 ? getWidth() : displayMetrics.widthPixels;

        int cardsPerRow = 4;
        int availableWidth = screenWidth - (padding * (cardsPerRow + 1));
        int cardWidth = availableWidth / cardsPerRow;
        int cardHeightPx = (int) (cardWidth * 1.5);

        for (Card card : newCards) {
            ImageView cardView = new ImageView(getContext());
            cardView.setLayoutParams(new LayoutParams(cardWidth, cardHeightPx));
            int resId = getResources().getIdentifier(card.getImageResourceName(), "drawable", getContext().getPackageName());
            cardView.setImageResource(resId != 0 ? resId : R.drawable.card_back);
            if (isSelectable) {
                cardView.setOnClickListener(v -> {
                    if (onCardSelectedListener != null) {
                        onCardSelectedListener.onCardSelected(card);
                    }
                });
            }
            cardViews.add(cardView);
            cards.add(card);
            addView(cardView);
        }
        requestLayout();
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int count = cardViews.size();
        if (count == 0) {
            lastCardPosition = new float[]{0f, 0f}; // اگر هیچ کارتی نباشه، مختصات صفر می‌مونه
            return;
        }

        int screenWidth = getWidth();
        int screenHeight = getHeight();

        int cardsPerRow = 4;
        int horizontalSpacing = padding;
        int verticalSpacing = padding;

        int availableWidth = screenWidth - (padding * (cardsPerRow + 1));
        int cardWidth = availableWidth / cardsPerRow;
        int cardHeightPx = (int) (cardWidth * 1.5);

        for (ImageView cardView : cardViews) {
            ViewGroup.LayoutParams params = cardView.getLayoutParams();
            params.width = cardWidth;
            params.height = cardHeightPx;
            cardView.setLayoutParams(params);
        }

        int rows = (int) Math.ceil((double) count / cardsPerRow);
        int requiredHeight = (rows * (cardHeightPx + verticalSpacing)) + padding;
        if (getMinimumHeight() < requiredHeight) {
            setMinimumHeight(requiredHeight);
        }

        int startX = padding;
        int startY = padding;

        for (int i = 0; i < count; i++) {
            ImageView card = cardViews.get(i);
            int row = i / cardsPerRow;
            int column = i % cardsPerRow;
            int left = startX + (column * (cardWidth + horizontalSpacing));
            int top = startY + (row * (cardHeightPx + verticalSpacing));
            card.layout(left, top, left + cardWidth, top + cardHeightPx);
            card.setZ(selectedCards.contains(cards.get(i)) ? 1 : 0);
            card.setAlpha(selectedCards.contains(cards.get(i)) ? 0.7f : 1.0f);
        }

        // محاسبه موقعیت کارت بعدی (n+1)
        int nextIndex = count; // اندیس کارت بعدی
        int nextRow = nextIndex / cardsPerRow;
        int nextColumn = nextIndex % cardsPerRow;
        lastCardPosition[0] = startX + (nextColumn * (cardWidth + horizontalSpacing));
        lastCardPosition[1] = startY + (nextRow * (cardHeightPx + verticalSpacing));
    }

    public List<Card> getCards() {
        return cards;
    }

    public void setSelectable(boolean selectable) {
        this.isSelectable = selectable;
        for (ImageView cardView : cardViews) {
            cardView.setOnClickListener(selectable && onCardSelectedListener != null ?
                    v -> onCardSelectedListener.onCardSelected(cards.get(cardViews.indexOf(cardView))) : null);
        }
        requestLayout();
    }

    public void setOnCardSelectedListener(OnCardSelectedListener listener) {
        this.onCardSelectedListener = listener;
    }

    public void updateSelection(List<Card> selected) {
        this.selectedCards = new ArrayList<>(selected);
        requestLayout();
    }

    public void clearSelection() {
        selectedCards.clear();
        requestLayout();
    }

    public float[] getLastCardPosition() {
        return lastCardPosition; // برگرداندن مختصات کارت بعدی
    }

    public interface OnCardSelectedListener {
        void onCardSelected(Card card);
    }
}