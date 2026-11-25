package com.deepseek.consoleapp;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Parses the legacy dashboard usage endpoints (/usage/amount, /usage/cost).
 * Their biz_data shape is:
 *   amount: {"total":[{model,usage:[{type,amount}...]}], "days":[{date,data:[{model,usage:[...]}]}]}
 *   cost:   [{currency,"total":[...],"days":[...]}]   (an array, one per currency)
 * usage type values: PROMPT_TOKEN, PROMPT_CACHE_HIT_TOKEN, PROMPT_CACHE_MISS_TOKEN,
 *                    RESPONSE_TOKEN, REQUEST
 */
public final class UsageParser {
    public static final class Day {
        public double cost;
        public long hit, miss, output, requests, prompt;
    }

    private UsageParser() {
    }

    /**
     * Parse by_api_key/amount: biz_data.series[].buckets[].usage.{RESPONSE_TOKEN,...}
     * Returns a map keyed by "yyyy-MM-dd" plus "__total__".
     */
    public static Map<String, Day> parseByKeyAmount(JSONObject data, int tzSeconds) {
        Map<String, Day> result = new LinkedHashMap<>();
        if (data == null) return result;
        JSONObject biz = ApiClient.biz(data);
        Day total = new Day();
        addAmountSeries(biz.optJSONArray("series"), tzSeconds, result, total);
        // Function calls (e.g. tool-search) have their own series; the official
        // UI includes them in the totals, so we must too.
        addAmountSeries(biz.optJSONArray("function_series"), tzSeconds, result, total);
        result.put("__total__", total);
        return result;
    }

    private static void addAmountSeries(JSONArray series, int tzSeconds,
                                        Map<String, Day> result, Day total) {
        if (series == null) return;
        for (int s = 0; s < series.length(); s++) {
            JSONArray buckets = series.optJSONObject(s).optJSONArray("buckets");
            if (buckets == null) continue;
            for (int i = 0; i < buckets.length(); i++) {
                JSONObject b = buckets.optJSONObject(i);
                if (b == null) continue;
                JSONObject usage = b.optJSONObject("usage");
                if (usage == null) continue;
                String date = dateKey(b.optLong("time", 0), tzSeconds);
                Day d = result.computeIfAbsent(date, k -> new Day());
                long hit = (long) num(usage.opt("PROMPT_CACHE_HIT_TOKEN"));
                long miss = (long) num(usage.opt("PROMPT_CACHE_MISS_TOKEN"));
                long out = (long) num(usage.opt("RESPONSE_TOKEN"));
                long req = (long) num(usage.opt("REQUEST"));
                d.hit += hit; d.miss += miss; d.output += out; d.requests += req;
                total.hit += hit; total.miss += miss; total.output += out; total.requests += req;
            }
        }
    }

    /** Parse by_api_key/cost: biz_data.data[].series[].buckets[].cost */
    public static Map<String, Day> parseByKeyCost(JSONObject data, int tzSeconds) {
        Map<String, Day> result = new LinkedHashMap<>();
        if (data == null) return result;
        JSONObject biz = ApiClient.biz(data);
        Day total = new Day();
        JSONArray currencies = biz.optJSONArray("data");
        if (currencies == null) return result;
        for (int c = 0; c < currencies.length(); c++) {
            JSONObject currency = currencies.optJSONObject(c);
            if (currency == null) continue;
            addCostSeries(currency.optJSONArray("series"), tzSeconds, result, total);
            // function_series (tool-search etc.) costs are part of the official total.
            addCostSeries(currency.optJSONArray("function_series"), tzSeconds, result, total);
        }
        result.put("__total__", total);
        return result;
    }

    private static void addCostSeries(JSONArray series, int tzSeconds,
                                      Map<String, Day> result, Day total) {
        if (series == null) return;
        for (int s = 0; s < series.length(); s++) {
            JSONArray buckets = series.optJSONObject(s).optJSONArray("buckets");
            if (buckets == null) continue;
            for (int i = 0; i < buckets.length(); i++) {
                JSONObject b = buckets.optJSONObject(i);
                if (b == null) continue;
                String date = dateKey(b.optLong("time", 0), tzSeconds);
                double cost = num(b.opt("cost"));
                result.computeIfAbsent(date, k -> new Day()).cost += cost;
                total.cost += cost;
            }
        }
    }

    private static String dateKey(long epochSeconds, int tzSeconds) {
        java.util.Calendar cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"));
        cal.setTimeInMillis((epochSeconds + tzSeconds) * 1000L);
        return String.format(java.util.Locale.US, "%04d-%02d-%02d",
                cal.get(java.util.Calendar.YEAR),
                cal.get(java.util.Calendar.MONTH) + 1,
                cal.get(java.util.Calendar.DAY_OF_MONTH));
    }

