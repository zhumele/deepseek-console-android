package com.deepseek.consoleapp.ui;

import android.content.Context;
import android.graphics.Color;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.deepseek.consoleapp.MainActivity;
import com.deepseek.consoleapp.Ui;

public abstract class BaseScreen extends ScrollView {
    protected final MainActivity activity;
    protected final LinearLayout content;

    public BaseScreen(MainActivity activity) {
        super(activity);
        this.activity = activity;
        setBackgroundColor(Ui.BG);
        content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(Ui.dp(activity, 16), Ui.dp(activity, 14), Ui.dp(activity, 16), Ui.dp(activity, 24));
        content.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        addView(content);
    }

    protected void clear() {
        content.removeAllViews();
    }

    protected void title(String value) {
        TextView tv = Ui.text(getContext(), value, 20, Ui.INK, true);
        tv.setPadding(0, 0, 0, Ui.dp(getContext(), 4));
        content.addView(tv);
    }

    protected void subtitle(String value) {
        content.addView(Ui.muted(getContext(), value, 12));
        content.addView(Ui.space(getContext(), 10));
    }

    protected void loading(String message) {
        clear();
        TextView tv = Ui.muted(getContext(), message, 13);
        tv.setPadding(0, Ui.dp(getContext(), 40), 0, 0);
        tv.setGravity(android.view.Gravity.CENTER_HORIZONTAL);
        content.addView(tv);
    }

    protected void error(String message) {
        clear();
        LinearLayout card = Ui.card(getContext());
        card.addView(Ui.text(getContext(), "加载失败", 15, 0xFFC0392B, true));
        card.addView(Ui.space(getContext(), 6));
        card.addView(Ui.muted(getContext(), message, 12));
        content.addView(card);
    }

    protected void row(String label, String value) {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        TextView left = Ui.text(getContext(), label, 13, Ui.MUTED, false);
        left.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView right = Ui.text(getContext(), value, 13, Ui.INK, true);
        right.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.4f));
        right.setGravity(android.view.Gravity.END);
        row.addView(left);
        row.addView(right);
        content.addView(row);
        content.addView(Ui.space(getContext(), 8));
    }

    public abstract void reload();
}
