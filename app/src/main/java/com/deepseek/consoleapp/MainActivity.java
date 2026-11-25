package com.deepseek.consoleapp;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.deepseek.consoleapp.ui.AccountScreen;
import com.deepseek.consoleapp.ui.BaseScreen;
import com.deepseek.consoleapp.ui.BillingScreen;
import com.deepseek.consoleapp.ui.KeysScreen;
import com.deepseek.consoleapp.ui.TopUpScreen;
import com.deepseek.consoleapp.ui.UsageScreen;

public class MainActivity extends Activity {
    private String token;
    private final BaseScreen[] screens = new BaseScreen[5];
    private final TextView[] tabs = new TextView[5];
    private FrameLayout container;
    private int current = -1;

    private static final String[] LABELS = {"用量", "Keys", "充值", "账单", "账号"};
    private static final int[] ICONS = {
            android.R.drawable.ic_menu_agenda,
            android.R.drawable.ic_menu_manage,
            android.R.drawable.ic_menu_add,
            android.R.drawable.ic_menu_recent_history,
            android.R.drawable.ic_menu_myplaces
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        token = SecureTokenStore.load(this);
        if (token == null) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }
        ApiClient.DEBUG_DUMP = getIntent().getBooleanExtra("dump", false);
        AuthWatcher.watch(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);
        root.setFitsSystemWindows(true);

        TextView header = Ui.text(this, "DeepSeek 控制台", 18, Ui.INK, true);
        header.setPadding(Ui.dp(this, 16), Ui.dp(this, 14), Ui.dp(this, 16), Ui.dp(this, 10));
        root.addView(header);

        container = new FrameLayout(this);
        container.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        root.addView(container);

        LinearLayout nav = new LinearLayout(this);
        nav.setOrientation(LinearLayout.HORIZONTAL);
        nav.setBackgroundColor(Color.WHITE);
        nav.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 8));
        for (int i = 0; i < LABELS.length; i++) {
            TextView tab = new TextView(this);
            tab.setText(LABELS[i]);
            tab.setTextSize(11);
            tab.setGravity(android.view.Gravity.CENTER);
            tab.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 4));
            tab.setCompoundDrawablesWithIntrinsicBounds(0, ICONS[i], 0, 0);
            tab.setCompoundDrawablePadding(Ui.dp(this, 3));
            tab.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            final int index = i;
            tab.setOnClickListener(v -> showTab(index));
            tabs[i] = tab;
            nav.addView(tab);
        }
        root.addView(nav);
        setContentView(root);
        showTab(0);
    }

    private void showTab(int index) {
        if (current == index) {
            screens[index].reload();
            return;
        }
        current = index;
        for (int i = 0; i < tabs.length; i++) {
            tabs[i].setTextColor(i == index ? Ui.BLUE : Ui.MUTED);
        }
        if (screens[index] == null) {
            switch (index) {
                case 0: screens[index] = new UsageScreen(this); break;
                case 1: screens[index] = new KeysScreen(this); break;
                case 2: screens[index] = new TopUpScreen(this); break;
                case 3: screens[index] = new BillingScreen(this); break;
                default: screens[index] = new AccountScreen(this); break;
            }
        }
        container.removeAllViews();
        container.addView(screens[index], new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        screens[index].reload();
    }

    public String token() {
        return token;
    }

    public void setToken(String value) {
        this.token = value;
    }
}