    /** Parse token counts from the /usage/amount response, keyed by date. */
    public static Map<String, Day> parseAmount(JSONObject data) {
        Map<String, Day> result = new LinkedHashMap<>();
        if (data == null) return result;
        JSONObject biz = ApiClient.biz(data);
        Day total = new Day();
        accumulateModels(biz.optJSONArray("total"), total, false);
        result.put("__total__", total);
        JSONArray days = biz.optJSONArray("days");
        Map<String, Day> parsed = parseDays(days, false);
        for (Map.Entry<String, Day> e : parsed.entrySet()) result.put(e.getKey(), e.getValue());
        return result;
    }

    /** Parse costs from the /usage/cost response, keyed by date. */
    public static Map<String, Day> parseCost(JSONObject data) {
        Map<String, Day> result = new LinkedHashMap<>();
        if (data == null) return result;
        JSONObject biz = ApiClient.biz(data);
        Day total = new Day();
        JSONArray arr = biz.optJSONArray("data");
        if (arr == null) arr = biz.optJSONArray("items");
        if (arr == null) arr = new JSONArray().put(biz);
        for (int i = 0; i < arr.length(); i++) {
            JSONObject c = arr.optJSONObject(i);
            if (c == null) continue;
            accumulateModels(c.optJSONArray("total"), total, true);
            Map<String, Day> parsed = parseDays(c.optJSONArray("days"), true);
            for (Map.Entry<String, Day> e : parsed.entrySet()) {
                Day d = result.computeIfAbsent(e.getKey(), k -> new Day());
                d.cost += e.getValue().cost;
            }
        }
        result.put("__total__", total);
        return result;
    }

    private static Map<String, Day> parseDays(JSONArray days, boolean costMode) {
        Map<String, Day> result = new LinkedHashMap<>();
        if (days == null) return result;
        for (int i = 0; i < days.length(); i++) {
            JSONObject day = days.optJSONObject(i);
            if (day == null) continue;
            String date = day.optString("date", "");
            if (date.isEmpty()) continue;
            Day d = result.computeIfAbsent(date, k -> new Day());
            accumulateModels(day.optJSONArray("data"), d, costMode);
        }
        return result;
    }

    private static void accumulateModels(JSONArray models, Day out, boolean costMode) {
        if (models == null) return;
        for (int i = 0; i < models.length(); i++) {
            JSONObject model = models.optJSONObject(i);
            if (model == null) continue;
            JSONArray usages = model.optJSONArray("usage");
            if (usages == null) continue;
            for (int j = 0; j < usages.length(); j++) {
                JSONObject u = usages.optJSONObject(j);
                if (u == null) continue;
                String type = u.optString("type", "");
                double amount = num(u.opt("amount"));
                if (costMode) {
                    // In cost responses, every usage item's amount is a money value.
                    out.cost += amount;
                } else {
                    switch (type) {
                        case "PROMPT_TOKEN": out.prompt += (long) amount; break;
                        case "PROMPT_CACHE_HIT_TOKEN": out.hit += (long) amount; break;
                        case "PROMPT_CACHE_MISS_TOKEN": out.miss += (long) amount; break;
                        case "RESPONSE_TOKEN":
                        case "COMPLETION_TOKEN": out.output += (long) amount; break;
                        case "REQUEST": out.requests += (long) amount; break;
                        default: break;
                    }
                }
            }
        }
    }

    /** Detect currency symbol from the cost response. */
    public static String currency(JSONObject data) {
        if (data == null) return "";
        JSONObject biz = ApiClient.biz(data);
        JSONArray arr = biz.optJSONArray("data");
        if (arr == null) arr = biz.optJSONArray("items");
        if (arr == null) arr = new JSONArray().put(biz);
        for (int i = 0; i < arr.length(); i++) {
            JSONObject c = arr.optJSONObject(i);
            if (c == null) continue;
            String cur = c.optString("currency", "");
            if (!cur.isEmpty()) return symbol(cur);
        }
        return "";
    }

    private static String symbol(String cur) {
        if ("CNY".equalsIgnoreCase(cur)) return "¥";
        if ("USD".equalsIgnoreCase(cur)) return "$";
        return cur + " ";
    }

    private static double num(Object raw) {
        if (raw instanceof Number) return ((Number) raw).doubleValue();
        try { return Double.parseDouble(String.valueOf(raw)); } catch (Exception e) { return 0; }
    }
}
