package com.example.a4Barg.utils;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.animation.ObjectAnimator;

import androidx.constraintlayout.widget.ConstraintLayout;

import com.example.a4Barg.R;
import com.example.a4Barg.model.Card;

import java.util.ArrayList;
import java.util.List;

public class CardContainerView extends ConstraintLayout {
    public enum Type {
        HAND, TABLE
    }

    private Type type;
    private List<ImageView> cardViews = new ArrayList<>();
    private List<Card> cards = new ArrayList<>();
    private boolean showCards = true; // فقط برای HAND
    private boolean enabled = true; // فقط برای HAND
    private boolean isSelectable = false; // فقط برای TABLE
    private List<Card> selectedCards = new ArrayList<>(); // فقط برای TABLE
    private OnCardPlayedListener cardPlayedListener; // فقط برای HAND
    private OnCardSelectedListener onCardSelectedListener; // فقط برای TABLE

    // متغیرهای مربوط به چیدمان HAND
    private final int overlap = 100;
    private final int angleFactor = 7;
    private final int cardHeight = 138;
    private final int padding = 10;
    private final int curveFactor = 40;

    // متغیرهای مربوط به چیدمان TABLE
    private final int cardsPerRow = 4;
    private final int horizontalSpacing = 10;
    private final int verticalSpacing = 10;

    // متغیرهای مربوط به drag-and-drop
    private float startX, startY;
    private float cardStartY;
    private ImageView draggedCard;
    private static final int PLAY_THRESHOLD = 200;

    private float[] lastCardPosition = new float[]{0f, 0f}; // مختصات آخرین کارت برای TABLE
    private List<ImageView> highlightedCards = new ArrayList<>();

    // متغیر برای ذخیره اندازه کارت روی زمین
    private float tableCardWidth = 0f;
    private float tableCardHeight = 0f;

    public CardContainerView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void setType(Type type) {
        this.type = type;
    }

