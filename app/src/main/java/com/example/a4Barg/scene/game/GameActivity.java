package com.example.a4Barg.scene.game;

import android.app.AlertDialog;
import android.os.Bundle;
import android.util.Log;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import com.example.a4Barg.R;
import com.example.a4Barg.common.BaseActivity;
import com.example.a4Barg.model.Card;
import com.example.a4Barg.model.GameState;
import com.example.a4Barg.model.Player;
import com.example.a4Barg.networking.SocketManager;
import com.example.a4Barg.utils.HandView;

import java.util.List;

public class GameActivity extends BaseActivity {
    private static final String TAG = "GameActivity";
    private GameViewModel viewModel;
    private HandView handView;
    private LinearLayout playedCardsContainer;
    private ImageView deck;
    private TextView player1Cards, player2Cards;
    private TextView player1Username, player1Exp, player1Coins;
    private TextView player2Username, player2Exp, player2Coins;
    private TextView userUsername, userExp, userCoins;
    private String roomNumber;
    private String userId;
    private boolean isHost;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_game);

        // دریافت اطلاعات از Intent
        roomNumber = getIntent().getStringExtra("roomNumber");
        userId = getSharedPreferences("auth", MODE_PRIVATE).getString("userId", null);
        isHost = getIntent().getBooleanExtra("isHost", false);

        // مقداردهی اولیه View‌ها
        handView = findViewById(R.id.handView);
        playedCardsContainer = findViewById(R.id.played_cards_container);
        deck = findViewById(R.id.deck);
        player1Cards = findViewById(R.id.player1_cards);
        player2Cards = findViewById(R.id.player2_cards);
        player1Username = findViewById(R.id.player1_username);
        player1Exp = findViewById(R.id.player1_exp);
        player1Coins = findViewById(R.id.player1_coins);
        player2Username = findViewById(R.id.player2_username);
        player2Exp = findViewById(R.id.player2_exp);
        player2Coins = findViewById(R.id.player2_coins);
        userUsername = findViewById(R.id.user_username);
        userExp = findViewById(R.id.user_exp);
        userCoins = findViewById(R.id.user_coins);

        // تنظیم اطلاعات کاربر
        userUsername.setText(getSharedPreferences("auth", MODE_PRIVATE).getString("username", "User"));
        userExp.setText("EXP: 500");
        userCoins.setText("Coins: 500");

        // تنظیم ViewModel
        viewModel = new ViewModelProvider(this).get(GameViewModel.class);

        // تنظیم Listener برای بازی کردن کارت
        handView.setOnCardPlayedListener(card -> {
            viewModel.playCard(card); // تغییر به متد بدون roomNumber
        });

        // گوش دادن به کلیک روی دسته کارت‌ها برای کشیدن کارت
        deck.setOnClickListener(v -> {
            GameState state = viewModel.getGameState().getValue();
            if (state != null && state.getCurrentPlayerId().equals(userId)) {
                viewModel.drawCard(); // تغییر به متد بدون roomNumber
            } else {
                Toast.makeText(this, "نوبت شما نیست!", Toast.LENGTH_SHORT).show();
            }
        });

        // مشاهده تغییرات وضعیت بازی
        viewModel.getGameState().observe(this, this::updateGameState);

        // مشاهده تغییرات کارت‌های بازیکن
        viewModel.getPlayerCards().observe(this, this::updatePlayerCards);

        // مشاهده درخواست انتخاب خال
        viewModel.getChooseSuitEvent().observe(this, message -> {
            showSuitSelectionDialog();
        });

        // مشاهده پایان بازی
        viewModel.getGameEndedEvent().observe(this, message -> {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
            finish();
        });

        // اگر میزبان هستیم، درخواست start_game را ارسال می‌کنیم
        if (isHost) {
            viewModel.startGame(roomNumber);
            Log.d(TAG, "Host is starting the game for room: " + roomNumber);
        }
    }

    private void updateGameState(GameState state) {
        if (state == null) return;

        // به‌روزرسانی اطلاعات بازیکن‌ها
        List<Player> players = state.getPlayers();
        Player player1 = null, player2 = null, user = null;
        for (Player player : players) {
            if (player.getUserId().equals(userId)) {
                user = player;
            } else if (player1 == null) {
                player1 = player;
            } else {
                player2 = player;
            }
        }

        if (player1 != null) {
            player1Username.setText(player1.getUsername());
            player1Exp.setText("EXP: " + player1.getExperience());
            player1Coins.setText("Coins: " + player1.getCoins());
            player1Cards.setText(String.valueOf(player1.getCardCount()));
        }

        if (player2 != null) {
            player2Username.setText(player2.getUsername());
            player2Exp.setText("EXP: " + player2.getExperience());
            player2Coins.setText("Coins: " + player2.getCoins());
            player2Cards.setText(String.valueOf(player2.getCardCount()));
        }

        // به‌روزرسانی کارت وسط
        playedCardsContainer.removeAllViews();
        Card currentCard = state.getCurrentCard();
        if (currentCard != null) {
            ImageView cardView = new ImageView(this);
            int resId = getResources().getIdentifier(currentCard.getImageResourceName(), "drawable", getPackageName());
            if (resId != 0) {
                cardView.setImageResource(resId);
            } else {
                cardView.setImageResource(R.drawable.card_back);
            }
            cardView.setLayoutParams(new LinearLayout.LayoutParams(
                    (int) (100 * getResources().getDisplayMetrics().density),
                    (int) (150 * getResources().getDisplayMetrics().density)
            ));
            playedCardsContainer.addView(cardView);
        }

        // نمایش پیام جریمه اگر وجود داشته باشه
        if (state.getPenaltyCount() > 0) {
            Toast.makeText(this, "جریمه: باید " + state.getPenaltyCount() + " کارت بکشید!", Toast.LENGTH_SHORT).show();
        }
    }

    private void updatePlayerCards(List<Card> cards) {
        handView.removeAllViews();
        for (Card card : cards) {
            handView.addCard(card);
        }
    }

    private void showSuitSelectionDialog() {
        String[] suits = {"Hearts", "Diamonds", "Clubs", "Spades"};
        new AlertDialog.Builder(this)
                .setTitle("انتخاب خال")
                .setItems(suits, (dialog, which) -> {
                    String selectedSuit = suits[which];
                    viewModel.chooseSuit(selectedSuit); // تغییر به متد بدون roomNumber
                })
                .setCancelable(false)
                .show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        SocketManager.disconnect();
    }
}