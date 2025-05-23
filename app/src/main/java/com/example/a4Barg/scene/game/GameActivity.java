package com.example.a4Barg.scene.game;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Intent;
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
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.lifecycle.ViewModelProvider;

import com.example.a4Barg.R;
import com.example.a4Barg.common.BaseActivity;
import com.example.a4Barg.model.Card;
import com.example.a4Barg.networking.SocketManager;
import com.example.a4Barg.scene.lobby.LobbyActivity;
import com.example.a4Barg.utils.CardContainerView;
import com.example.a4Barg.utils.CollectedCardsView;

import org.json.JSONException;
import org.json.JSONObject;

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
    private boolean isInitialUserHandAnimationComplete = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game);

        viewModel = new ViewModelProvider(this).get(GameViewModel.class);
        userId = getIntent().getStringExtra("userId");
        roomNumber = getIntent().getStringExtra("roomNumber");
        gameId = getIntent().getStringExtra("gameId");
        Log.d("GameActivity", "onCreate: userId=" + userId + ", gameId=" + gameId + ", roomNumber=" + roomNumber);
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

        // Set initial visibility of collected cards to GONE
        userCollectedCardsView.setVisibility(View.GONE);
        opponentCollectedCardsView.setVisibility(View.GONE);

        rootLayout.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                rootLayout.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                Log.d("GameActivity", "XML layout fully loaded: width=" + rootLayout.getWidth() + ", height=" + rootLayout.getHeight());
                Log.d("GameActivity", "tableView: width=" + tableView.getWidth() + ", height=" + tableView.getHeight());
                Log.d("GameActivity", "userHandView: width=" + userHandView.getWidth() + ", height=" + userHandView.getHeight());
                viewModel.sendPlayerReady(gameId, userId);
            }
        });

        userHandView.setOnCardPlayedListener((card, dropX, dropY, rotation) -> {
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
        });

        tableView.setOnCardSelectedListener(card -> {
            Log.d("GameActivity", "Card selected: " + card.toString() + ", pendingCard: " + (viewModel.getPendingCard() != null ? viewModel.getPendingCard().toString() : "null"));
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

            // بررسی حالت select_king_or_queen
            List<List<Card>> possibleOptions = viewModel.getPossibleOptions().getValue();
            boolean isKingOrQueenSelection = possibleOptions != null && !possibleOptions.isEmpty() && possibleOptions.get(0).size() == 1;

            if (isKingOrQueenSelection) {
                // برای حالت انتخاب King یا Queen، مستقیماً گزینه را انتخاب کنید
                List<Card> selectedOption = new ArrayList<>();
                selectedOption.add(card);
                clearAllBlinkingSelections();
                tableView.setSelectable(false);
                tableView.clearHighlights();
                viewModel.selectOption(selectedOption);
                Log.d("GameActivity", "Selected option for king/queen: " + card.toString());
                return;
            }

            // منطق انتخاب برای ترکیب‌های عددی
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
                    showError("مجموع " + (cardPlayedValue + currentSelectionValue) + " شد! ترکیب اشتباه است.", null);
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
        viewModel.setActivity(this);
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

        // Add listener for turn timer updates
        SocketManager.addCustomListener("turn_timer_update", data -> {
            try {
                String receivedGameId = data.getString("gameId");
                if (receivedGameId.equals(gameId)) {
                    String turnUserId = data.getString("userId");
                    int remainingTime = data.getInt("remainingTime");
                    runOnUiThread(() -> updateTurnIndicator(turnUserId, remainingTime));
                }
            } catch (JSONException e) {
                Log.e("GameActivity", "Error processing turn_timer_update: " + e.getMessage());
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

    // متد اصلاح‌شده showError برای نمایش AlertDialog با دکمه
    public void showError(String message, GameViewModel.DialogType dialogType) {
        runOnUiThread(() -> {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setMessage(message);
            builder.setCancelable(false); // جلوگیری از بستن دیالوگ با کلیک خارج از آن

            if (dialogType == GameViewModel.DialogType.EXIT) {
                builder.setTitle("باخت بازی");
                builder.setPositiveButton("خروج", (dialog, which) -> {
                    Log.d("GameActivity", "Exit button clicked, finishing activity");
                    finishAffinity();
                    System.exit(0); // بستن فعالیت بازی
                });
            } else if (dialogType == GameViewModel.DialogType.RETURN_TO_LOBBY) {
                builder.setTitle("برد بازی");
                builder.setPositiveButton("بازگشت به لابی", (dialog, which) -> {
                    Log.d("GameActivity", "Return to lobby button clicked, starting LobbyActivity");
                    Intent intent = new Intent(GameActivity.this, LobbyActivity.class);
                    intent.putExtra("userId", userId);
                    startActivity(intent);
                    finish(); // بستن فعالیت بازی پس از انتقال به لابی
                });
            } else {
                // برای خطاهای عمومی بدون دکمه خاص
                builder.setTitle("خطا");
                builder.setPositiveButton("تأیید", (dialog, which) -> dialog.dismiss());
            }

            AlertDialog dialog = builder.create();
            dialog.show();
        });
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
        Log.d("GameActivity", "Showing options, count: " + options.size());
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
            // اطمینان از تنظیم selectable پس از چیدمان کامل
            tableView.post(() -> {
                tableView.setSelectable(true);
                Log.d("GameActivity", "Table set to selectable after layout");
            });
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
                        if (cardView != null) {
                            Log.d("HandCards", "Card " + i + " position: x=" + cardView.getX() + ", y=" + cardView.getY());
                            Log.d("HandCards", "Card " + i + " size: width=" + cardView.getWidth() + ", height=" + cardView.getHeight());
                            Log.d("HandCards", "Card " + i + " rotation: " + cardView.getRotation());
                        } else {
                            Log.w("HandCards", "Card " + i + " view is null");
                        }
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
            return;
        }
        Log.d("TableCards", "Cards to display: " + tableCards.toString());

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
                        synchronizeTableViewCards(tableCards);
                        Log.d("TableCards", "Cards laid out in tableView");
                        float[] lastCardPos = tableView.getLastCardPosition();
                        Log.d("TableCards", "Last card position: x=" + lastCardPos[0] + ", y=" + lastCardPos[1]);
                        logTableCardPositions();
                    }
                });
            } else {
                int screenWidth = tableView.getWidth();
                int screenHeight = tableView.getHeight();
                Log.d("TableCards", "TableView dimensions: width=" + screenWidth + ", height=" + screenHeight);
                synchronizeTableViewCards(tableCards);
                Log.d("TableCards", "Cards laid out in tableView");
                float[] lastCardPos = tableView.getLastCardPosition();
                Log.d("TableCards", "Last card position: x=" + lastCardPos[0] + ", y=" + lastCardPos[1]);
                logTableCardPositions();
            }
        }
    }

    private void synchronizeTableViewCards(List<Card> tableCards) {
        int expectedCount = tableCards.size();
        int actualCount = tableView.getChildCount();
        if (actualCount != expectedCount) {
            Log.w("TableCards", "Mismatch in tableView children: expected=" + expectedCount + ", actual=" + actualCount);
            tableView.removeAllViews();
            tableView.setCards(tableCards);
        } else {
            tableView.setCards(tableCards);
        }
    }

    private void logTableCardPositions() {
        tableView.post(() -> {
            int count = tableView.getCards().size();
            int childCount = tableView.getChildCount();
            if (count != childCount) {
                Log.w("TableCards", "Mismatch between card list and views: cards=" + count + ", views=" + childCount);
            }
            for (int i = 0; i < count && i < childCount; i++) {
                View childView = tableView.getChildAt(i);
                if (childView instanceof ImageView) {
                    ImageView cardView = (ImageView) childView;
                    Log.d("TableCards", "Card " + i + " position: x=" + cardView.getX() + ", y=" + cardView.getY());
                    Log.d("TableCards", "Card " + i + " size: width=" + cardView.getWidth() + ", height=" + cardView.getHeight());
                } else {
                    Log.w("TableCards", "Card " + i + " view is not an ImageView or is null");
                }
            }
        });
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
                    int childCount = tableView.getChildCount();
                    if (count != childCount) {
                        Log.w("TableCards", "Mismatch after initial animation: cards=" + count + ", views=" + childCount);
                    }
                    for (int i = 0; i < count && i < childCount; i++) {
                        View childView = tableView.getChildAt(i);
                        if (childView instanceof ImageView) {
                            ImageView cardView = (ImageView) childView;
                            Log.d("TableCards", "Card " + i + " position: x=" + cardView.getX() + ", y=" + cardView.getY());
                            Log.d("TableCards", "Card " + i + " size: width=" + cardView.getWidth() + ", height=" + cardView.getHeight());
                        } else {
                            Log.w("TableCards", "Card " + i + " view is not an ImageView or is null");
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
                isInitialUserHandAnimationComplete = true;

                userHandView.post(() -> {
                    int count = userHandView.getCards().size();
                    for (int i = 0; i < count; i++) {
                        View childView = userHandView.getChildAt(i);
                        if (childView instanceof ImageView) {
                            ImageView cardView = (ImageView) childView;
                            Log.d("HandCards", "Card " + i + " position: x=" + cardView.getX() + ", y=" + cardView.getY());
                            Log.d("HandCards", "Card " + i + " size: width=" + cardView.getWidth() + ", height=" + cardView.getHeight());
                            Log.d("HandCards", "Card " + i + " rotation: " + cardView.getRotation());
                        } else {
                            Log.w("HandCards", "Card " + i + " view is not an ImageView or is null");
                        }
                    }
                });

                // Send initial animation complete request to server
                sendInitialAnimationComplete();
            }
        });

        fullAnimatorSet.start();
        Log.d("HandCards", "Started animation for initial 4 cards");
    }

    private void sendInitialAnimationComplete() {
        Log.d("GameActivity", "Sending initial_animation_complete request for gameId: " + gameId);
        viewModel.sendInitialAnimationComplete(gameId, userId);
    }

    private void updateUserCollectedCards(List<Card> cards) {
        userCollectedCardsView.setCards(cards);
        userCollectedCardsView.setVisibility(cards != null && !cards.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void updateOpponentCollectedCards(List<Card> cards) {
        opponentCollectedCardsView.setCards(cards);
        opponentCollectedCardsView.setVisibility(cards != null && !cards.isEmpty() ? View.VISIBLE : View.GONE);
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

    private void updateTurnIndicator(String turnUserId, int remainingTime) {
        String text = turnUserId.equals(userId) ?
                "نوبت شما: " + remainingTime + " ثانیه" :
                "نوبت حریف: " + remainingTime + " ثانیه";
        turnIndicator.setText(text);
        if (turnUserId.equals(userId)) {
            userHandView.setEnabled(true);
        } else {
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
        Log.d("animation", "Starting animateCard - Played Card: " + playedCard.toString() + ", Played by: " + (isUser ? "User" : "Opponent") + ", isAutomaticPlay: " + viewModel.isAutomaticPlay());
        Log.d("animation", "Initial Source Position - Title: " + (isUser ? "User Hand (Drop Location)" : "Opponent Hand Center") + ", Coordinates: (" + startX + ", " + startY + "), Rotation: " + startRotation);
        Log.d("animation", "Cards to Collect: " + (tableCardsToCollect == null ? "None" : tableCardsToCollect.toString()));

        // For automatic play by user, get the card's position from userHandView
        if (isUser && viewModel.isAutomaticPlay()) {
            int cardIndex = userHandView.getCards().indexOf(playedCard);
            if (cardIndex != -1 && cardIndex < userHandView.getChildCount()) {
                View childView = userHandView.getChildAt(cardIndex);
                if (childView instanceof ImageView) {
                    ImageView cardView = (ImageView) childView;
                    int[] location = new int[2];
                    cardView.getLocationOnScreen(location);
                    int[] rootLocation = new int[2];
                    rootLayout.getLocationOnScreen(rootLocation);
                    startX = location[0] - rootLocation[0];
                    startY = location[1] - rootLocation[1];
                    startRotation = cardView.getRotation();
                    Log.d("animation", "Adjusted Source Position for Automatic Play - Card: " + playedCard.toString() + ", Coordinates: (" + startX + ", " + startY + "), Rotation: " + startRotation);
                } else {
                    Log.w("animation", "Card view at index " + cardIndex + " is not an ImageView for card: " + playedCard.toString());
                }
            } else {
                Log.w("animation", "Card not found in userHandView or view not rendered for card: " + playedCard.toString());
                // Fallback to center of userHandView
                startX = userHandView.getX() + userHandView.getWidth() / 2f;
                startY = userHandView.getY() + userHandView.getHeight() / 2f;
                startRotation = 0f;
                Log.d("animation", "Using fallback position - Center of userHandView: (" + startX + ", " + startY + ")");
            }
        }

        float[] tableCardSize = tableView.getTableCardSize();
        float targetWidth = tableCardSize[0];
        float targetHeight = tableCardSize[1];

        if (targetWidth <= 0 || targetHeight <= 0 || Float.isNaN(targetWidth) || Float.isNaN(targetHeight)) {
            Log.w("animation", "Invalid table card size, using default values: width=240, height=360");
            targetWidth = 240f;
            targetHeight = 360f;
        }
        Log.d("dimen", "Table card size: width=" + targetWidth + ", height=" + targetHeight);

        DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
        float tableAspectRatio = targetWidth / targetHeight;
        if (Float.isNaN(tableAspectRatio) || tableAspectRatio <= 0) {
            Log.w("animation", "Invalid tableAspectRatio, using default value: 0.6667");
            tableAspectRatio = 0.6667f;
        }

        float handCardHeight = (int) (138 * displayMetrics.density);
        float handCardWidth = handCardHeight * tableAspectRatio;
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
            float finalStartX = startX;
            float finalStartY = startY;
            float finalStartRotation = startRotation;
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

                if (Float.isNaN(scaleX) || Float.isNaN(scaleY) || scaleX <= 0 || scaleY <= 0) {
                    Log.w("animation", "Invalid scale values, using default scale: 1.0");
                    scaleX = 1.0f;
                    scaleY = 1.0f;
                }

                ObjectAnimator moveX = ObjectAnimator.ofFloat(animatedCard, "x", finalStartX, endX);
                ObjectAnimator moveY = ObjectAnimator.ofFloat(animatedCard, "y", finalStartY, endY);
                ObjectAnimator rotate = ObjectAnimator.ofFloat(animatedCard, "rotation", finalStartRotation, 0f);
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

            View collectedView = isUser ? userCollectedCardsView : opponentCollectedCardsView;
            // Ensure collectedView has valid dimensions
            if (collectedView.getWidth() == 0 || collectedView.getHeight() == 0) {
                collectedView.post(() -> {
                    Log.d("animation", "Collected view dimensions after layout: width=" + collectedView.getWidth() + ", height=" + collectedView.getHeight());
                });
                Log.w("animation", "Collected view has invalid dimensions, using default scale: 1.0");
            }

            float finalScaleX = collectedView.getWidth() > 0 ? collectedView.getWidth() / handCardWidth : 1.0f;
            float finalScaleY = collectedView.getHeight() > 0 ? collectedView.getHeight() / handCardHeight : 1.0f;
            float finalWidth = handCardWidth * finalScaleX;
            float finalHeight = handCardHeight * finalScaleY;
            Log.d("dimen", "Played card final size (in collected cards): width=" + finalWidth + ", height=" + finalHeight);

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

                DisplayMetrics metrics = getResources().getDisplayMetrics();
                int cardWidth = (int) (120 * metrics.density);
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

            int[] collectedLocation = new int[2];
            int[] rootLocation = new int[2];
            rootLayout.getLocationOnScreen(rootLocation);

            // Ensure collectedView is laid out before getting location
            if (collectedView.getWidth() == 0 || collectedView.getHeight() == 0) {
                Log.w("animation", "Collected view not yet laid out, forcing measure and layout");
                collectedView.measure(View.MeasureSpec.makeMeasureSpec(rootLayout.getWidth(), View.MeasureSpec.AT_MOST),
                        View.MeasureSpec.makeMeasureSpec(rootLayout.getHeight(), View.MeasureSpec.AT_MOST));
                collectedView.layout(0, 0, collectedView.getMeasuredWidth(), collectedView.getMeasuredHeight());
            }

            collectedView.getLocationOnScreen(collectedLocation);

            float collectedX = collectedLocation[0] - rootLocation[0] + collectedView.getWidth() / 2f - playedCardView.getWidth() / 2f;
            float collectedY = collectedLocation[1] - rootLocation[1] + collectedView.getHeight() / 2f - playedCardView.getHeight() / 2f;

            // Fallback positions if coordinates are invalid
            if (collectedX == 0 && collectedY == 0) {
                Log.w("animation", "Invalid collected coordinates, using fallback position for " + (isUser ? "User" : "Opponent") + " Collected Cards");
                if (isUser) {
                    collectedX = rootLayout.getWidth() - collectedView.getWidth() - 20; // Right side
                    collectedY = rootLayout.getHeight() - collectedView.getHeight() - 20; // Bottom
                } else {
                    collectedX = 20; // Left side
                    collectedY = 20; // Top
                }
                Log.d("animation", "Fallback position set: (" + collectedX + ", " + collectedY + ")");
            }

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

            for (int i = 0; i < animators.size(); i++) {
                if (i == 0) {
                    animatorSet.play(animators.get(i));
                } else {
                    animatorSet.play(animators.get(i)).after(animators.get(i - 1)).after(1000);
                }
            }

            animatorSet.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    Log.d("animation", "Final animation to collected cards completed.");
                    for (ImageView cardView : collectedCardViews) {
                        rootLayout.removeView(cardView);
                        String cardName = (cardView == playedCardView) ? playedCard.toString() : tableCardsToCollect.get(collectedCardViews.indexOf(cardView) - 1).toString();
                        Log.d("animation", "Removed card from rootLayout: " + cardName);
                    }
                    for (int i = tableView.getChildCount() - 1; i >= 0; i--) {
                        View child = tableView.getChildAt(i);
                        if (child instanceof ImageView) {
                            tableView.removeView(child);
                            Log.d("animation", "Removed leftover view from table GLO");
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
        int availableWidth = tableView.getWidth() - (10 * (4 + 1));
        int cardWidth = availableWidth / 4;
        int cardHeightPx = (int) (cardWidth * 1.5);

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
        if (index != -1 && index < tableView.getChildCount()) {
            View childView = tableView.getChildAt(index);
            if (childView instanceof ImageView) {
                return (ImageView) childView;
            }
        }
        return null;
    }
}