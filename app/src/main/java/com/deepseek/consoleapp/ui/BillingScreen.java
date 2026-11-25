package com.deepseek.consoleapp.ui;

import android.content.Intent;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.deepseek.consoleapp.ApiClient;
import com.deepseek.consoleapp.MainActivity;
import com.deepseek.consoleapp.Ui;
import com.deepseek.consoleapp.UsageParser;
import com.deepseek.consoleapp.WebActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Map;

public class BillingScreen extends BaseScreen {
    private JSONObject costData;
    private JSONObject amountData;
    private final List<String> errors = new ArrayList<>();
    private int pending;

    public BillingScreen(MainActivity activity) {
        super(activity);
    }

    @Override
    public void reload() {
        costData = null;
        amountData = null;
        errors.clear();
        pending = 2;
        loading("正在读取账单…");
        Calendar nowCal = Calendar.getInstance(java.util.TimeZone.getTimeZone("GMT+08:00"));
        Calendar monthStart = Calendar.getInstance(java.util.TimeZone.getTimeZone("GMT+08:00"));
        monthStart.set(nowCal.get(Calendar.YEAR), nowCal.get(Calendar.MONTH), 1, 0, 0, 0);
        monthStart.set(Calendar.MILLISECOND, 0);
        long start = monthStart.getTimeInMillis() / 1000;
        long end = (System.currentTimeMillis() / 86400000 + 1) * 86400 - 28800;
        String range = "?start=" + start + "&end=" + end + "&tz=28800";

        ApiClient.get("/api/v0/usage/by_api_key/cost" + range, activity.token(), new ApiClient.Callback() {
            @Override public void onSuccess(JSONObject data) { finish("cost", data, null); }
            @Override public void onError(String message, int code) { finish("cost", null, message); }
        });
        ApiClient.get("/api/v0/usage/by_api_key/amount" + range, activity.token(), new ApiClient.Callback() {
            @Override public void onSuccess(JSONObject data) { finish("amount", data, null); }
            @Override public void onError(String message, int code) { finish("amount", null, message); }
        });
    }

    private synchronized void finish(String which, JSONObject data, String error) {
        if ("cost".equals(which)) costData = data;
        if ("amount".equals(which)) amountData = data;
        if (error != null) errors.add(which + "：" + error);
        pending--;
        if (pending == 0) activity.runOnUiThread(this::render);
    }

    private void render() {
        clear();
        title("账单");
        Calendar cal = Calendar.getInstance();
        subtitle(String.format(Locale.CHINA, "%d 年 %d 月 · 数据来自开放平台", cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1));

        Map<String, UsageParser.Day> costDays = UsageParser.parseByKeyCost(costData, 28800);
        Map<String, UsageParser.Day> amountDays = UsageParser.parseByKeyAmount(amountData, 28800);
        String currency = "¥";

        UsageParser.Day monthTotal = costDays.get("__total__");
        double total = monthTotal == null ? 0 : monthTotal.cost;

        LinearLayout summary = Ui.card(getContext());
        summary.addView(Ui.text(getContext(), "本月消费合计", 14, Ui.MUTED, false));
        summary.addView(Ui.space(getContext(), 6));
        summary.addView(Ui.text(getContext(), currency + String.format(Locale.US, "%,.2f", total), 28, Ui.INK, true));
        summary.addView(Ui.space(getContext(), 8));
        UsageParser.Day tokenTotal = amountDays.get("__total__");
        long hit = tokenTotal == null ? 0 : tokenTotal.hit;
        long miss = tokenTotal == null ? 0 : tokenTotal.miss;
        long output = tokenTotal == null ? 0 : tokenTotal.output;
        addRow(summary, "缓存命中 / 未命中", Ui.count(hit) + " / " + Ui.count(miss));
        addRow(summary, "输出 Token", Ui.count(output));
        content.addView(summary);

        if (!errors.isEmpty()) {
            LinearLayout warn = Ui.card(getContext());
            warn.addView(Ui.text(getContext(), "部分账单数据不可用", 13, 0xFFC0392B, true));
            for (String e : errors) warn.addView(Ui.muted(getContext(), "· " + e, 11));
            content.addView(warn);
        }

        java.util.Set<String> dateSet = new java.util.TreeSet<>();
        dateSet.addAll(costDays.keySet());
        dateSet.addAll(amountDays.keySet());
        dateSet.remove("__total__");
        List<String> dates = new ArrayList<>(dateSet);
        java.util.Collections.reverse(dates);

        LinearLayout list = Ui.card(getContext());
        list.addView(Ui.text(getContext(), "按日明细", 15, Ui.INK, true));
        list.addView(Ui.space(getContext(), 10));
        if (dates.isEmpty()) {
            list.addView(Ui.muted(getContext(), "本月暂无账单数据。", 12));
        } else {
            for (String date : dates) {
                UsageParser.Day t = amountDays.get(date);
                UsageParser.Day c = costDays.get(date);
                long tokens = t == null ? 0 : (t.hit + t.miss + t.output);
                double cost = c == null ? 0 : c.cost;
                LinearLayout row = new LinearLayout(getContext());
                row.setOrientation(LinearLayout.HORIZONTAL);
                TextView left = Ui.text(getContext(), date, 12, Ui.MUTED, false);
                left.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.2f));
                TextView mid = Ui.text(getContext(), Ui.count(tokens) + " tokens", 11, Ui.MUTED, false);
                mid.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
                mid.setGravity(android.view.Gravity.CENTER);
                TextView right = Ui.text(getContext(), currency + String.format(Locale.US, "%,.2f", cost), 12, Ui.INK, true);
                right.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.8f));
                right.setGravity(android.view.Gravity.END);
                row.addView(left);
                row.addView(mid);
                row.addView(right);
                list.addView(row);
                list.addView(Ui.space(getContext(), 9));
            }
        }
        content.addView(list);

        Button official = Ui.button(getContext(), "在官网查看账单与发票", true);
        official.setOnClickListener(v -> {
            Intent intent = new Intent(getContext(), WebActivity.class);
            intent.putExtra("url", "https://platform.deepseek.com/transactions");
            getContext().startActivity(intent);
        });
        content.addView(official);
    }

    private void addRow(LinearLayout parent, String label, String value) {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        TextView l = Ui.text(getContext(), label, 12, Ui.MUTED, false);
        l.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView r = Ui.text(getContext(), value, 12, Ui.INK, true);
        r.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        r.setGravity(android.view.Gravity.END);
        row.addView(l);
        row.addView(r);
        parent.addView(row);
        parent.addView(Ui.space(getContext(), 7));
    }

}