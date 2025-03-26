package com.example.a4Barg.utils;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;


import com.example.a4Barg.R;
import com.example.a4Barg.model.Card;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class HandView extends FrameLayout {
    private List<ImageView> cards = new ArrayList<>();
    private List<Integer> initialX = new ArrayList<>();
    private List<Integer> initialY = new ArrayList<>();
    private List<Card> cardModels = new ArrayList<>(); // برای ذخیره مدل کارت‌ها
    private static final String TAG = "HandView";
    private Consumer<Card> onCardPlayedListener; // Callback برای بازی کردن کارت

    public HandView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void setOnCardPlayedListener(Consumer<Card> listener) {
        this.onCardPlayedListener = listener;
    }

    public void addCard(Card card) {
        ImageView cardView = new ImageView(getContext());
        DisplayMetrics displayMetrics = getContext().getResources().getDisplayMetrics();

        int baseWidth = (int) (150 * displayMetrics.density);
        int baseHeight = (int) (220 * displayMetrics.density);

        int maxWidth = getWidth() > 0 ? (int) (getWidth() * 0.9) : (int) (417 * displayMetrics.density);
        int maxHeight = getHeight() > 0 ? (int) (getHeight() * 0.9) : (int) (170 * displayMetrics.density);

        float widthScale = Math.min(1.0f, (float) maxWidth / baseWidth);
        float heightScale = Math.min(1.0f, (float) maxHeight / baseHeight);
        float scale = Math.min(widthScale, heightScale);

        int cardWidth = (int) (baseWidth * scale);
        int cardHeight = (int) (baseHeight * scale);

        cardView.setLayoutParams(new LayoutParams(cardWidth, cardHeight));
        String imageName = card.getImageResourceName();
        Log.d(TAG, "Trying to load image: " + imageName + " for card: " + card + ", HandView size: " + getWidth() + "x" + getHeight());
        int resId = getResources().getIdentifier(imageName, "drawable", getContext().getPackageName());
        if (resId != 0) {
            cardView.setImageResource(resId);
        } else {
            Log.e(TAG, "Image resource not found for: " + imageName + ". Check drawable resources.");
            cardView.setImageResource(R.drawable.card_back);
        }
        cards.add(cardView);
        cardModels.add(card); // ذخیره مدل کارت
        addView(cardView);
        Log.d(TAG, "Added card to HandView. Size: " + cardWidth + "x" + cardHeight + ", HandView size: " + getWidth() + "x" + getHeight());
        requestLayout();
        invalidate();

        post(() -> {
            requestLayout();
            invalidate();
            Log.d(TAG, "Post-layout check. HandView size: " + getWidth() + "x" + getHeight());
        });
    }

    public void removeCard(ImageView cardView) {
        int index = cards.indexOf(cardView);
        if (index != -1) {
            cards.remove(index);
            cardModels.remove(index);
            removeView(cardView);
            requestLayout();
            invalidate();
            Log.d(TAG, "Removed card from HandView. Remaining cards: " + cards.size());
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int count = cards.size();
        if (count == 0) {
            Log.d(TAG, "No cards to layout in HandView.");
            return;
        }

        DisplayMetrics displayMetrics = getContext().getResources().getDisplayMetrics();
        int overlap = (int) (50 * displayMetrics.density);
        int centerX = getWidth() / 2;
        int startX = centerX - (count * overlap) / 2;
        int baseY = getHeight() / 2 - (int) (110 * displayMetrics.density);

        initialX.clear();
        initialY.clear();

        Log.d(TAG, "Laying out " + count + " cards. HandView size: " + getWidth() + "x" + getHeight());

        for (int i = 0; i < count; i++) {
            ImageView card = cards.get(i);
            if (card == null) {
                Log.w(TAG, "Card at index " + i + " is null.");
                continue;
            }

            int left = startX + (i * overlap);
            int angle = (i - count / 2) * 10;
            int adjustedY = (int) (baseY - 60 * Math.cos(Math.toRadians((double) (i - count / 2) * 15)));

            int cardWidth = card.getLayoutParams().width;
            int cardHeight = card.getLayoutParams().height;

            left = Math.max(0, Math.min(left, getWidth() - cardWidth));
            adjustedY = Math.max(0, Math.min(adjustedY, getHeight() - cardHeight));

            card.layout(left, adjustedY, left + cardWidth, adjustedY + cardHeight);
            card.setRotation(angle);

            initialX.add(left);
            initialY.add(adjustedY);

            enableDrag(card, i);
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private void enableDrag(final ImageView card, final int index) {
        card.setOnTouchListener(new OnTouchListener() {
            float dX, dY;
            float startX, startY;

            @Override
            public boolean onTouch(View view, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        dX = view.getX() - event.getRawX();
                        dY = view.getY() - event.getRawY();
                        startX = view.getX();
                        startY = view.getY();
                        break;

                    case MotionEvent.ACTION_MOVE:
                        view.animate()
                                .x(event.getRawX() + dX)
                                .y(event.getRawY() + dY)
                                .setDuration(0)
                                .start();
                        break;

                    case MotionEvent.ACTION_UP:
                        float dropX = view.getX();
                        float dropY = view.getY();

                        LinearLayout playedCardsContainer = ((Activity) getContext()).findViewById(R.id.played_cards_container);
                        if (playedCardsContainer != null) {
                            float containerX = playedCardsContainer.getX();
                            float containerY = playedCardsContainer.getY();
                            float containerWidth = playedCardsContainer.getWidth();
                            float containerHeight = playedCardsContainer.getHeight();

                            if (dropX >= containerX && dropX <= containerX + containerWidth &&
                                    dropY >= containerY && dropY <= containerY + containerHeight) {
                                Toast.makeText(getContext(), "کارت بازی شد!", Toast.LENGTH_SHORT).show();
                                Card playedCard = cardModels.get(index);
                                if (onCardPlayedListener != null) {
                                    onCardPlayedListener.accept(playedCard); // فراخوانی callback
                                }
                                removeCard((ImageView) view);
                            } else {
                                view.animate()
                                        .x(initialX.get(index))
                                        .y(initialY.get(index))
                                        .rotation(view.getRotation())
                                        .setDuration(300)
                                        .start();
                            }
                        } else {
                            Log.e(TAG, "playedCardsContainer not found!");
                        }
                        break;
                }
                return true;
            }
        });
    }
}