package com.example.a4Barg.scene.game;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
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
import com.example.a4Barg.utils.CollectedCardsView;
import com.example.a4Barg.utils.HandView;
import com.example.a4Barg.utils.TableView;

import java.util.ArrayList;
import java.util.List;

public class GameActivity extends AppCompatActivity {

    private GameViewModel viewModel;
    private HandView userHandView;
    private HandView opponentHandView;
    private TableView tableView;
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

        userHandView.setShowCards(true);
        opponentHandView.setShowCards(false);

        userHandView.setOnCardPlayedListener(new HandView.OnCardPlayedListener() {
            @Override
            public void onCardPlayed(Card card, float dropX, float dropY, float rotation) {
                List<Card> tableCards = tableView.getCards();
                List<List<Card>> combinations = findCombinations(tableCards, getCardValue(card.getRank()));
                if (combinations.size() > 1) {
                    viewModel.playCard(card, new ArrayList<>());
                } else {
                    List<Card> cardsToCollect = combinations.isEmpty() ? new ArrayList<>() : combinations.get(0);
                    viewModel.playCard(card, cardsToCollect);
                }
                viewModel.setLastDropPosition(dropX, dropY, rotation);
            }

            @Override
            public void onCardPlayed(Card card) {
            }
        });

        tableView.setOnCardSelectedListener(card -> {
            List<List<Card>> combinations = viewModel.getPossibleCombinations().getValue();
            if (combinations == null || combinations.isEmpty()) {
                return;
            }

            if (selectedTableCards.contains(card)) {
                selectedTableCards.remove(card);
            } else {
                selectedTableCards.add(card);
            }
            tableView.updateSelection(selectedTableCards);

            for (List<Card> combo : combinations) {
                boolean isMatch = true;
                if (combo.size() == selectedTableCards.size()) {
                    for (Card selectedCard : selectedTableCards) {
                        boolean found = false;
                        for (Card comboCard : combo) {
                            if (selectedCard.getSuit().equals(comboCard.getSuit()) &&
                                    selectedCard.getRank().equals(comboCard.getRank())) {
                                found = true;
                                break;
                            }
                        }
                        if (!found) {
                            isMatch = false;
                            break;
                        }
                    }
                    if (isMatch) {
                        Card pendingCard = viewModel.getPendingCard();
                        if (pendingCard != null) {
                            viewModel.playCard(pendingCard, new ArrayList<>(selectedTableCards));
                            selectedTableCards.clear();
                            tableView.clearSelection();
                        }
                        break;
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
        viewModel.getTableCards().observe(this, this::updateTableCards);
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
        viewModel.getPossibleCombinations().observe(this, combinations -> {
            if (combinations != null && !combinations.isEmpty()) {
                tableView.setSelectable(true);
            } else {
                tableView.setSelectable(false);
                selectedTableCards.clear();
                tableView.clearSelection();
            }
        });
        viewModel.getGameOver().observe(this, gameOver -> {
            if (gameOver) {
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

    private List<List<Card>> findCombinations(List<Card> tableCards, int playedCardValue) {
        List<List<Card>> result = new ArrayList<>();
        int target = 11 - playedCardValue;

        List<Card> numericCards = new ArrayList<>();
        for (Card card : tableCards) {
            int value = getCardValue(card.getRank());
            if (value <= 10) {
                numericCards.add(card);
            }
        }

        for (int i = 1; i < (1 << numericCards.size()); i++) {
            List<Card> combination = new ArrayList<>();
            int sum = 0;
            for (int j = 0; j < numericCards.size(); j++) {
                if ((i & (1 << j)) > 0) {
                    Card card = numericCards.get(j);
                    sum += getCardValue(card.getRank());
                    combination.add(card);
                }
            }
            if (sum == target) {
                result.add(combination);
            }
        }
        return result;
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
        }
    }

    public TableView getTableView() {
        return tableView;
    }

    public HandView getUserHandView() {
        return userHandView;
    }

    public HandView getOpponentHandView() {
        return opponentHandView;
    }

    public void animateCard(Card card, boolean isUser, float startX, float startY, float startRotation, Runnable onAnimationEnd) {
        ImageView animatedCard = new ImageView(this);
        int resId = getResources().getIdentifier(card.getImageResourceName(), "drawable", getPackageName());
        animatedCard.setImageResource(resId != 0 ? resId : R.drawable.card_back);

        DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
        int cardWidth = (int) (120 * displayMetrics.density);
        int cardHeightPx = (int) (138 * displayMetrics.density);
        ConstraintLayout.LayoutParams params = new ConstraintLayout.LayoutParams(cardWidth, cardHeightPx);
        animatedCard.setLayoutParams(params);

        animatedCard.setElevation(25f);

        animatedCard.setX(startX);
        animatedCard.setY(startY);
        animatedCard.setRotation(startRotation);

        rootLayout.addView(animatedCard);

        tableView.post(() -> {
            float[] lastCardPosition = tableView.getLastCardPosition();
            float endX = tableView.getX() + lastCardPosition[0];
            float endY = tableView.getY() + lastCardPosition[1];

            float adjustedStartY = startY;
            if (!isUser) {
                adjustedStartY -= cardHeightPx;
            }

            ObjectAnimator moveX = ObjectAnimator.ofFloat(animatedCard, "x", startX, endX);
            ObjectAnimator moveY = ObjectAnimator.ofFloat(animatedCard, "y", adjustedStartY, endY);
            ObjectAnimator rotate = ObjectAnimator.ofFloat(animatedCard, "rotation", startRotation, 0f);

            AnimatorSet animatorSet = new AnimatorSet();
            animatorSet.playTogether(moveX, moveY, rotate);
            animatorSet.setDuration(1000);
            animatorSet.start();

            animatorSet.addListener(new android.animation.AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(android.animation.Animator animation) {
                    // اضافه کردن انیمیشن محو شدن (fade out) برای کارت انیمیشن‌شده
                    ObjectAnimator fadeOut = ObjectAnimator.ofFloat(animatedCard, "alpha", 1f, 0f);
                    fadeOut.setDuration(200); // مدت زمان محو شدن 200 میلی‌ثانیه
                    fadeOut.start();

                    // اجرای callback برای به‌روزرسانی TableView
                    if (onAnimationEnd != null) {
                        onAnimationEnd.run();
                    }

                    // اطمینان از رندر شدن TableView قبل از حذف کارت انیمیشن‌شده
                    tableView.post(() -> {
                        // حذف کارت انیمیشن‌شده پس از اطمینان از رندر شدن TableView
                        rootLayout.removeView(animatedCard);
                    });
                }
            });
        });
    }
}