    public void setCards(List<Card> newCards) {
        removeAllViews();
        cardViews.clear();
        cards.clear();
        if (type == Type.TABLE) selectedCards.clear();

        DisplayMetrics displayMetrics = getContext().getResources().getDisplayMetrics();
        int cardWidth = (int) (120 * displayMetrics.density);
        int cardHeightPx = (int) (cardHeight * displayMetrics.density);

        for (Card card : newCards) {
            ImageView cardView = new ImageView(getContext());
            cardView.setLayoutParams(new ConstraintLayout.LayoutParams(cardWidth, cardHeightPx));
            if (type == Type.HAND && showCards) {
                int resId = getResources().getIdentifier(card.getImageResourceName(), "drawable", getContext().getPackageName());
                cardView.setImageResource(resId != 0 ? resId : R.drawable.card_back);
            } else if (type == Type.TABLE) {
                int resId = getResources().getIdentifier(card.getImageResourceName(), "drawable", getContext().getPackageName());
                cardView.setImageResource(resId != 0 ? resId : R.drawable.card_back);
            } else {
                cardView.setImageResource(R.drawable.card_back);
            }
            if (type == Type.HAND) {
                setupDragAndDrop(cardView, card);
            } else if (type == Type.TABLE && isSelectable) {
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

    public void setShowCards(boolean show) {
        if (type == Type.HAND) {
            this.showCards = show;
            updateCardViews();
        }
    }

    private void updateCardViews() {
        for (int i = 0; i < cardViews.size(); i++) {
            ImageView cardView = cardViews.get(i);
            if (showCards && i < cards.size()) {
                Card card = cards.get(i);
                int resId = getResources().getIdentifier(card.getImageResourceName(), "drawable", getContext().getPackageName());
                cardView.setImageResource(resId != 0 ? resId : R.drawable.card_back);
            } else {
                cardView.setImageResource(R.drawable.card_back);
            }
        }
    }

    public void setEnabled(boolean enabled) {
        if (type == Type.HAND) {
            this.enabled = enabled;
        }
    }

    public void setSelectable(boolean selectable) {
        if (type == Type.TABLE) {
            this.isSelectable = selectable;
            for (ImageView cardView : cardViews) {
                cardView.setOnClickListener(selectable && onCardSelectedListener != null ?
                        v -> onCardSelectedListener.onCardSelected(cards.get(cardViews.indexOf(cardView))) : null);
            }
            requestLayout();
        }
    }

    public void setOnCardPlayedListener(OnCardPlayedListener listener) {
        if (type == Type.HAND) {
            this.cardPlayedListener = listener;
        }
    }

    public void setOnCardSelectedListener(OnCardSelectedListener listener) {
        if (type == Type.TABLE) {
            this.onCardSelectedListener = listener;
        }
    }

    public void updateSelection(List<Card> selected) {
        if (type == Type.TABLE) {
            this.selectedCards = new ArrayList<>(selected);
            requestLayout();
        }
    }

    public void clearSelection() {
        if (type == Type.TABLE) {
            selectedCards.clear();
            requestLayout();
        }
    }

    public List<Card> getCards() {
        return cards;
    }

    public float[] getLastCardPosition() {
        if (type == Type.TABLE) {
            return lastCardPosition;
        }
        return new float[]{0f, 0f};
    }

    public float[] getTableCardSize() {
        return new float[]{tableCardWidth, tableCardHeight};
    }

    public void removeCardFromHand(Card card) {
        if (type == Type.HAND) {
            int index = cards.indexOf(card);
            if (index != -1) {
                removeView(cardViews.get(index));
                cardViews.remove(index);
                cards.remove(index);
                requestLayout();
            }
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        if (type == Type.HAND) {
            layoutHand();
        } else if (type == Type.TABLE) {
            layoutTable();
        }
    }

    private void layoutHand() {
        int count = cardViews.size();
        if (count == 0) return;

        DisplayMetrics displayMetrics = getContext().getResources().getDisplayMetrics();
        int cardWidth = (int) (120 * displayMetrics.density);
        int cardHeightPx = (int) (cardHeight * displayMetrics.density);
        int screenWidth = getWidth();
        int screenHeight = getHeight();

        int maxAllowedWidth = screenWidth - (2 * padding);
        int totalWidth = (count - 1) * overlap + cardWidth;

        if (totalWidth > maxAllowedWidth) {
            float reductionFactor = (float) maxAllowedWidth / totalWidth;
            totalWidth = (count - 1) * (int) (overlap * reductionFactor) + cardWidth;
        }

        int startX = (screenWidth - totalWidth) / 2;
        int baseY = screenHeight - cardHeightPx - padding;

        for (int i = 0; i < count; i++) {
            ImageView card = cardViews.get(i);
            int left = startX + (i * overlap);
            int angle = (i - count / 2) * angleFactor;

            int adjustedY;
            if (count == 1) {
                adjustedY = baseY;
                angle = 0;
            } else {
                double normalizedPosition = (i - (count - 1) / 2.0) / ((count - 1) / 2.0);
                adjustedY = (int) (baseY - curveFactor * Math.cos(Math.toRadians(normalizedPosition * 90)));
            }

            if (card != draggedCard) {
                card.layout(left, adjustedY, left + cardWidth, adjustedY + cardHeightPx);
                card.setRotation(angle);
            }
            card.setZ(i);
        }
    }

    private void layoutTable() {
        int count = cardViews.size();
        if (count == 0) {
            lastCardPosition = new float[]{0f, 0f};
            tableCardWidth = 0f;
            tableCardHeight = 0f;
            return;
        }

        int screenWidth = getWidth();
        int screenHeight = getHeight();

        int availableWidth = screenWidth - (padding * (cardsPerRow + 1));
        int cardWidth = availableWidth / cardsPerRow;
        int cardHeightPx = (int) (cardWidth * 1.5);

        // ذخیره اندازه کارت‌های روی زمین
        tableCardWidth = cardWidth;
        tableCardHeight = cardHeightPx;

        for (ImageView cardView : cardViews) {
            ConstraintLayout.LayoutParams params = new ConstraintLayout.LayoutParams(cardWidth, cardHeightPx);
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

        int nextIndex = count;
        int nextRow = nextIndex / cardsPerRow;
        int nextColumn = nextIndex % cardsPerRow;
        lastCardPosition[0] = startX + (nextColumn * (cardWidth + horizontalSpacing));
        lastCardPosition[1] = startY + (nextRow * (cardHeightPx + verticalSpacing));
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setupDragAndDrop(ImageView cardView, Card card) {
        if (type != Type.HAND) return;

        cardView.setOnTouchListener(new OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (!enabled) return false;

                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        startX = event.getRawX();
                        startY = event.getRawY();
                        cardStartY = v.getY();
                        draggedCard = (ImageView) v;
                        // افزایش elevation برای اطمینان از نمایش در بالای tableView
                        draggedCard.setElevation(15f);
                        draggedCard.bringToFront();
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        float newX = event.getRawX() - startX;
                        float newY = event.getRawY() - startY;
                        draggedCard.setX(v.getX() + newX);
                        draggedCard.setY(v.getY() + newY);
                        startX = event.getRawX();
                        startY = event.getRawY();
                        return true;

                    case MotionEvent.ACTION_UP:
                        float currentY = draggedCard.getY();
                        if (cardStartY - currentY > PLAY_THRESHOLD) {
                            if (cardPlayedListener != null) {
                                int[] location = new int[2];
                                CardContainerView.this.getLocationOnScreen(location);
                                float globalDropX = draggedCard.getX() + location[0];
                                float globalDropY = draggedCard.getY() + location[1];
                                float rotation = draggedCard.getRotation();
                                cardPlayedListener.onCardPlayed(card, globalDropX, globalDropY, rotation);
                            }
                            // بازگرداندن elevation به حالت اولیه
                            draggedCard.setElevation(0f);
                        } else {
                            draggedCard.setX(v.getLeft());
                            draggedCard.setY(cardStartY);
                            // بازگرداندن elevation به حالت اولیه
                            draggedCard.setElevation(0f);
                            draggedCard = null;
                            requestLayout();
                        }
                        return true;
                }
                return false;
            }
        });
    }

    public void highlightCards(List<Card> cardsToHighlight, int color) {
        clearHighlights();
        for (Card card : cardsToHighlight) {
            int index = cards.indexOf(card);
            if (index != -1) {
                ImageView cardView = cardViews.get(index);
                highlightedCards.add(cardView);
                ObjectAnimator animator = ObjectAnimator.ofFloat(cardView, "alpha", 0.5f, 1f);
                animator.setDuration(500);
                animator.setRepeatCount(ObjectAnimator.INFINITE);
                animator.setRepeatMode(ObjectAnimator.REVERSE);
                animator.start();
            }
        }
    }

    public void clearHighlights() {
        for (ImageView cardView : highlightedCards) {
            cardView.clearAnimation();
            cardView.setAlpha(1f);
            cardView.clearColorFilter();
        }
        highlightedCards.clear();
    }

    public interface OnCardPlayedListener {
        void onCardPlayed(Card card, float dropX, float dropY, float rotation);
    }

    public interface OnCardSelectedListener {
        void onCardSelected(Card card);
    }
}