


package com.example.a4Barg.scene.game;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.lifecycle.ViewModelProvider;

import com.example.a4Barg.R;
import com.example.a4Barg.model.Card;
import com.example.a4Barg.model.InGameMessage;
import com.example.a4Barg.networking.SocketManager;
import com.example.a4Barg.utils.CardContainerView;
import com.example.a4Barg.utils.CollectedCardsView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GameActivity extends AppCompatActivity {

    private GameViewModel viewModel;
    private CardContainerView userHandView;
    private CardContainerView opponentHandView;
    private CardContainerView tableView;
    private CollectedCardsView userCollectedCardsView;
    private CollectedCardsView opponentCollectedCardsView;
    private String userId;
    private String roomNumber;
    private String gameId;
    private TextView userUsername, userExp, userCoins, userSurs;
    private TextView opponentUsername, opponentExp, opponentCoins, opponentSurs;
    private TextView turnIndicator;
    private TextView tvResults;
    private Button btnInGameMessage;
    private TextView tvUserInGameMessage;
    private TextView tvOpponentInGameMessage;
    private List<Card> selectedTableCards = new ArrayList<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private ConstraintLayout rootLayout;

    private Map<ImageView, ObjectAnimator> selectedBlinkingAnimators = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game);

        viewModel = new ViewModelProvider(this).get(GameViewModel.class);
        userId = getIntent().getStringExtra("userId");
        roomNumber = getIntent().getStringExtra("roomNumber");
        viewModel.setUserId(userId);

        rootLayout = findViewById(R.id.layout);
        userHandView = findViewById(R.id.user_handView);
        opponentHandView = findViewById(R.id.opponent_handView);
        tableView = findViewById(R.id.table_view);
        userCollectedCardsView = findViewById(R.id.user_collected_cards);
        opponentCollectedCardsView = findViewById(R.id.opponent_collected_cards);
        userUsername = findViewById(R.id.user_username);
        userExp = findViewById(R.id.user_exp);
        userCoins = findViewById(R.id.user_coins);
        userSurs = findViewById(R.id.user_surs);
        opponentUsername = findViewById(R.id.opponent_username);
        opponentExp = findViewById(R.id.opponent_exp);
        opponentCoins = findViewById(R.id.opponent_coins);
        opponentSurs = findViewById(R.id.opponent_surs);
        turnIndicator = findViewById(R.id.turn_indicator);
        tvResults = findViewById(R.id.tvResults);
        btnInGameMessage = findViewById(R.id.btnInGameMessage);
        tvUserInGameMessage = findViewById(R.id.tvUserInGameMessage);
        tvOpponentInGameMessage = findViewById(R.id.tvOpponentInGameMessage);

        userHandView.setType(CardContainerView.Type.HAND);
        opponentHandView.setType(CardContainerView.Type.HAND);
        tableView.setType(CardContainerView.Type.TABLE);

        userHandView.setShowCards(true);
        opponentHandView.setShowCards(false);

        userHandView.setOnCardPlayedListener(new CardContainerView.OnCardPlayedListener() {
            @Override
            public void onCardPlayed(Card card, float dropX, float dropY, float rotation) {
                clearAllBlinkingSelections();
                tableView.clearHighlights();
                viewModel.setPendingCard(card);
                List<Card> tableCards = tableView.getCards();
                int playedValue = getCardValue(card.getRank());
                List<List<Card>> combinations = findCombinations(tableCards, playedValue);

                if (combinations.isEmpty()) {
                    viewModel.playCard(card, new ArrayList<>());
                } else if (combinations.size() == 1) {
                    viewModel.playCard(card, combinations.get(0));
                } else {
                    tableView.setSelectable(true);
                    showOptions(combinations);
                }
                viewModel.setLastDropPosition(dropX, dropY, rotation);
            }
        });

        tableView.setOnCardSelectedListener(card -> {
            if (viewModel.getPendingCard() == null) {
                Log.w("GameActivity", "Card selected but no pending card!");
                return;
            }

            ImageView cardView = null;
            int index = tableView.getCards().indexOf(card);
            if (index != -1) {
                View childView = tableView.getChildAt(index);
                if (childView instanceof ImageView) {
                    cardView = (ImageView) childView;
                }
            }
            if (cardView == null) {
                Log.e("GameActivity", "Could not find ImageView for card: " + card.toString());
                return;
            }

            if (selectedTableCards.contains(card)) {
                selectedTableCards.remove(card);
                stopBlinkingGreen(cardView);
            } else if (selectedTableCards.size() < tableView.getCards().size()) {
                cardView.clearAnimation();
                cardView.setAlpha(1f);
                selectedTableCards.add(card);
                startBlinkingGreen(cardView);

                int currentSelectionValue = calculateTotalValue(selectedTableCards);
                int cardPlayedValue = getCardValue(viewModel.getPendingCard().getRank());

                if (cardPlayedValue + currentSelectionValue == 11) {
                    List<Card> cardsToPlay = new ArrayList<>(selectedTableCards);
                    clearAllBlinkingSelections();
                    tableView.setSelectable(false);
                    tableView.clearHighlights();
                    viewModel.playCard(viewModel.getPendingCard(), cardsToPlay);
                } else if (cardPlayedValue + currentSelectionValue > 11) {
                    showError("مجموع " + (cardPlayedValue + currentSelectionValue) + " شد! ترکیب اشتباه است.");
                    clearAllBlinkingSelections();
                    tableView.setSelectable(true);

                    List<List<Card>> combinations = findCombinations(tableView.getCards(), cardPlayedValue);
                    if (!combinations.isEmpty()) {
                        showOptions(combinations);
                    } else {
                        tableView.clearHighlights();
                    }
                }
            }
        });

        btnInGameMessage.setOnClickListener(v -> showMessageDialog());

        SocketManager.initialize(this, userId);
        viewModel.setupGameListeners(this);

        viewModel.startGame(roomNumber);

        viewModel.getUserCards().observe(this, this::updateUserHand);
        viewModel.getOpponentCardCount().observe(this, this::updateOpponentHand);
        viewModel.getTableCards().observe(this, tableCards -> {
            List<Card> currentSelection = new ArrayList<>(selectedTableCards);
            boolean selectionStillValid = true;
            if (!currentSelection.isEmpty()) {
                for (Card selectedCard : currentSelection) {
                    if (!tableCards.contains(selectedCard)) {
                        selectionStillValid = false;
                        break;
                    }
                }
                if (!selectionStillValid) {
                    Log.d("GameActivity", "Table changed, clearing invalid selection.");
                    clearAllBlinkingSelections();
                }
            }
            updateTableCards(tableCards);
        });
        viewModel.getUserCollectedCards().observe(this, this::updateUserCollectedCards);
        viewModel.getOpponentCollectedCards().observe(this, this::updateOpponentCollectedCards);
        viewModel.getCurrentTurn().observe(this, this::updateTurnIndicator);
        viewModel.getUserInfo().observe(this, info -> {
            userUsername.setText(info[0]);
            userExp.setText("EXP: " + info[1]);
            userCoins.setText("Coins: " + info[2]);
        });
        viewModel.getOpponentInfo().observe(this, info -> {
            opponentUsername.setText(info[0]);
            opponentExp.setText("EXP: " + info[1]);
            opponentCoins.setText("Coins: " + info[2]);
        });
        viewModel.getUserSurs().observe(this, surs -> userSurs.setText("Surs: " + surs));
        viewModel.getOpponentSurs().observe(this, surs -> opponentSurs.setText("Surs: " + surs));
        viewModel.getPossibleOptions().observe(this, options -> {
            if (options != null && !options.isEmpty()) {
            } else {
            }
        });
        viewModel.getGameOver().observe(this, gameOver -> {
            if (gameOver) {
                clearAllBlinkingSelections();
                userHandView.setEnabled(false);
                tableView.setSelectable(false);
                turnIndicator.setText("بازی تموم شد");
                tvResults.setVisibility(View.VISIBLE);
            }
        });
        viewModel.getGameResultText().observe(this, resultText -> {
            tvResults.setText(resultText);
        });

        viewModel.getInGameMessage().observe(this, message -> {
            if (message != null) {
                if (message.getUserId().equals(userId)) {
                    showMessage(tvUserInGameMessage, message.getMessage());
                } else {
                    showMessage(tvOpponentInGameMessage, message.getMessage());
                }
            }
        });
    }

    private void startBlinkingGreen(ImageView cardView) {
        if (selectedBlinkingAnimators.containsKey(cardView)) {
            Log.w("GameActivity", "Already blinking green: " + cardView.toString());
            return;
        }
        cardView.setColorFilter(Color.GREEN, PorterDuff.Mode.OVERLAY);
        ObjectAnimator animator = ObjectAnimator.ofFloat(cardView, "alpha", 0.6f, 1f);
        animator.setDuration(500);
        animator.setRepeatCount(ObjectAnimator.INFINITE);
        animator.setRepeatMode(ObjectAnimator.REVERSE);
        animator.start();
        selectedBlinkingAnimators.put(cardView, animator);
        Log.d("GameActivity", "Started blinking green: " + cardView.toString());
    }

    private void stopBlinkingGreen(ImageView cardView) {
        ObjectAnimator animator = selectedBlinkingAnimators.remove(cardView);
        if (animator != null) {
            animator.cancel();
        }
        cardView.setColorFilter(null);
        cardView.setAlpha(1f);
        Log.d("animation", "Stopped blinking green: " + cardView.toString());
    }

    private void clearAllBlinkingSelections() {
        Log.d("GameActivity", "Clearing all blinking selections.");
        List<ImageView> viewsToClear = new ArrayList<>(selectedBlinkingAnimators.keySet());
        for (ImageView view : viewsToClear) {
            stopBlinkingGreen(view);
        }
        selectedBlinkingAnimators.clear();
        selectedTableCards.clear();
        tableView.clearHighlights();
    }

    private void showMessageDialog() {
        String[] messages = {"دمت گرم", "عجب بازیکنی", "بازی بلد نیستی", "من میبرم"};
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("انتخاب پیام");
        builder.setItems(messages, (dialog, which) -> {
            String selectedMessage = messages[which];
            viewModel.sendInGameMessage(selectedMessage);
        });
        builder.setNegativeButton("لغو", null);
        builder.show();
    }

    private void showMessage(TextView textView, String message) {
        textView.setText(message);
        textView.setVisibility(View.VISIBLE);
        handler.postDelayed(() -> textView.setVisibility(View.GONE), 5000);
    }

    public void showError(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private int getCardValue(String rank) {
        switch (rank) {
            case "Ace": return 1;
            case "2": return 2;
            case "3": return 3;
            case "4": return 4;
            case "5": return 5;
            case "6": return 6;
            case "7": return 7;
            case "8": return 8;
            case "9": return 9;
            case "10": return 10;
            case "Jack": return 11;
            case "Queen": return 12;
            case "King": return 13;
            default: return 0;
        }
    }

    private int calculateTotalValue(List<Card> cards) {
        int total = 0;
        for (Card card : cards) {
            total += getCardValue(card.getRank());
        }
        return total;
    }

    private List<List<Card>> findCombinations(List<Card> tableCards, int playedCardValue) {
        List<List<Card>> result = new ArrayList<>();
        int target = 11 - playedCardValue;

        if (target <= 0) return result;

        List<Card> numericCards = new ArrayList<>();
        for (Card card : tableCards) {
            int value = getCardValue(card.getRank());
            if (value <= 10) {
                numericCards.add(card);
            }
        }

        int n = numericCards.size();
        for (int i = 0; i < (1 << n); i++) {
            List<Card> currentCombination = new ArrayList<>();
            int currentSum = 0;
            for (int j = 0; j < n; j++) {
                if ((i & (1 << j)) > 0) {
                    Card card = numericCards.get(j);
                    currentSum += getCardValue(card.getRank());
                    currentCombination.add(card);
                }
            }
            if (currentSum == target) {
                result.add(currentCombination);
            }
        }
        return result;
    }

    public void showOptions(List<List<Card>> options) {
        tableView.clearHighlights();
        List<Card> collectableCards = new ArrayList<>();
        for (List<Card> option : options) {
            for (Card card : option) {
                ImageView cardView = null;
                int index = tableView.getCards().indexOf(card);
                if (index != -1) {
                    View childView = tableView.getChildAt(index);
                    if (childView instanceof ImageView) {
                        cardView = (ImageView) childView;
                    }
                }
                if (!collectableCards.contains(card) && cardView != null && !selectedBlinkingAnimators.containsKey(cardView)) {
                    collectableCards.add(card);
                }
            }
        }
        if (!collectableCards.isEmpty()) {
            Log.d("GameActivity", "Highlighting options (alpha blink): " + collectableCards.size());
            tableView.highlightCards(collectableCards, Color.argb(0, 0, 0, 0));
        } else {
            Log.d("GameActivity", "No options to highlight (or they are already selected).");
        }
    }

    private boolean validateCombination(Card playedCard, List<Card> tableCardsToCollect) {
        int playedValue = getCardValue(playedCard.getRank());
        int sum = playedValue + calculateTotalValue(tableCardsToCollect);
        return sum == 11;
    }

    private void updateUserHand(List<Card> cards) {
        userHandView.setCards(cards);
    }

    private void updateOpponentHand(Integer cardCount) {
        List<Card> opponentCards = new ArrayList<>();
        for (int i = 0; i < cardCount; i++) {
            opponentCards.add(new Card("unknown", "unknown"));
        }
        opponentHandView.setCards(opponentCards);
    }

    private void updateTableCards(List<Card> tableCards) {
        tableView.setCards(tableCards);
    }

    private void updateUserCollectedCards(List<Card> cards) {
        userCollectedCardsView.setCards(cards);
    }

    private void updateOpponentCollectedCards(List<Card> cards) {
        opponentCollectedCardsView.setCards(cards);
    }

    private void updateTurnIndicator(String turnUserId) {
        if (turnUserId != null && turnUserId.equals(userId)) {
            turnIndicator.setText("نوبت شما");
            userHandView.setEnabled(true);
        } else {
            turnIndicator.setText("نوبت حریف");
            userHandView.setEnabled(false);
            clearAllBlinkingSelections();
            tableView.setSelectable(false);
        }
    }

    public CardContainerView getTableView() {
        return tableView;
    }

    public CardContainerView getUserHandView() {
        return userHandView;
    }

    public CardContainerView getOpponentHandView() {
        return opponentHandView;
    }

    public void animateCard(Card playedCard, boolean isUser, float startX, float startY, float startRotation, List<Card> tableCardsToCollect, Runnable onAnimationEnd) {
        Log.d("animation", "Starting animateCard - Played Card: " + playedCard.toString() + ", Played by: " + (isUser ? "User" : "Opponent"));
        Log.d("animation", "Source Position - Title: " + (isUser ? "User Hand (Drop Location)" : "Opponent Hand Center") + ", Coordinates: (" + startX + ", " + startY + "), Rotation: " + startRotation);
        Log.d("animation", "Cards to Collect: " + (tableCardsToCollect == null ? "None" : tableCardsToCollect.toString()));

        if (tableCardsToCollect == null || tableCardsToCollect.isEmpty()) {
            Log.d("animation", "No cards to collect, animating directly to table.");
            ImageView animatedCard = createAnimatedCard(playedCard);
            animatedCard.setX(startX);
            animatedCard.setY(startY);
            animatedCard.setRotation(startRotation);
            rootLayout.addView(animatedCard);
            Log.d("animation", "Added played card to rootLayout at position: (" + startX + ", " + startY + ")");

            tableView.post(() -> {
                float[] lastCardPosition = tableView.getLastCardPosition();
                float endX = tableView.getX() + lastCardPosition[0];
                float endY = tableView.getY() + lastCardPosition[1];
                Log.d("animation", "Destination Position - Title: Table (Last Card Position), Coordinates: (" + endX + ", " + endY + ")");

                ObjectAnimator moveX = ObjectAnimator.ofFloat(animatedCard, "x", startX, endX);
                ObjectAnimator moveY = ObjectAnimator.ofFloat(animatedCard, "y", startY, endY);
                ObjectAnimator rotate = ObjectAnimator.ofFloat(animatedCard, "rotation", startRotation, 0f);

                AnimatorSet animatorSet = new AnimatorSet();
                animatorSet.playTogether(moveX, moveY, rotate);
                animatorSet.setDuration(1000);
                animatorSet.start();
                Log.d("animation", "Started animation to table for card: " + playedCard.toString());

                animatorSet.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        Log.d("animation", "Animation to table completed for card: " + playedCard.toString());
                        rootLayout.removeView(animatedCard);
                        Log.d("animation", "Removed played card from rootLayout");
                        if (onAnimationEnd != null) {
                            Log.d("animation", "Running onAnimationEnd callback.");
                            onAnimationEnd.run();
                        }
                    }
                });
            });
        } else {
            Log.d("animation", "Cards to collect exist, starting collection animation.");
            List<ImageView> collectedCardViews = new ArrayList<>();
            ImageView playedCardView = createAnimatedCard(playedCard);
            playedCardView.setX(startX);
            playedCardView.setY(startY);
            playedCardView.setRotation(startRotation);
            rootLayout.addView(playedCardView);
            collectedCardViews.add(playedCardView);
            Log.d("animation", "Added played card to rootLayout at position: (" + startX + ", " + startY + ")");

            List<ImageView> tableCardViews = new ArrayList<>();
            for (Card tableCard : tableCardsToCollect) {
                ImageView tableCardView = findTableCardView(tableCard);
                if (tableCardView != null) {
                    int[] tableCardLocation = new int[2];
                    tableCardView.getLocationOnScreen(tableCardLocation);
                    int[] rootLocation = new int[2];
                    rootLayout.getLocationOnScreen(rootLocation);

                    float tableCardX = tableCardLocation[0] - rootLocation[0];
                    float tableCardY = tableCardLocation[1] - rootLocation[1];
                    Log.d("animation", "Found table card: " + tableCard.toString() + " at position: (" + tableCardX + ", " + tableCardY + ")");

                    // کارت اصلی در tableView باقی می‌ماند تا زمان اتمام انیمیشن مرحله‌ای
                    tableCardViews.add(tableCardView);
                } else {
                    Log.w("animation", "Could not find table card view for: " + tableCard.toString());
                }
            }

            AnimatorSet animatorSet = new AnimatorSet();
            List<AnimatorSet> animators = new ArrayList<>();
            float lastX = startX;
            float lastY = startY;

            for (int i = 0; i < tableCardViews.size(); i++) {
                ImageView tableCardView = tableCardViews.get(i);
                Card tableCard = tableCardsToCollect.get(i);

                int[] tableCardLocation = new int[2];
                tableCardView.getLocationOnScreen(tableCardLocation);
                int[] rootLocation = new int[2];
                rootLayout.getLocationOnScreen(rootLocation);

                float overlapX = tableCardLocation[0] - rootLocation[0];
                float overlapY = tableCardLocation[1] - rootLocation[1];
                Log.d("animation", "Step " + (i + 1) + ": Moving to overlap position for card: " + tableCard.toString() + ", Target: (" + overlapX + ", " + overlapY + ")");

                // جابجایی 20% به سمت راست برای کارت بازی‌شده
                DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
                int cardWidth = (int) (120 * displayMetrics.density);
                float offsetX = cardWidth * 0.2f; // 20% عرض کارت
                overlapX += offsetX;

                List<ObjectAnimator> stageAnimators = new ArrayList<>();
                for (ImageView cardView : collectedCardViews) {
                    String cardName = (cardView == playedCardView) ? playedCard.toString() : tableCardsToCollect.get(collectedCardViews.indexOf(cardView) - 1).toString();
                    Log.d("animation", "Animating card: " + cardName + " from (" + lastX + ", " + lastY + ") to (" + overlapX + ", " + overlapY + ")");
                    stageAnimators.add(ObjectAnimator.ofFloat(cardView, "x", lastX, overlapX));
                    stageAnimators.add(ObjectAnimator.ofFloat(cardView, "y", lastY, overlapY));
                    stageAnimators.add(ObjectAnimator.ofFloat(cardView, "rotation", cardView.getRotation(), 0f));
                }

                AnimatorSet stageAnimator = new AnimatorSet();
                stageAnimator.playTogether(stageAnimators.toArray(new ObjectAnimator[0]));
                stageAnimator.setDuration(1000);
                animators.add(stageAnimator);
                Log.d("animation", "Created overlap animation for step " + (i + 1));

                int finalI = i; // برای دسترسی به index در listener
                float finalOverlapX = overlapX;
                stageAnimator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        Log.d("animation", "Stage " + (finalI + 1) + " completed, removing table card: " + tableCard.toString());
                        if (tableCardView.getParent() != null) {
                            ((ViewGroup) tableCardView.getParent()).removeView(tableCardView);
                        }
                        rootLayout.addView(tableCardView);
                        tableCardView.setX(finalOverlapX);
                        tableCardView.setY(overlapY);
                        collectedCardViews.add(tableCardView);
                        Log.d("animation", "Added original table card to collectedCardViews: " + tableCard.toString());
                    }
                });

                lastX = overlapX;
                lastY = overlapY;
            }

            // انیمیشن نهایی به سمت collectedCards
            View collectedView = isUser ? userCollectedCardsView : opponentCollectedCardsView;
            int[] collectedLocation = new int[2];
            collectedView.getLocationOnScreen(collectedLocation);
            int[] rootLocation = new int[2];
            rootLayout.getLocationOnScreen(rootLocation);

            float collectedX = collectedLocation[0] - rootLocation[0] + collectedView.getWidth() / 2f - playedCardView.getWidth() / 2f;
            float collectedY = collectedLocation[1] - rootLocation[1] + collectedView.getHeight() / 2f - playedCardView.getHeight() / 2f;
            Log.d("animation", "Final Destination - Title: " + (isUser ? "User Collected Cards" : "Opponent Collected Cards") + ", Coordinates: (" + collectedX + ", " + collectedY + ")");

            List<ObjectAnimator> finalAnimators = new ArrayList<>();
            for (ImageView cardView : collectedCardViews) {
                String cardName = (cardView == playedCardView) ? playedCard.toString() : tableCardsToCollect.get(collectedCardViews.indexOf(cardView) - 1).toString();
                Log.d("animation", "Final Step: Moving card: " + cardName + " from overlap position: (" + lastX + ", " + lastY + ") to collected position: (" + collectedX + ", " + collectedY + ")");
                cardView.setX(lastX);
                cardView.setY(lastY);
                finalAnimators.add(ObjectAnimator.ofFloat(cardView, "x", lastX, collectedX));
                finalAnimators.add(ObjectAnimator.ofFloat(cardView, "y", lastY, collectedY));
            }

            AnimatorSet finalAnimatorSet = new AnimatorSet();
            finalAnimatorSet.playTogether(finalAnimators.toArray(new ObjectAnimator[0]));
            finalAnimatorSet.setDuration(1000);
            animators.add(finalAnimatorSet);
            Log.d("animation", "Created final animation to collected cards");

            for (int i = 0; i < animators.size(); i++) {
                if (i == 0) {
                    animatorSet.play(animators.get(i));
                } else {
                    animatorSet.play(animators.get(i)).after(animators.get(i - 1)).after(1000); // مکث 1 ثانیه‌ای بین مراحل
                }
            }

            // تغییر تصویر کارت‌ها به پشت کارت قبل از انیمیشن نهایی
            finalAnimatorSet.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationStart(Animator animation) {
                    for (ImageView cardView : collectedCardViews) {
                        String cardName = (cardView == playedCardView) ? playedCard.toString() : tableCardsToCollect.get(collectedCardViews.indexOf(cardView) - 1).toString();
                        cardView.setImageResource(R.drawable.card_back);
                        Log.d("animation", "Changed card image to card_back for: " + cardName + " before final animation");
                    }
                }
            });

            animatorSet.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    Log.d("animation", "Final animation to collected cards completed.");
                    for (ImageView cardView : collectedCardViews) {
                        rootLayout.removeView(cardView);
                        Log.d("animation", "Removed card from rootLayout: " + (cardView == playedCardView ? playedCard.toString() : tableCardsToCollect.get(collectedCardViews.indexOf(cardView) - 1).toString()));
                    }
                    // پاکسازی کامل tableView از ویوهای باقیمانده
                    for (int i = 0; i < tableView.getChildCount(); i++) {
                        View child = tableView.getChildAt(i);
                        if (child instanceof ImageView) {
                            tableView.removeView(child);
                            Log.d("animation", "Removed leftover view from tableView");
                        }
                    }
                    if (onAnimationEnd != null) {
                        Log.d("animation", "Running onAnimationEnd callback.");
                        onAnimationEnd.run();
                    }
                }
            });

            Log.d("animation", "Starting animation sequence.");
            animatorSet.start();
        }
    }

    private ImageView createAnimatedCard(Card card) {
        ImageView animatedCard = new ImageView(this);
        int resId = getResources().getIdentifier(card.getImageResourceName(), "drawable", getPackageName());
        animatedCard.setImageResource(resId != 0 ? resId : R.drawable.card_back);

        DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
        int cardWidth = (int) (120 * displayMetrics.density);
        int cardHeightPx = (int) (138 * displayMetrics.density);
        ConstraintLayout.LayoutParams params = new ConstraintLayout.LayoutParams(cardWidth, cardHeightPx);
        animatedCard.setLayoutParams(params);

        animatedCard.setElevation(100f);
        animatedCard.setZ(100f);

        return animatedCard;
    }

    private ImageView findTableCardView(Card tableCard) {
        int index = tableView.getCards().indexOf(tableCard);
        if (index != -1) {
            View childView = tableView.getChildAt(index);
            if (childView instanceof ImageView) {
                return (ImageView) childView;
            }
        }
        return null;
    }
}
