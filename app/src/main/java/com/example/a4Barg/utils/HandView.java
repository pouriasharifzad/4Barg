package com.example.a4Barg.utils;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.Toast;

import com.example.a4Barg.R;
import com.example.a4Barg.model.Card;

import java.util.ArrayList;
import java.util.List;

public class HandView extends FrameLayout {
    private List<ImageView> cards = new ArrayList<>();
    private List<Card> cardModels = new ArrayList<>();
    private boolean showCards = true; // برای کنترل نمایش رو یا پشت کارت‌ها

    private final int overlap = 100;
    private final int angleFactor = 7;
    private final int cardHeight = 138;
    private final int padding = 30;
    private final int curveFactor = 40;

    // متغیرهای مربوط به Drag-and-Drop
    private float startX, startY; // موقعیت اولیه لمس
    private float cardStartY; // موقعیت اولیه کارت
    private ImageView draggedCard; // کارتی که در حال کشیده شدن است
    private static final int PLAY_THRESHOLD = 300; // حد آستانه (پیکسل) برای تشخیص بازی شدن

    // ویژگی Gravity
    private int gravity = Gravity.BOTTOM; // پیش‌فرض پایین

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
            // اضافه کردن قابلیت Drag-and-Drop
            setupDragAndDrop(cardView, card);
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

        // اضافه کردن قابلیت Drag-and-Drop
        setupDragAndDrop(cardView, card);
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

    // متد جدید برای تنظیم Gravity
    public void setGravity(int gravity) {
        this.gravity = gravity;
        requestLayout();
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int count = cards.size();
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
        int baseY;
        if (gravity == Gravity.BOTTOM) {
            baseY = screenHeight - cardHeightPx - padding; // پایین‌ترین نقطه با کمی فاصله
        } else {
            baseY = getHeight() / 2 - (cardHeightPx / 2); // وسط (پیش‌فرض قبلی)
        }

        // از چپ به راست می‌چینیم تا کارت سمت چپ آخرین رندر بشه
        for (int i = 0; i < count; i++) {
            ImageView card = cards.get(i);
            int left = startX + (i * overlap);
            int angle = (i - count / 2) * angleFactor;

            double normalizedPosition = (i - (count - 1) / 2.0) / ((count - 1) / 2.0);
            int adjustedY = (int) (baseY - curveFactor * Math.cos(Math.toRadians(normalizedPosition * 90)));

            // اگه کارت در حال Drag نیست، موقعیتش رو تنظیم کن
            if (card != draggedCard) {
                card.layout(left, adjustedY, left + cardWidth, adjustedY + cardHeightPx);
                card.setRotation(angle);
            }
            // تنظیم Z-Order: کارت سمت چپ (اولین i) باید بالاترین باشه
            card.setZ(i);
        }
    }
    @SuppressLint("ClickableViewAccessibility")
    // متد جدید برای اضافه کردن Drag-and-Drop
    private void setupDragAndDrop(ImageView cardView, Card card) {
        cardView.setOnTouchListener(new OnTouchListener() {

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        startX = event.getRawX();
                        startY = event.getRawY();
                        cardStartY = v.getY(); // موقعیت اولیه کارت
                        draggedCard = (ImageView) v;
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
                        if (cardStartY - currentY > PLAY_THRESHOLD) { // اگه بیشتر از 200 پیکسل بالا بره
                            Toast.makeText(getContext(), "کارت بازی شد: " + card.getSuit() + " " + card.getRank(), Toast.LENGTH_SHORT).show();
                            removeCardFromHand(card); // کارت رو از دست حذف کن
                        } else {
                            // برگردوندن کارت به موقعیت اولیه بدون تغییر Z-Order
                            draggedCard.setX(v.getLeft());
                            draggedCard.setY(cardStartY);
                        }
                        draggedCard = null;
                        requestLayout(); // بازچینی کارت‌ها با حفظ نظم
                        return true;
                }
                return false;
            }
        });
    }

    // متد کمکی برای حذف کارت از دست
    private void removeCardFromHand(Card card) {
        int index = cardModels.indexOf(card);
        if (index != -1) {
            removeView(cards.get(index));
            cards.remove(index);
            cardModels.remove(index);
        }
    }
}