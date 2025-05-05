package com.example.a4Barg.scene.game;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.graphics.Color;
import android.graphics.PorterDuff; // <-- Import اضافه شد
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log; // <-- برای لاگ‌گیری احتمالی اضافه شد
import android.view.View;
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
    private String gameId; // Note: gameId is declared but not initialized or used in the provided code
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

    // --- اضافه شد: Map برای نگهداری انیمیشن‌های چشمک‌زن کارت‌های انتخاب شده ---
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
                // --- پاک کردن انتخاب‌های قبلی قبل از شروع فرآیند جدید ---
                clearAllBlinkingSelections();
                tableView.clearHighlights(); // پاک کردن هایلایت‌های قبلی CardContainerView
                // ----------------------------------------------------

                viewModel.setPendingCard(card); // Set the pending card immediately
                List<Card> tableCards = tableView.getCards();
                int playedValue = getCardValue(card.getRank());
                List<List<Card>> combinations = findCombinations(tableCards, playedValue);

                // selectedTableCards should be already cleared by clearAllBlinkingSelections()
                // tableView.clearHighlights(); // Already called above

                if (combinations.isEmpty()) {
                    // هیچ ترکیبی وجود نداره، کارت به زمین اضافه بشه
                    viewModel.playCard(card, new ArrayList<>());
                } else if (combinations.size() == 1) {
                    // فقط یه ترکیب وجود داره، خودکار جمع بشه
                    viewModel.playCard(card, combinations.get(0));
                } else {
                    // چندین ترکیب ممکنه، کاربر باید انتخاب کنه
                    tableView.setSelectable(true);
                    showOptions(combinations); // Highlight possible options (alpha blink, no color)
                }
                viewModel.setLastDropPosition(dropX, dropY, rotation);
            }
        });

        // --- تغییرات اصلی در setOnCardSelectedListener ---
        tableView.setOnCardSelectedListener(card -> {
            if (viewModel.getPendingCard() == null) {
                Log.w("GameActivity", "Card selected but no pending card!");
                return;
            }

            ImageView cardView = null;
            int index = tableView.getCards().indexOf(card);
            if (index != -1) {
                View childView = tableView.getChildAt(index);
                // Ensure it's an ImageView before casting
                if (childView instanceof ImageView) {
                    cardView = (ImageView) childView;
                }
            }
            // Exit if we couldn't find the corresponding ImageView
            if (cardView == null) {
                Log.e("GameActivity", "Could not find ImageView for card: " + card.toString());
                return;
            }

            // --- منطق انتخاب / لغو انتخاب با استفاده از متدهای کمکی ---
            if (selectedTableCards.contains(card)) {
                // --- Deselect ---
                selectedTableCards.remove(card);
                stopBlinkingGreen(cardView); // توقف چشمک‌زن و حذف فیلتر سبز

                // Optional: Re-highlight general options if needed after deselect?
                // This might be complex if the deselected card was the *only* selection.
                // For now, rely on showOptions called initially.

            } else if (selectedTableCards.size() < tableView.getCards().size()) {
                // --- Select ---
                // First, stop any non-green blinking from showOptions if this card was highlighted there
                cardView.clearAnimation(); // Stop CardContainerView's potential alpha blink
                cardView.setAlpha(1f);     // Ensure full alpha before applying green blink

                selectedTableCards.add(card);
                startBlinkingGreen(cardView); // شروع چشمک‌زن سبز

                // Calculate total value of ONLY selected cards on table
                int currentSelectionValue = calculateTotalValue(selectedTableCards);
                // Get the value of the card played from hand
                int cardPlayedValue = getCardValue(viewModel.getPendingCard().getRank());

                if (cardPlayedValue + currentSelectionValue == 11) {
                    // Combination is 11, finalize play
                    List<Card> cardsToPlay = new ArrayList<>(selectedTableCards); // Copy selection *before* clearing
                    clearAllBlinkingSelections(); // Stop blinking, clear filters, clear local lists/maps
                    tableView.setSelectable(false); // Disable further selection
                    tableView.clearHighlights(); // Clear any highlights from showOptions
                    viewModel.playCard(viewModel.getPendingCard(), cardsToPlay); // Play the card

                } else if (cardPlayedValue + currentSelectionValue > 11) {
                    // Combination is invalid (over 11)
                    showError("مجموع " + (cardPlayedValue + currentSelectionValue) + " شد! ترکیب اشتباه است.");
                    clearAllBlinkingSelections(); // Stop blinking, clear filters, clear local lists/maps
                    tableView.setSelectable(true); // Allow selection again

                    // Re-show possible options
                    List<List<Card>> combinations = findCombinations(tableView.getCards(), cardPlayedValue);
                    if (!combinations.isEmpty()) {
                        showOptions(combinations); // Highlight possible cards (alpha blink, no color)
                    } else {
                        tableView.clearHighlights();
                    }
                } else {
                    // Combination is less than 11, continue selecting.
                    // The card is already blinking green via startBlinkingGreen.
                }
            }
        });
        // --- پایان تغییرات در setOnCardSelectedListener ---

        btnInGameMessage.setOnClickListener(v -> showMessageDialog());

        SocketManager.initialize(this, userId);
        viewModel.setupGameListeners(this);

        viewModel.startGame(roomNumber);

        // --- Observers ---
        viewModel.getUserCards().observe(this, this::updateUserHand);
        viewModel.getOpponentCardCount().observe(this, this::updateOpponentHand);
        viewModel.getTableCards().observe(this, tableCards -> {
            // When table cards update externally (e.g., opponent plays, deal),
            // the current selection might become invalid. Clear it.
            List<Card> currentSelection = new ArrayList<>(selectedTableCards);
            boolean selectionStillValid = true;
            if (!currentSelection.isEmpty()) {
                for(Card selectedCard : currentSelection) {
                    if (!tableCards.contains(selectedCard)) {
                        selectionStillValid = false;
                        break;
                    }
                }
                if(!selectionStillValid) {
                    Log.d("GameActivity", "Table changed, clearing invalid selection.");
                    clearAllBlinkingSelections();
                }
            }
            updateTableCards(tableCards); // Update the view with new cards
        });
        viewModel.getUserCollectedCards().observe(this, this::updateUserCollectedCards);
        viewModel.getOpponentCollectedCards().observe(this, this::updateOpponentCollectedCards);
        viewModel.getCurrentTurn().observe(this, turnUserId -> {
            // If turn changes, clear any pending selection state? Usually handled by playCard.
            // clearAllBlinkingSelections(); // Maybe needed if a player can pass turn while selecting?
            updateTurnIndicator(turnUserId);
        });
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
            // This observer is mainly for the ViewModel to potentially trigger
            // the display of options if logic dictates, but the initial trigger
            // is usually after onCardPlayed.
            if (options != null && !options.isEmpty()) {
                // Don't automatically call showOptions here unless intended
                // as it might override the user's current blinking selection.
                // tableView.setSelectable(true);
                // showOptions(options);
            } else {
                // tableView.setSelectable(false);
                // tableView.clearHighlights();
            }
        });
        viewModel.getGameOver().observe(this, gameOver -> {
            if (gameOver) {
                clearAllBlinkingSelections(); // --- توقف چشمک‌زن‌ها در پایان بازی ---
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

    // --- متدهای کمکی برای چشمک‌زن سبز ---

    private void startBlinkingGreen(ImageView cardView) {
        if (selectedBlinkingAnimators.containsKey(cardView)) {
            Log.w("GameActivity", "Already blinking green: " + cardView.toString());
            return; // Already blinking this view
        }
        // Apply green overlay tint - image underneath should remain visible
        cardView.setColorFilter(Color.GREEN, PorterDuff.Mode.OVERLAY); // Use OVERLAY

        // Create and start blinking animation (adjust alpha range as needed)
        ObjectAnimator animator = ObjectAnimator.ofFloat(cardView, "alpha", 0.6f, 1f); // Blink between 60% and 100% opaque
        animator.setDuration(500); // Blinking speed
        animator.setRepeatCount(ObjectAnimator.INFINITE);
        animator.setRepeatMode(ObjectAnimator.REVERSE);
        animator.start();

        selectedBlinkingAnimators.put(cardView, animator); // Store the animator
        Log.d("GameActivity", "Started blinking green: " + cardView.toString());
    }

    private void stopBlinkingGreen(ImageView cardView) {
        ObjectAnimator animator = selectedBlinkingAnimators.remove(cardView);
        if (animator != null) {
            animator.cancel(); // Stop the animation
        } else {
            // If no animator was found in our map, maybe it wasn't blinking green?
            // Still ensure filter and alpha are reset.
        }
        // Remove tint and restore full alpha
        cardView.setColorFilter(null);
        cardView.setAlpha(1f);
        Log.d("GameActivity", "Stopped blinking green: " + cardView.toString());
    }

    // Call this method whenever the current selection needs to be completely reset
    private void clearAllBlinkingSelections() {
        Log.d("GameActivity", "Clearing all blinking selections.");
        // Use a copy of the keys to avoid ConcurrentModificationException while iterating and removing
        List<ImageView> viewsToClear = new ArrayList<>(selectedBlinkingAnimators.keySet());
        for (ImageView view : viewsToClear) {
            // The remove operation happens inside stopBlinkingGreen
            stopBlinkingGreen(view);
        }
        // Map should be empty now, but clear for certainty
        selectedBlinkingAnimators.clear();
        // Also clear the logical list of selected cards
        selectedTableCards.clear();

        // It's generally safe to also clear CardContainerView's highlights here too,
        // as any new interaction requiring options will call showOptions again.
        tableView.clearHighlights();
    }

    // --- سایر متدها (بدون تغییر یا با تغییرات جزئی) ---

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
        handler.postDelayed(() -> textView.setVisibility(View.GONE), 5000); // 5 seconds
    }

    public void showError(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        // Consider clearing selection state on error as well?
        // clearAllBlinkingSelections(); // Already called in the listener when sum > 11
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
            case "Jack": return 11; // Jack might need special handling if it cannot be collected
            case "Queen": return 12;
            case "King": return 13;
            default: return 0;
        }
    }

    // This method seems unused now
    private List<Card> getCollectableCards(List<Card> tableCards, int playedValue) {
        Log.w("GameActivity", "getCollectableCards is likely unused.");
        return new ArrayList<>();
    }

    // Calculates value of a list of cards (used for selectedTableCards)
    private int calculateTotalValue(List<Card> cards) {
        int total = 0;
        for (Card card : cards) {
            total += getCardValue(card.getRank());
        }
        return total;
    }

    // Finds combinations on the table that sum to (11 - playedCardValue)
    private List<List<Card>> findCombinations(List<Card> tableCards, int playedCardValue) {
        List<List<Card>> result = new ArrayList<>();
        int target = 11 - playedCardValue;

        if (target <= 0) return result; // Cannot make 11 if played card is 11 or more

        List<Card> numericCards = new ArrayList<>();
        for (Card card : tableCards) {
            int value = getCardValue(card.getRank());
            // Only consider cards on table that can be part of a sum (e.g., <= 10, or Ace=1)
            // Jack, Queen, King usually cannot be picked up by numeric cards to make 11.
            if (value <= 10) { // Adjust this condition based on game rules (e.g., if Ace can be 11 sometimes)
                numericCards.add(card);
            }
            // Handle Jack pickup: If played card is Jack, can it pick up other Jacks?
            // Handle Sur scenario (sweeping table) - this seems handled elsewhere.
        }

        // Find all subsets of numericCards that sum exactly to target
        int n = numericCards.size();
        for (int i = 0; i < (1 << n); i++) { // Iterate through all possible subsets (2^n)
            List<Card> currentCombination = new ArrayList<>();
            int currentSum = 0;
            for (int j = 0; j < n; j++) {
                // Check if the j-th bit is set in i
                if ((i & (1 << j)) > 0) {
                    Card card = numericCards.get(j);
                    currentSum += getCardValue(card.getRank());
                    currentCombination.add(card);
                }
            }
            // Add the combination if the sum matches the target
            if (currentSum == target) {
                result.add(currentCombination);
            }
        }
        // Log.d("GameActivity", "Found " + result.size() + " combinations for target " + target);
        return result;
    }

    // Highlights possible cards to select from (uses CardContainerView's alpha blink)
    public void showOptions(List<List<Card>> options) {
        tableView.clearHighlights(); // Clear previous CardContainerView highlights
        List<Card> collectableCards = new ArrayList<>();
        for (List<Card> option : options) {
            for (Card card : option) {
                // Ensure we don't try to highlight a card already selected (blinking green)
                // Find the view for the card
                ImageView cardView = null;
                int index = tableView.getCards().indexOf(card);
                if (index != -1) {
                    View childView = tableView.getChildAt(index);
                    if (childView instanceof ImageView) {
                        cardView = (ImageView) childView;
                    }
                }

                // Only add to collectableCards if it's not already blinking green
                if (!collectableCards.contains(card) && cardView != null && !selectedBlinkingAnimators.containsKey(cardView)) {
                    collectableCards.add(card);
                }
            }
        }
        if (!collectableCards.isEmpty()) {
            Log.d("GameActivity", "Highlighting options (alpha blink): " + collectableCards.size());
            tableView.highlightCards(collectableCards, Color.argb(0, 0, 0, 0)); // Pass transparent color, CardContainerView handles alpha blink
        } else {
            Log.d("GameActivity", "No options to highlight (or they are already selected).");
        }
    }

    // This method seems related to viewModel logic, not view interaction directly anymore
    private void selectOption(List<Card> selectedOption) {
        Log.w("GameActivity", "selectOption method called, likely handled by viewModel now.");
        // This logic is now mostly inside the setOnCardSelectedListener
        // Card pendingCard = viewModel.getPendingCard();
        // ... validation ...
        // viewModel.selectOption(selectedOption); // ViewModel might handle the state change
    }

    // Validates if played card + collected cards == 11
    private boolean validateCombination(Card playedCard, List<Card> tableCardsToCollect) {
        int playedValue = getCardValue(playedCard.getRank());
        int sum = playedValue + calculateTotalValue(tableCardsToCollect);
        return sum == 11;
    }

    // --- Update View Methods ---
    private void updateUserHand(List<Card> cards) {
        userHandView.setCards(cards);
    }

    private void updateOpponentHand(Integer cardCount) {
        List<Card> opponentCards = new ArrayList<>();
        for (int i = 0; i < cardCount; i++) {
            // Use a placeholder or actual card back model if available
            opponentCards.add(new Card("unknown", "unknown")); // Assuming Card("unknown", "unknown") maps to card_back
        }
        opponentHandView.setCards(opponentCards);
    }

    private void updateTableCards(List<Card> tableCards) {
        tableView.setCards(tableCards);
        // Note: Blinking selections might need re-validation here, handled partly in observer
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
            userHandView.setEnabled(true); // Enable hand interaction
        } else {
            turnIndicator.setText("نوبت حریف");
            userHandView.setEnabled(false); // Disable hand interaction
            // If it's not user's turn, they shouldn't be able to select from table either
            clearAllBlinkingSelections();
            tableView.setSelectable(false);
        }
    }

    // --- Getters for Views (potentially used by ViewModel or other parts) ---
    public CardContainerView getTableView() {
        return tableView;
    }

    public CardContainerView getUserHandView() {
        return userHandView;
    }

    public CardContainerView getOpponentHandView() {
        return opponentHandView;
    }

    // --- Animation for card played from hand/opponent ---
    public void animateCard(Card card, boolean isUser, float startX, float startY, float startRotation, Runnable onAnimationEnd) {
        ImageView animatedCard = new ImageView(this);
        int resId = getResources().getIdentifier(card.getImageResourceName(), "drawable", getPackageName());
        animatedCard.setImageResource(resId != 0 ? resId : R.drawable.card_back);

        DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
        // Use consistent card size if possible, or fetch from resources/layout params
        int cardWidth = (int) (100 * displayMetrics.density); // Adjust size as needed
        int cardHeightPx = (int) (130 * displayMetrics.density); // Adjust size as needed
        ConstraintLayout.LayoutParams params = new ConstraintLayout.LayoutParams(cardWidth, cardHeightPx);
        animatedCard.setLayoutParams(params);

        animatedCard.setElevation(25f); // Make sure it's above other cards

        // Set initial position and rotation based on where the card was played from
        animatedCard.setX(startX);
        animatedCard.setY(startY);
        animatedCard.setRotation(startRotation);

        rootLayout.addView(animatedCard); // Add to the main layout for animation

        // Calculate target position on the table (e.g., next available slot)
        tableView.post(() -> { // Ensure table layout is calculated before getting position
            float[] lastCardPosition = tableView.getLastCardPosition(); // Get position from CardContainerView
            float endX = tableView.getX() + lastCardPosition[0]; // Target X relative to rootLayout
            float endY = tableView.getY() + lastCardPosition[1]; // Target Y relative to rootLayout

            // Adjust startY if needed (e.g., opponent's card starts higher)
            // float adjustedStartY = startY; // Keep original Y from parameters for now

            ObjectAnimator moveX = ObjectAnimator.ofFloat(animatedCard, "x", startX, endX);
            ObjectAnimator moveY = ObjectAnimator.ofFloat(animatedCard, "y", startY, endY);
            ObjectAnimator rotate = ObjectAnimator.ofFloat(animatedCard, "rotation", startRotation, 0f); // Rotate to 0 degrees on table

            AnimatorSet animatorSet = new AnimatorSet();
            animatorSet.playTogether(moveX, moveY, rotate);
            animatorSet.setDuration(800); // Animation duration (milliseconds)
            animatorSet.start();

            animatorSet.addListener(new android.animation.AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(android.animation.Animator animation) {
                    // Remove the temporary animated view *after* animation ends
                    rootLayout.removeView(animatedCard);
                    // Run the callback (e.g., to update the table view state in ViewModel/Activity)
                    if (onAnimationEnd != null) {
                        onAnimationEnd.run();
                    }
                    // Note: The actual card is added to the tableView's data structure
                    // by the updateTableCards method triggered by the ViewModel.
                    // This animation is purely visual.
                }
            });
        });
    }
}