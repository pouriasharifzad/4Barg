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
    private boolean isSelectable = false; // جدید
    private List<Card> selectedCards = new ArrayList<>(); // جدید
    private OnCardSelectedListener onCardSelectedListener; // جدید

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
        if (count == 0) return;

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
            card.setZ(selectedCards.contains(cards.get(i)) ? 1 : 0); // کارت‌های انتخاب‌شده بالاتر نمایش داده می‌شن
            card.setAlpha(selectedCards.contains(cards.get(i)) ? 0.7f : 1.0f); // کارت‌های انتخاب‌شده کمی شفاف‌تر
        }
    }

    public List<Card> getCards() {
        return cards;
    }

    // جدید: تنظیم حالت انتخاب‌پذیری
    public void setSelectable(boolean selectable) {
        this.isSelectable = selectable;
        for (ImageView cardView : cardViews) {
            cardView.setOnClickListener(selectable && onCardSelectedListener != null ?
                    v -> onCardSelectedListener.onCardSelected(cards.get(cardViews.indexOf(cardView))) : null);
        }
        requestLayout();
    }

    // جدید: تنظیم لیسنر برای انتخاب کارت
    public void setOnCardSelectedListener(OnCardSelectedListener listener) {
        this.onCardSelectedListener = listener;
    }

    // جدید: آپدیت وضعیت انتخاب
    public void updateSelection(List<Card> selected) {
        this.selectedCards = new ArrayList<>(selected);
        requestLayout();
    }

    // جدید: پاک کردن انتخاب‌ها
    public void clearSelection() {
        selectedCards.clear();
        requestLayout();
    }

    // جدید: اینترفیس برای انتخاب کارت
    public interface OnCardSelectedListener {
        void onCardSelected(Card card);
    }
}