package com.deepseek.consoleapp.ui;

import android.graphics.Color;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.deepseek.consoleapp.ApiClient;
import com.deepseek.consoleapp.MainActivity;
import com.deepseek.consoleapp.Ui;
import com.deepseek.consoleapp.UsageParser;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

public class UsageScreen extends BaseScreen {
    private JSONObject summaryData;
    private JSONObject amountData;
    private JSONObject costData;
    private final List<String> errors = new ArrayList<>();
    private int pending;
    private int tzSeconds;

    public UsageScreen(MainActivity activity) {
        super(activity);
    }

    @Override
    public void reload() {
        summaryData = null;
        amountData = null;
        costData = null;
        errors.clear();
        pending = 3;
        tzSeconds = -TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 1000;

        loading("正在读取余额与用量…");
        // Match the official page exactly: start/end are the first/last day of the
        // current month at 00:00 UTC, and tz is a POSITIVE 28800 (GMT+8 seconds).
        // "本月": 1st of the current month 00:00 GMT+8 through tomorrow 00:00 GMT+8.
        java.util.Calendar nowCal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("GMT+08:00"));
        java.util.Calendar monthStart = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("GMT+08:00"));
        monthStart.set(nowCal.get(java.util.Calendar.YEAR), nowCal.get(java.util.Calendar.MONTH), 1, 0, 0, 0);
        monthStart.set(java.util.Calendar.MILLISECOND, 0);
        long start = monthStart.getTimeInMillis() / 1000;
        long end = (System.currentTimeMillis() / 86400000 + 1) * 86400 - 28800;
        int tz = 28800;
        String range = "?start=" + start + "&end=" + end + "&tz=" + tz;

        ApiClient.get("/api/v0/users/get_user_summary", activity.token(), new ApiClient.Callback() {
            @Override public void onSuccess(JSONObject data) { finish("summary", data, null); }
            @Override public void onError(String message, int code) { finish("summary", null, message); }
        });
        ApiClient.get("/api/v0/usage/by_api_key/amount" + range, activity.token(), new ApiClient.Callback() {
            @Override public void onSuccess(JSONObject data) { finish("amount", data, null); }
            @Override public void onError(String message, int code) { finish("amount", null, message); }
        });
        ApiClient.get("/api/v0/usage/by_api_key/cost" + range, activity.token(), new ApiClient.Callback() {
            @Override public void onSuccess(JSONObject data) { finish("cost", data, null); }
            @Override public void onError(String message, int code) { finish("cost", null, message); }
        });
    }

    private synchronized void finish(String which, JSONObject data, String error) {
        if ("summary".equals(which)) summaryData = data;
        if ("amount".equals(which)) amountData = data;
        if ("cost".equals(which)) costData = data;
        if (error != null) errors.add(which + "：" + error);
        pending--;
        if (pending > 0) return;
        activity.runOnUiThread(this::render);
    }

    private void render() {
        clear();
        title("用量信息");
        subtitle("数据来自 DeepSeek 开放平台，通常有约 5 分钟延迟。");

        if (summaryData == null && amountData == null && costData == null) {
            error(errors.isEmpty() ? "没有拿到数据" : errors.get(0));
            return;
        }

        renderWallets();
        renderTotals();
        if (!errors.isEmpty()) {
            LinearLayout card = Ui.card(getContext());
            card.addView(Ui.text(getContext(), "部分数据不可用", 13, 0xFFC0392B, true));
            for (String e : errors) card.addView(Ui.muted(getContext(), "· " + e, 11));
            content.addView(card);
        }
    }

    private void renderWallets() {
        LinearLayout card = Ui.card(getContext());
        card.addView(Ui.text(getContext(), "账户余额", 15, Ui.INK, true));
        card.addView(Ui.space(getContext(), 8));
        Map<String, Double> wallets = parseWallets(summaryData);
        if (wallets.isEmpty()) {
            card.addView(Ui.muted(getContext(), "未获取到余额信息", 12));
        } else {
            for (Map.Entry<String, Double> entry : wallets.entrySet()) {
                rowIn(card, entry.getKey(), Ui.money(entry.getValue(), entry.getKey()));
            }
        }
        content.addView(card);
    }

    private void renderTotals() {
        Map<String, UsageParser.Day> amountDays = UsageParser.parseByKeyAmount(amountData, 28800);
        Map<String, UsageParser.Day> costDays = UsageParser.parseByKeyCost(costData, 28800);
        UsageParser.Day at = amountDays.get("__total__");
        UsageParser.Day ct = costDays.get("__total__");

        double totalCost = ct == null ? 0 : ct.cost;
        long hit = at == null ? 0 : at.hit;
        long miss = at == null ? 0 : at.miss;
        long output = at == null ? 0 : at.output;
        long requests = at == null ? 0 : at.requests;

        LinearLayout card = Ui.card(getContext());
        card.addView(Ui.text(getContext(), "本月总览", 15, Ui.INK, true));
        card.addView(Ui.space(getContext(), 8));
        rowIn(card, "消费金额", UsageParser.currency(costData) + String.format(Locale.US, "%,.2f", totalCost));
        rowIn(card, "API 请求次数", requests > 0 ? Ui.count(requests) : "—");
        rowIn(card, "Token 合计", hit + miss + output > 0 ? Ui.count(hit + miss + output) : "—");
        rowIn(card, "缓存命中 / 未命中", Ui.count(hit) + " / " + Ui.count(miss));
        rowIn(card, "输出 Token", Ui.count(output));
        content.addView(card);

        // Merge per-day costs and build a top-10 daily spend chart.
        Map<String, UsageParser.Day> chartDays = new LinkedHashMap<>();
        for (Map.Entry<String, UsageParser.Day> e : costDays.entrySet()) {
            if ("__total__".equals(e.getKey())) continue;
            chartDays.put(e.getKey(), e.getValue());
        }
        if (chartDays.isEmpty()) return;
        double max = 0;
        for (UsageParser.Day d : chartDays.values()) max = Math.max(max, d.cost);

        List<String> dates = new ArrayList<>(chartDays.keySet());
        Collections.sort(dates, (a, b) -> Double.compare(chartDays.get(b).cost, chartDays.get(a).cost));
        LinearLayout chart = Ui.card(getContext());
        chart.addView(Ui.text(getContext(), "每日消费", 15, Ui.INK, true));
        chart.addView(Ui.space(getContext(), 10));
        int shown = 0;
        for (String date : dates) {
            if (shown++ >= 10) break;
            addBar(chart, date.substring(5), chartDays.get(date).cost, max);
        }
        content.addView(chart);
    }

    private void addBar(LinearLayout parent, String label, double value, double max) {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);

        TextView name = Ui.text(getContext(), label, 11, Ui.MUTED, false);
        name.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(getContext(), 44), ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout track = new LinearLayout(getContext());
        LinearLayout.LayoutParams trackLp = new LinearLayout.LayoutParams(0, Ui.dp(getContext(), 9), 1f);
        trackLp.setMargins(Ui.dp(getContext(), 6), 0, Ui.dp(getContext(), 8), 0);
        track.setLayoutParams(trackLp);
        track.setBackgroundColor(0xFFEDF0F6);
        LinearLayout fill = new LinearLayout(getContext());
        int width = max > 0 ? Math.max(2, (int) (270 * value / max)) : 2;
        fill.setLayoutParams(new LinearLayout.LayoutParams(width, Ui.dp(getContext(), 9)));
        fill.setBackgroundColor(Ui.BLUE);
        track.addView(fill);
        TextView amount = Ui.text(getContext(), String.format(Locale.US, "%,.2f", value), 11, Ui.INK, false);
        amount.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(getContext(), 62), ViewGroup.LayoutParams.WRAP_CONTENT));
        amount.setGravity(android.view.Gravity.END);

        row.addView(name);
        row.addView(track);
        row.addView(amount);
        parent.addView(row);
        parent.addView(Ui.space(getContext(), 7));
    }

    private void rowIn(LinearLayout parent, String label, String value) {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        TextView left = Ui.text(getContext(), label, 13, Ui.MUTED, false);
        left.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView right = Ui.text(getContext(), value, 13, Ui.INK, true);
        right.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.3f));
        right.setGravity(android.view.Gravity.END);
        row.addView(left);
        row.addView(right);
        parent.addView(row);
        parent.addView(Ui.space(getContext(), 8));
    }

    private static Map<String, Double> parseWallets(JSONObject data) {
        Map<String, Double> result = new LinkedHashMap<>();
        if (data == null) return result;
        JSONObject biz = ApiClient.biz(data);
        String[] keys = {"normal_wallets", "bonus_wallets", "wallets", "balance_infos"};
        for (String key : keys) {
            JSONArray arr = biz.optJSONArray(key);
            if (arr == null) continue;
            for (int i = 0; i < arr.length(); i++) {
                JSONObject item = arr.optJSONObject(i);
                if (item == null) continue;
                String currency = item.optString("currency", "CNY");
                double value = optDouble(item, "balance", optDouble(item, "total_balance", 0));
                result.put(currency, result.getOrDefault(currency, 0d) + value);
            }
        }
        return result;
    }
    private static double optDouble(JSONObject obj, String key, double fallback) {
        if (!obj.has(key) || obj.isNull(key)) return fallback;
        Object v = obj.opt(key);
        if (v instanceof Number) return ((Number) v).doubleValue();
        try { return Double.parseDouble(String.valueOf(v)); } catch (Exception e) { return fallback; }
    }
}