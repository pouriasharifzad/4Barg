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
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.lifecycle.ViewModelProvider;

import com.example.a4Barg.R;
import com.example.a4Barg.common.BaseActivity;
import com.example.a4Barg.model.Card;
import com.example.a4Barg.model.InGameMessage;
import com.example.a4Barg.networking.SocketManager;
import com.example.a4Barg.utils.CardContainerView;
import com.example.a4Barg.utils.CollectedCardsView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GameActivity extends BaseActivity {

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
    private boolean isInitialTableCardsSet = false;
    private boolean isInitialUserHandSet = false;
    private boolean isTableAnimationComplete = false;

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
        Log.d("HandCards", "Updating user hand with " + (cards != null ? cards.size() : 0) + " cards");
        if (cards == null || cards.isEmpty()) {
            Log.d("HandCards", "No cards to display in user hand");
            userHandView.setCards(new ArrayList<>());
            return;
        }
        Log.d("HandCards", "Cards to display: " + cards.toString());
        if (cards.size() == 4 && userHandView.getCards().isEmpty() && !isInitialUserHandSet) {
            userHandView.setInitialAnimationPending(true);
            if (isTableAnimationComplete) {
                animateInitialUserHandCards(cards);
                isInitialUserHandSet = true;
            } else {
                Log.d("HandCards", "Waiting for table animation to complete before user hand animation");
            }
        } else {
            userHandView.setCards(cards);
            Log.d("HandCards", "Cards laid out in userHandView");
            userHandView.post(() -> {
                int count = userHandView.getCards().size();
                if (count > 0) {
                    for (int i = 0; i < count; i++) {
                        ImageView cardView = (ImageView) userHandView.getChildAt(i);
                        Log.d("HandCards", "Card " + i + " position: x=" + cardView.getX() + ", y=" + cardView.getY());
                        Log.d("HandCards", "Card " + i + " size: width=" + cardView.getWidth() + ", height=" + cardView.getHeight());
                        Log.d("HandCards", "Card " + i + " rotation: " + cardView.getRotation());
                    }
                }
            });
        }
    }

    private void updateOpponentHand(Integer cardCount) {
        List<Card> opponentCards = new ArrayList<>();
        for (int i = 0; i < cardCount; i++) {
            opponentCards.add(new Card("unknown", "unknown"));
        }
        opponentHandView.setCards(opponentCards);
    }

    private void updateTableCards(List<Card> tableCards) {
        Log.d("TableCards", "Updating table cards with " + (tableCards != null ? tableCards.size() : 0) + " cards");
        if (tableCards == null || tableCards.isEmpty()) {
            Log.d("TableCards", "No cards to display on table");
            tableView.setCards(new ArrayList<>());
            // isInitialTableCardsSet را بازنشانی نمی‌کنیم تا انیمیشن اولیه دوباره اجرا نشود
            return;
        }
        Log.d("TableCards", "Cards to display: " + tableCards.toString());

        // فقط در ابتدای بازی (وقتی دست کاربر 4 کارت دارد) انیمیشن اولیه اجرا شود
        List<Card> userCards = viewModel.getUserCards().getValue();
        boolean isGameStart = userCards != null && userCards.size() == 4 && !isInitialTableCardsSet;
        if (tableCards.size() == 4 && isGameStart) {
            tableView.setInitialAnimationPending(true);
            animateInitialTableCards(tableCards);
            isInitialTableCardsSet = true;
        } else {
            if (tableView.getWidth() == 0 || tableView.getHeight() == 0) {
                tableView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                        tableView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                        int screenWidth = tableView.getWidth();
                        int screenHeight = tableView.getHeight();
                        Log.d("TableCards", "TableView dimensions after layout: width=" + screenWidth + ", height=" + screenHeight);
                        tableView.setCards(tableCards);
                        Log.d("TableCards", "Cards laid out in tableView");
                        float[] lastCardPos = tableView.getLastCardPosition();
                        Log.d("TableCards", "Last card position: x=" + lastCardPos[0] + ", y=" + lastCardPos[1]);
                        tableView.post(() -> {
                            int count = tableView.getCards().size();
                            if (count > 0) {
                                for (int i = 0; i < count; i++) {
                                    ImageView cardView = (ImageView) tableView.getChildAt(i);
                                    Log.d("TableCards", "Card " + i + " position: x=" + cardView.getX() + ", y=" + cardView.getY());
                                    Log.d("TableCards", "Card " + i + " size: width=" + cardView.getWidth() + ", height=" + cardView.getHeight());
                                }
                            }
                        });
                    }
                });
            } else {
                int screenWidth = tableView.getWidth();
                int screenHeight = tableView.getHeight();
                Log.d("TableCards", "TableView dimensions: width=" + screenWidth + ", height=" + screenHeight);
                tableView.setCards(tableCards);
                Log.d("TableCards", "Cards laid out in tableView");
                float[] lastCardPos = tableView.getLastCardPosition();
                Log.d("TableCards", "Last card position: x=" + lastCardPos[0] + ", y=" + lastCardPos[1]);
                tableView.post(() -> {
                    int count = tableView.getCards().size();
                    if (count > 0) {
                        for (int i = 0; i < count; i++) {
                            ImageView cardView = (ImageView) tableView.getChildAt(i);
                            Log.d("TableCards", "Card " + i + " position: x=" + cardView.getX() + ", y=" + cardView.getY());
                            Log.d("TableCards", "Card " + i + " size: width=" + cardView.getWidth() + ", height=" + cardView.getHeight());
                        }
                    }
                });
            }
        }
    }

    private void animateInitialTableCards(List<Card> initialCards) {
        if (initialCards.size() != 4) {
            Log.w("TableCards", "animateInitialTableCards called with incorrect number of cards: " + initialCards.size());
            return;
        }

        int screenWidth = tableView.getWidth();
        int screenHeight = tableView.getHeight();
        Log.d("TableCards", "TableView dimensions: width=" + screenWidth + ", height=" + screenHeight);

        float cardWidth = 240;
        float cardHeight = 360;
        float aspectRatio = cardWidth / cardHeight;

        float startX = screenWidth;
        float startY = screenHeight / 2f - cardHeight / 2f;
        Log.d("TableCards", "Animation start position: x=" + startX + ", y=" + startY);

        float[] targetXs = new float[]{10, 260, 510, 760};
        float[] targetYs = new float[]{10, 10, 10, 10};

        tableView.setCards(new ArrayList<>());

        List<ImageView> animatedCards = new ArrayList<>();
        for (int i = 0; i < initialCards.size(); i++) {
            Card card = initialCards.get(i);
            ImageView cardView = createAnimatedCard(card);

            ConstraintLayout.LayoutParams params = (ConstraintLayout.LayoutParams) cardView.getLayoutParams();
            params.width = (int) cardWidth;
            params.height = (int) cardHeight;
            cardView.setLayoutParams(params);
            Log.d("TableCards", "Card " + i + " initial size: width=" + cardWidth + ", height=" + cardHeight);

            cardView.setX(startX);
            cardView.setY(startY);
            cardView.setScaleX(0f);
            cardView.setScaleY(0f);
            cardView.setRotation(0f);

            rootLayout.addView(cardView);
            animatedCards.add(cardView);
            Log.d("TableCards", "Added card " + i + " to rootLayout at position: x=" + startX + ", y=" + startY + " with initial scale: (0, 0)");
        }

        AnimatorSet fullAnimatorSet = new AnimatorSet();
        List<Animator> cardAnimators = new ArrayList<>();

        for (int i = 0; i < animatedCards.size(); i++) {
            ImageView cardView = animatedCards.get(i);
            float targetX = tableView.getX() + targetXs[i];
            float targetY = tableView.getY() + targetYs[i];
            Log.d("TableCards", "Card " + i + " target position: x=" + targetX + ", y=" + targetY);

            ObjectAnimator moveX = ObjectAnimator.ofFloat(cardView, "x", startX, targetX);
            ObjectAnimator moveY = ObjectAnimator.ofFloat(cardView, "y", startY, targetY);
            ObjectAnimator scaleX = ObjectAnimator.ofFloat(cardView, "scaleX", 0f, 1f);
            ObjectAnimator scaleY = ObjectAnimator.ofFloat(cardView, "scaleY", 0f, 1f);

            AnimatorSet cardAnimator = new AnimatorSet();
            cardAnimator.playTogether(moveX, moveY, scaleX, scaleY);
            cardAnimator.setDuration(500);
            cardAnimators.add(cardAnimator);

            Log.d("TableCards", "Created animation for card " + i + " to position: x=" + targetX + ", y=" + targetY + " with scale from (0,0) to (1,1)");
        }

        for (int i = 0; i < cardAnimators.size(); i++) {
            if (i == 0) {
                fullAnimatorSet.play(cardAnimators.get(i));
            } else {
                fullAnimatorSet.play(cardAnimators.get(i)).after(cardAnimators.get(i - 1)).after(250);
            }
        }

        fullAnimatorSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                Log.d("TableCards", "Initial cards animation completed");
                tableView.setCards(initialCards);
                Log.d("TableCards", "Cards laid out in tableView after animation");
                isTableAnimationComplete = true;

                for (ImageView cardView : animatedCards) {
                    rootLayout.removeView(cardView);
                    Log.d("TableCards", "Removed animated card from rootLayout");
                }

                tableView.setInitialAnimationPending(false);

                tableView.post(() -> {
                    int count = tableView.getCards().size();
                    if (count > 0) {
                        for (int i = 0; i < count; i++) {
                            ImageView cardView = (ImageView) tableView.getChildAt(i);
                            Log.d("TableCards", "Card " + i + " position: x=" + cardView.getX() + ", y=" + cardView.getY());
                            Log.d("TableCards", "Card " + i + " size: width=" + cardView.getWidth() + ", height=" + cardView.getHeight());
                        }
                    }
                });

                List<Card> userCards = viewModel.getUserCards().getValue();
                if (userCards != null && userCards.size() == 4 && userHandView.getCards().isEmpty() && !isInitialUserHandSet) {
                    userHandView.setInitialAnimationPending(true);
                    animateInitialUserHandCards(userCards);
                    isInitialUserHandSet = true;
                }
            }
        });

        fullAnimatorSet.start();
        Log.d("TableCards", "Started animation for initial 4 cards");
    }

    private void animateInitialUserHandCards(List<Card> initialCards) {
        if (initialCards.size() != 4) {
            Log.w("HandCards", "animateInitialUserHandCards called with incorrect number of cards: " + initialCards.size());
            return;
        }

        int screenWidth = userHandView.getWidth();
        int screenHeight = userHandView.getHeight();
        Log.d("HandCards", "UserHandView dimensions: width=" + screenWidth + ", height=" + screenHeight);

        float cardWidth = 315;
        float cardHeight = 362;
        float aspectRatio = cardWidth / cardHeight;

        float startX = screenWidth;
        float startY = screenHeight / 2f - cardHeight / 2f;
        Log.d("HandCards", "Animation start position: x=" + startX + ", y=" + startY);

        float[] targetXs = new float[]{178, 278, 378, 478};
        float[] targetYs = new float[]{1402, 1367, 1367, 1402};
        float[] targetRotations = new float[]{-14, -7, 0, 7};

        userHandView.setCards(new ArrayList<>());

        List<ImageView> animatedCards = new ArrayList<>();
        for (int i = 0; i < initialCards.size(); i++) {
            Card card = initialCards.get(i);
            ImageView cardView = createAnimatedCard(card);

            ConstraintLayout.LayoutParams params = (ConstraintLayout.LayoutParams) cardView.getLayoutParams();
            params.width = (int) cardWidth;
            params.height = (int) cardHeight;
            cardView.setLayoutParams(params);
            Log.d("HandCards", "Card " + i + " initial size: width=" + cardWidth + ", height=" + cardHeight);

            cardView.setX(startX);
            cardView.setY(startY);
            cardView.setScaleX(0f);
            cardView.setScaleY(0f);
            cardView.setRotation(0f);

            rootLayout.addView(cardView);
            animatedCards.add(cardView);
            Log.d("HandCards", "Added card " + i + " to rootLayout at position: x=" + startX + ", y=" + startY + " with initial scale: (0, 0)");
        }

        AnimatorSet fullAnimatorSet = new AnimatorSet();
        List<Animator> cardAnimators = new ArrayList<>();

        for (int i = 0; i < animatedCards.size(); i++) {
            ImageView cardView = animatedCards.get(i);
            float targetX = userHandView.getX() + targetXs[i];
            float targetY = userHandView.getY() + targetYs[i];
            float targetRotation = targetRotations[i];
            Log.d("HandCards", "Card " + i + " target position: x=" + targetX + ", y=" + targetY + ", rotation=" + targetRotation);

            ObjectAnimator moveX = ObjectAnimator.ofFloat(cardView, "x", startX, targetX);
            ObjectAnimator moveY = ObjectAnimator.ofFloat(cardView, "y", startY, targetY);
            ObjectAnimator rotate = ObjectAnimator.ofFloat(cardView, "rotation", 0f, targetRotation);
            ObjectAnimator scaleX = ObjectAnimator.ofFloat(cardView, "scaleX", 0f, 1f);
            ObjectAnimator scaleY = ObjectAnimator.ofFloat(cardView, "scaleY", 0f, 1f);

            AnimatorSet cardAnimator = new AnimatorSet();
            cardAnimator.playTogether(moveX, moveY, rotate, scaleX, scaleY);
            cardAnimator.setDuration(250);
            cardAnimators.add(cardAnimator);

            Log.d("HandCards", "Created animation for card " + i + " to position: x=" + targetX + ", y=" + targetY + ", rotation=" + targetRotation + " with scale from (0,0) to (1,1)");
        }

        for (int i = 0; i < cardAnimators.size(); i++) {
            if (i == 0) {
                fullAnimatorSet.play(cardAnimators.get(i));
            } else {
                fullAnimatorSet.play(cardAnimators.get(i)).after(cardAnimators.get(i - 1)).after(125);
            }
        }

        fullAnimatorSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                Log.d("HandCards", "Initial cards animation completed");
                userHandView.setCards(initialCards);
                Log.d("HandCards", "Cards laid out in userHandView after animation");

                for (ImageView cardView : animatedCards) {
                    rootLayout.removeView(cardView);
                    Log.d("HandCards", "Removed animated card from rootLayout");
                }

                userHandView.setInitialAnimationPending(false);

                userHandView.post(() -> {
                    int count = userHandView.getCards().size();
                    if (count > 0) {
                        for (int i = 0; i < count; i++) {
                            ImageView cardView = (ImageView) userHandView.getChildAt(i);
                            Log.d("HandCards", "Card " + i + " position: x=" + cardView.getX() + ", y=" + cardView.getY());
                            Log.d("HandCards", "Card " + i + " size: width=" + cardView.getWidth() + ", height=" + cardView.getHeight());
                            Log.d("HandCards", "Card " + i + " rotation: " + cardView.getRotation());
                        }
                    }
                });
            }
        });

        fullAnimatorSet.start();
        Log.d("HandCards", "Started animation for initial 4 cards");
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

        float[] tableCardSize = tableView.getTableCardSize();
        float targetWidth = tableCardSize[0];
        float targetHeight = tableCardSize[1];

        // بررسی مقادیر نامعتبر و استفاده از مقادیر پیش‌فرض
        if (targetWidth <= 0 || targetHeight <= 0 || Float.isNaN(targetWidth) || Float.isNaN(targetHeight)) {
            Log.w("animation", "Invalid table card size, using default values: width=240, height=360");
            targetWidth = 240f;
            targetHeight = 360f;
        }
        Log.d("dimen", "Table card size: width=" + targetWidth + ", height=" + targetHeight);

        DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
        float tableAspectRatio = targetWidth / targetHeight;
        // بررسی tableAspectRatio برای جلوگیری از NaN
        if (Float.isNaN(tableAspectRatio) || tableAspectRatio <= 0) {
            Log.w("animation", "Invalid tableAspectRatio, using default value: 0.6667");
            tableAspectRatio = 0.6667f; // نسبت عرض به ارتفاع پیش‌فرض (240/360)
        }

        float handCardHeight = (int) (138 * displayMetrics.density);
        float handCardWidth = handCardHeight * tableAspectRatio;
        // بررسی handCardWidth برای جلوگیری از NaN
        if (Float.isNaN(handCardWidth) || handCardWidth <= 0) {
            Log.w("animation", "Invalid handCardWidth, using default value: 240");
            handCardWidth = 240f;
        }

        if (tableCardsToCollect == null || tableCardsToCollect.isEmpty()) {
            Log.d("animation", "No cards to collect, animating directly to table.");
            ImageView animatedCard = createAnimatedCard(playedCard);
            animatedCard.setX(startX);
            animatedCard.setY(startY);
            animatedCard.setRotation(startRotation);
            rootLayout.addView(animatedCard);
            Log.d("animation", "Added played card to rootLayout at position: (" + startX + ", " + startY + ")");

            animatedCard.setScaleX(1f);
            animatedCard.setScaleY(1f);

            float finalTargetWidth = targetWidth;
            float finalHandCardWidth = handCardWidth;
            float finalTargetHeight = targetHeight;
            tableView.post(() -> {
                float[] lastCardPosition = tableView.getLastCardPosition();
                float endX = tableView.getX() + lastCardPosition[0];
                float endY = tableView.getY() + lastCardPosition[1];
                Log.d("animation", "Destination Position - Title: Table (Last Card Position), Coordinates: (" + endX + ", " + endY + ")");

                float scaleX = finalTargetWidth / finalHandCardWidth;
                float scaleY = finalTargetHeight / handCardHeight;
                float finalWidth = finalHandCardWidth * scaleX;
                float finalHeight = handCardHeight * scaleY;
                Log.d("dimen", "Played card final size (on table): width=" + finalWidth + ", height=" + finalHeight);

                // بررسی مقادیر NaN یا نامعتبر برای scaleX و scaleY
                if (Float.isNaN(scaleX) || Float.isNaN(scaleY) || scaleX <= 0 || scaleY <= 0) {
                    Log.w("animation", "Invalid scale values, using default scale: 1.0");
                    scaleX = 1.0f;
                    scaleY = 1.0f;
                }

                ObjectAnimator moveX = ObjectAnimator.ofFloat(animatedCard, "x", startX, endX);
                ObjectAnimator moveY = ObjectAnimator.ofFloat(animatedCard, "y", startY, endY);
                ObjectAnimator rotate = ObjectAnimator.ofFloat(animatedCard, "rotation", startRotation, 0f);
                ObjectAnimator scaleXAnimator = ObjectAnimator.ofFloat(animatedCard, "scaleX", 1f, scaleX);
                ObjectAnimator scaleYAnimator = ObjectAnimator.ofFloat(animatedCard, "scaleY", 1f, scaleY);

                AnimatorSet animatorSet = new AnimatorSet();
                animatorSet.playTogether(moveX, moveY, rotate, scaleXAnimator, scaleYAnimator);
                animatorSet.setDuration(1000);
                animatorSet.start();
                Log.d("animation", "Started animation to table for card: " + playedCard.toString() + " with scale from (1,1) to (" + scaleX + "," + scaleY + ")");

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
            playedCardView.setScaleX(1f);
            playedCardView.setScaleY(1f);
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

                    tableCardViews.add(tableCardView);
                } else {
                    Log.w("animation", "Could not find table card view for: " + tableCard.toString());
                }
            }

            AnimatorSet animatorSet = new AnimatorSet();
            List<AnimatorSet> animators = new ArrayList<>();
            float lastX = startX;
            float lastY = startY;

            float finalScaleX = userCollectedCardsView.getWidth() / handCardWidth;
            float finalScaleY = userCollectedCardsView.getHeight() / handCardHeight;
            float finalWidth = handCardWidth * finalScaleX;
            float finalHeight = handCardHeight * finalScaleY;
            Log.d("dimen", "Played card final size (in collected cards): width=" + finalWidth + ", height=" + finalHeight);

            // بررسی مقادیر NaN یا نامعتبر برای finalScaleX و finalScaleY
            if (Float.isNaN(finalScaleX) || Float.isNaN(finalScaleY) || finalScaleX <= 0 || finalScaleY <= 0) {
                Log.w("animation", "Invalid final scale values, using default scale: 1.0");
                finalScaleX = 1.0f;
                finalScaleY = 1.0f;
            }

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

                displayMetrics = getResources().getDisplayMetrics();
                int cardWidth = (int) (120 * displayMetrics.density);
                float offsetX = cardWidth * 0.2f;
                overlapX += offsetX;

                List<ObjectAnimator> stageAnimators = new ArrayList<>();
                for (ImageView cardView : collectedCardViews) {
                    String cardName = (cardView == playedCardView) ? playedCard.toString() : tableCardsToCollect.get(collectedCardViews.indexOf(cardView) - 1).toString();
                    Log.d("animation", "Animating card: " + cardName + " from (" + lastX + ", " + lastY + ") to (" + overlapX + ", " + overlapY + ")");
                    stageAnimators.add(ObjectAnimator.ofFloat(cardView, "x", lastX, overlapX));
                    stageAnimators.add(ObjectAnimator.ofFloat(cardView, "y", lastY, overlapY));
                    stageAnimators.add(ObjectAnimator.ofFloat(cardView, "rotation", cardView.getRotation(), 0f));
                    if (cardView == playedCardView) {
                        float scaleX = targetWidth / handCardWidth;
                        float scaleY = targetHeight / handCardHeight;
                        // بررسی مقادیر NaN یا نامعتبر
                        if (Float.isNaN(scaleX) || Float.isNaN(scaleY) || scaleX <= 0 || scaleY <= 0) {
                            Log.w("animation", "Invalid scale values for played card, using default scale: 1.0");
                            scaleX = 1.0f;
                            scaleY = 1.0f;
                        }
                        stageAnimators.add(ObjectAnimator.ofFloat(cardView, "scaleX", cardView.getScaleX(), scaleX));
                        stageAnimators.add(ObjectAnimator.ofFloat(cardView, "scaleY", cardView.getScaleY(), scaleY));
                        Log.d("animation", "Scaling played card " + cardName + " to table size: (" + scaleX + ", " + scaleY + ")");
                    }
                }

                AnimatorSet stageAnimator = new AnimatorSet();
                stageAnimator.playTogether(stageAnimators.toArray(new ObjectAnimator[0]));
                stageAnimator.setDuration(1000);
                animators.add(stageAnimator);
                Log.d("animation", "Created overlap animation for step " + (i + 1));

                int finalI = i;
                float finalOverlapX = overlapX;
                float finalTargetHeight1 = targetHeight;
                float finalTargetWidth1 = targetWidth;
                float finalHandCardWidth1 = handCardWidth;
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
                        float scaleX = finalTargetWidth1 / finalHandCardWidth1;
                        float scaleY = finalTargetHeight1 / handCardHeight;
                        // بررسی مقادیر NaN یا نامعتبر
                        if (Float.isNaN(scaleX) || Float.isNaN(scaleY) || scaleX <= 0 || scaleY <= 0) {
                            Log.w("animation", "Invalid scale values for table card, using default scale: 1.0");
                            scaleX = 1.0f;
                            scaleY = 1.0f;
                        }
                        tableCardView.setScaleX(scaleX);
                        tableCardView.setScaleY(scaleY);
                        collectedCardViews.add(tableCardView);
                        Log.d("animation", "Added original table card to collectedCardViews: " + tableCard.toString());
                    }
                });

                lastX = overlapX;
                lastY = overlapY;
            }

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
                finalAnimators.add(ObjectAnimator.ofFloat(cardView, "scaleX", cardView.getScaleX(), finalScaleX));
                finalAnimators.add(ObjectAnimator.ofFloat(cardView, "scaleY", cardView.getScaleY(), finalScaleY));
                Log.d("animation", "Scaling card " + cardName + " to collected size: (" + finalScaleX + ", " + finalScaleY + ")");
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
                    animatorSet.play(animators.get(i)).after(animators.get(i - 1)).after(1000);
                }
            }

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
        // استفاده از ابعاد سازگار با CardContainerView.layoutTable
        int availableWidth = tableView.getWidth() - (10 * (4 + 1)); // padding و cardsPerRow=4
        int cardWidth = availableWidth / 4;
        int cardHeightPx = (int) (cardWidth * 1.5);

        // بررسی مقادیر نامعتبر
        if (cardWidth <= 0 || cardHeightPx <= 0 || tableView.getWidth() == 0) {
            Log.w("animation", "Invalid table card size in createAnimatedCard, using default values: width=240, height=360");
            cardWidth = (int) (120 * displayMetrics.density);
            cardHeightPx = (int) (cardWidth * 1.5);
        }

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