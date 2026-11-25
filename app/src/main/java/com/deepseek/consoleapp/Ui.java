package com.deepseek.consoleapp;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/** Small UI helpers so screens stay readable without XML layouts. */
public final class Ui {
    public static final int BLUE = 0xFF3F68D8;
    public static final int INK = 0xFF1B2333;
    public static final int MUTED = 0xFF6B7486;
    public static final int LINE = 0xFFE6E9F0;
    public static final int BG = 0xFFF5F7FB;

    private Ui() {
    }

    public static int dp(Context c, float value) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                c.getResources().getDisplayMetrics());
    }

    public static TextView text(Context c, CharSequence value, int sp, int color, boolean bold) {
        TextView tv = new TextView(c);
        tv.setText(value);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        tv.setTextColor(color);
        tv.setTypeface(null, bold ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        tv.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        return tv;
    }

    public static TextView muted(Context c, CharSequence value, int sp) {
        return text(c, value, sp, MUTED, false);
    }

    public static LinearLayout card(Context c) {
        LinearLayout box = new LinearLayout(c);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(c, 16), dp(c, 16), dp(c, 16), dp(c, 16));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.WHITE);
        bg.setCornerRadius(dp(c, 14));
        bg.setStroke(1, LINE);
        box.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(c, 12);
        box.setLayoutParams(lp);
        return box;
    }

    public static Button button(Context c, String label, boolean primary) {
        Button b = new Button(c);
        b.setText(label);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        b.setAllCaps(false);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(c, 10));
        bg.setColor(primary ? BLUE : Color.WHITE);
        bg.setStroke(1, primary ? BLUE : LINE);
        b.setBackground(bg);
        b.setTextColor(primary ? Color.WHITE : BLUE);
        b.setPadding(dp(c, 12), dp(c, 8), dp(c, 12), dp(c, 8));
        return b;
    }

    public static ViewSpace space(Context c, int dp) {
        return new ViewSpace(c, dp);
    }

    public static final class ViewSpace extends android.view.View {
        public ViewSpace(Context c, int sizeDp) {
            super(c);
            setLayoutParams(new LinearLayout.LayoutParams(1, dp(c, sizeDp)));
        }
    }

    public static void alert(Context c, String title, String message) {
        new AlertDialog.Builder(c)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("知道了", null)
                .show();
    }

    public static String mask(String value) {
        if (TextUtils.isEmpty(value)) return "—";
        int keep = Math.min(6, value.length() / 3);
        if (value.length() <= keep * 2 + 3) return value.substring(0, 1) + "***" + value.substring(value.length() - 1);
        return value.substring(0, keep) + "***" + value.substring(value.length() - keep);
    }

    public static String money(double amount, String currency) {
        String symbol = "CNY".equalsIgnoreCase(currency) ? "¥" : ("USD".equalsIgnoreCase(currency) ? "$" : "");
        return symbol + String.format(Locale.US, "%,.2f", amount);
    }

    public static String count(long value) {
        return String.format(Locale.US, "%,d", value);
    }

    public static String dateKey(long seconds, int tzSeconds) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        return sdf.format(new Date((seconds + tzSeconds) * 1000L));
    }
}
