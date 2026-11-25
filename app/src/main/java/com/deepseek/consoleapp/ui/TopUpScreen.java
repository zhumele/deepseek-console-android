package com.deepseek.consoleapp.ui;

import android.content.Intent;
import android.widget.Button;
import android.widget.LinearLayout;

import com.deepseek.consoleapp.ApiClient;
import com.deepseek.consoleapp.MainActivity;
import com.deepseek.consoleapp.Ui;
import com.deepseek.consoleapp.WebActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.LinkedHashMap;
import java.util.Map;

public class TopUpScreen extends BaseScreen {
    private String selectedPayment = "alipay"; // alipay | wechat
    private Button alipayBtn;
    private Button wechatBtn;

    public TopUpScreen(MainActivity activity) {
        super(activity);
    }

    @Override
    public void reload() {
        loading("正在读取账户余额…");
        ApiClient.get("/api/v0/users/get_user_summary", activity.token(), new ApiClient.Callback() {
            @Override public void onSuccess(JSONObject data) {
                activity.runOnUiThread(() -> render(ApiClient.biz(data)));
            }
            @Override public void onError(String message, int code) {
                activity.runOnUiThread(() -> render(null));
            }
        });
    }

    private void render(JSONObject biz) {
        clear();
        title("充值");
        subtitle("支付由 DeepSeek 官方页面完成；本应用不接触支付信息。");

        LinearLayout card = Ui.card(getContext());
        card.addView(Ui.text(getContext(), "当前余额", 15, Ui.INK, true));
        card.addView(Ui.space(getContext(), 8));
        Map<String, Double> wallets = wallets(biz);
        if (wallets.isEmpty()) {
            card.addView(Ui.muted(getContext(), "未获取到余额，可在官方页面登录后查看。", 12));
        } else {
            for (Map.Entry<String, Double> e : wallets.entrySet()) {
                LinearLayout row = new LinearLayout(getContext());
                row.setOrientation(LinearLayout.HORIZONTAL);
                android.widget.TextView l = Ui.text(getContext(), e.getKey(), 13, Ui.MUTED, false);
                l.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
                android.widget.TextView r = Ui.text(getContext(), Ui.money(e.getValue(), e.getKey()), 15, Ui.INK, true);
                r.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
                r.setGravity(android.view.Gravity.END);
                row.addView(l);
                row.addView(r);
                card.addView(row);
                card.addView(Ui.space(getContext(), 8));
            }
        }
        content.addView(card);

        LinearLayout amounts = Ui.card(getContext());
        amounts.addView(Ui.text(getContext(), "选择金额", 14, Ui.INK, true));
        amounts.addView(Ui.space(getContext(), 6));
        amounts.addView(Ui.muted(getContext(), "选择档位后打开官方页面，将自动选好金额与支付方式。", 11));
        amounts.addView(Ui.space(getContext(), 12));
        String[] presets = {"10", "20", "50", "100", "300", "500"};
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < presets.length; i++) {
            Button chip = Ui.button(getContext(), "¥" + presets[i], false);
            chip.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 12);
            chip.setPadding(Ui.dp(getContext(), 2), Ui.dp(getContext(), 8),
                    Ui.dp(getContext(), 2), Ui.dp(getContext(), 8));
            chip.setMinWidth(0);
            chip.setMinHeight(0);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            lp.setMargins(0, 0, Ui.dp(getContext(), 4), Ui.dp(getContext(), 8));
            chip.setLayoutParams(lp);
            final String amount = presets[i];
            chip.setOnClickListener(v -> openOfficial(amount, selectedPayment));
            row.addView(chip);
        }
        amounts.addView(row);
        content.addView(amounts);

        // Payment method selector
        LinearLayout payCard = Ui.card(getContext());
        payCard.addView(Ui.text(getContext(), "支付方式", 14, Ui.INK, true));
        payCard.addView(Ui.space(getContext(), 6));
        LinearLayout payRow = new LinearLayout(getContext());
        payRow.setOrientation(LinearLayout.HORIZONTAL);
        alipayBtn = Ui.button(getContext(), "支付宝", "alipay".equals(selectedPayment));
        wechatBtn = Ui.button(getContext(), "微信支付", "wechat".equals(selectedPayment));
        LinearLayout.LayoutParams p1 = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        p1.setMargins(0, 0, Ui.dp(getContext(), 6), 0);
        alipayBtn.setLayoutParams(p1);
        wechatBtn.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        alipayBtn.setOnClickListener(v -> selectPayment("alipay"));
        wechatBtn.setOnClickListener(v -> selectPayment("wechat"));
        payRow.addView(alipayBtn);
        payRow.addView(wechatBtn);
        payCard.addView(payRow);
        content.addView(payCard);

        Button topUp = Ui.button(getContext(), "前往官方充值页（自定义金额）", true);
        topUp.setOnClickListener(v -> openOfficial(null, selectedPayment));
        content.addView(topUp);
    }

    private void selectPayment(String method) {
        selectedPayment = method;
        refreshPaymentButtons();
    }

    private void refreshPaymentButtons() {
        if (alipayBtn == null || wechatBtn == null) return;
        stylePayment(alipayBtn, "alipay".equals(selectedPayment));
        stylePayment(wechatBtn, "wechat".equals(selectedPayment));
    }

    private void stylePayment(Button b, boolean selected) {
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setCornerRadius(Ui.dp(getContext(), 10));
        bg.setColor(selected ? Ui.BLUE : android.graphics.Color.WHITE);
        bg.setStroke(1, Ui.BLUE);
        b.setBackground(bg);
        b.setTextColor(selected ? android.graphics.Color.WHITE : Ui.BLUE);
    }

    private void openOfficial(String amount, String payment) {
        Intent intent = new Intent(getContext(), WebActivity.class);
        intent.putExtra("url", "https://platform.deepseek.com/top_up");
        if (amount != null) intent.putExtra("topup_amount", amount);
        intent.putExtra("topup_payment", payment);
        getContext().startActivity(intent);
    }

    private static Map<String, Double> wallets(JSONObject biz) {
        Map<String, Double> result = new LinkedHashMap<>();
        if (biz == null) return result;
        String[] keys = {"normal_wallets", "bonus_wallets", "wallets", "balance_infos"};
        for (String key : keys) {
            JSONArray arr = biz.optJSONArray(key);
            if (arr == null) continue;
            for (int i = 0; i < arr.length(); i++) {
                JSONObject item = arr.optJSONObject(i);
                if (item == null) continue;
                String currency = item.optString("currency", "CNY");
                double value = item.optDouble("balance", item.optDouble("total_balance", 0));
                result.put(currency, result.getOrDefault(currency, 0d) + value);
            }
        }
        return result;
    }
}
