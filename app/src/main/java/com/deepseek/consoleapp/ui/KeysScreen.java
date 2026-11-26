package com.deepseek.consoleapp.ui;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.deepseek.consoleapp.ApiClient;
import com.deepseek.consoleapp.MainActivity;
import com.deepseek.consoleapp.Ui;

import org.json.JSONArray;
import org.json.JSONObject;

public class KeysScreen extends BaseScreen {
    public KeysScreen(MainActivity activity) {
        super(activity);
    }

    @Override
    public void reload() {
        loading("正在读取 API Keys…");
        ApiClient.get("/api/v0/users/get_api_keys", activity.token(), new ApiClient.Callback() {
            @Override
            public void onSuccess(JSONObject data) {
                activity.runOnUiThread(() -> render(ApiClient.biz(data)));
            }

            @Override
            public void onError(String message, int code) {
                activity.runOnUiThread(() -> error(message));
            }
        });
    }

    private void render(JSONObject biz) {
        clear();
        title("API Keys 管理");
        subtitle("密钥仅在创建时完整可见，请勿分享或写进客户端代码。");

        Button create = Ui.button(getContext(), "创建 API key", true);
        create.setOnClickListener(v -> showCreateDialog());
        content.addView(create);
        content.addView(Ui.space(getContext(), 14));

        JSONArray keys = null;
        if (biz != null) {
            keys = biz.optJSONArray("api_keys");
            if (keys == null) keys = biz.optJSONArray("items");
        }
        if (keys == null || keys.length() == 0) {
            LinearLayout card = Ui.card(getContext());
            card.addView(Ui.text(getContext(), "暂无 API key", 15, Ui.INK, true));
            card.addView(Ui.space(getContext(), 6));
            card.addView(Ui.muted(getContext(), "点击上方按钮创建第一个密钥。", 12));
            content.addView(card);
            return;
        }

        for (int i = 0; i < keys.length(); i++) {
            JSONObject key = keys.optJSONObject(i);
            if (key == null) continue;
            content.addView(keyCard(key));
        }
    }

    private LinearLayout keyCard(JSONObject key) {
        LinearLayout card = Ui.card(getContext());
        card.addView(Ui.text(getContext(), key.optString("name", "未命名"), 15, Ui.INK, true));
        card.addView(Ui.space(getContext(), 8));
        addRow(card, "Key", Ui.mask(key.optString("sensitive_id", key.optString("key", ""))));
        addRow(card, "Tracking ID", key.optString("tracking_id", "—"));
        addRow(card, "创建日期", formatDate(key.opt("created_at")));
        Object lastUse = key.has("last_use") ? key.opt("last_use") : key.opt("last_used_at");
        addRow(card, "最新使用", formatDate(lastUse));
        card.addView(Ui.space(getContext(), 6));

        LinearLayout actions = new LinearLayout(getContext());
        actions.setOrientation(LinearLayout.HORIZONTAL);
        Button copy = Ui.button(getContext(), "复制 Key", false);
        Button delete = Ui.button(getContext(), "删除", false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMargins(0, 0, Ui.dp(getContext(), 8), 0);
        copy.setLayoutParams(lp);
        delete.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        copy.setOnClickListener(v -> copy(Ui.mask(key.optString("sensitive_id", ""))));
        delete.setOnClickListener(v -> confirmDelete(key));
        actions.addView(copy);
        actions.addView(delete);
        card.addView(actions);
        return card;
    }

    private void addRow(LinearLayout parent, String label, String value) {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        TextView l = Ui.text(getContext(), label, 12, Ui.MUTED, false);
        l.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.8f));
        TextView r = Ui.text(getContext(), value, 12, Ui.INK, false);
        r.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.4f));
        r.setGravity(android.view.Gravity.END);
        row.addView(l);
        row.addView(r);
        parent.addView(row);
        parent.addView(Ui.space(getContext(), 6));
    }

    private void showCreateDialog() {
        EditText input = new EditText(getContext());
        input.setHint("例如：my-project");
        input.setSingleLine(true);
        new AlertDialog.Builder(getContext())
                .setTitle("创建 API key")
                .setMessage("请起一个便于识别的名称，单个账号最多 100 个 Key。")
                .setView(input)
                .setNegativeButton("取消", null)
                .setPositiveButton("创建", (dialog, which) -> {
                    String name = input.getText().toString().trim();
                    if (name.isEmpty()) {
                        Ui.alert(getContext(), "提示", "名称不能为空");
                        return;
                    }
                    JSONObject body = new JSONObject();
                    try {
                        body.put("action", "create");
                        body.put("name", name);
                        body.put("redacted_key", JSONObject.NULL);
                        body.put("created_at", JSONObject.NULL);
                        body.put("tracking_id", JSONObject.NULL);
                        body.put("is_exp", false);
                    } catch (Exception ignored) { }
                    loading("正在创建…");
                    ApiClient.post("/api/v0/users/edit_api_keys", activity.token(), body, new ApiClient.Callback() {
                        @Override public void onSuccess(JSONObject data) {
                            activity.runOnUiThread(() -> showCreated(ApiClient.biz(data)));
                        }
                        @Override public void onError(String message, int code) {
                            activity.runOnUiThread(() -> { Ui.alert(getContext(), "创建失败", message); reload(); });
                        }
                    });
                })
                .show();
    }

    private void showCreated(JSONObject biz) {
        String key = "";
        if (biz != null) {
            // Official response: biz_data.api_key = {name, sensitive_id (plaintext), tracking_id, ...}
            JSONObject apiKey = biz.optJSONObject("api_key");
            if (apiKey != null) {
                key = apiKey.optString("sensitive_id", apiKey.optString("key", ""));
            }
            if (key.isEmpty()) key = biz.optString("key", "");
            if (key.isEmpty() && biz.opt("api_key") instanceof String) {
                key = biz.optString("api_key");
            }
        }
        String finalKey = key;
        String message = finalKey.isEmpty()
                ? "密钥已创建，但响应中没有返回明文，请到官方页面查看。"
                : "请立即复制并保存，之后无法再次查看明文：\n\n" + finalKey;
        new AlertDialog.Builder(getContext())
                .setTitle("API key 已创建")
                .setMessage(message)
                .setPositiveButton("复制并关闭", (d, w) -> {
                    if (!finalKey.isEmpty()) copy(finalKey);
                    reload();
                })
                .setNeutralButton("关闭", (d, w) -> reload())
                .setCancelable(false)
                .show();
    }

    private void confirmDelete(JSONObject key) {
        String trackingId = key.optString("tracking_id", "");
        String redactedKey = key.optString("sensitive_id", "");
        Object createdRaw = key.opt("created_at");
        new AlertDialog.Builder(getContext())
                .setTitle("删除 API key")
                .setMessage("确定删除「" + key.optString("name", "未命名") + "」？此操作不可撤销。")
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", (d, w) -> {
                    JSONObject body = new JSONObject();
                    try {
                        body.put("action", "delete");
                        body.put("name", JSONObject.NULL);
                        body.put("redacted_key", redactedKey);
                        body.put("created_at", createdRaw);
                        body.put("tracking_id", trackingId);
                        body.put("is_exp", false);
                    } catch (Exception ignored) { }
                    loading("正在删除…");
                    ApiClient.post("/api/v0/users/edit_api_keys", activity.token(), body, new ApiClient.Callback() {
                        @Override public void onSuccess(JSONObject data) {
                            activity.runOnUiThread(KeysScreen.this::reload);
                        }
                        @Override public void onError(String message, int code) {
                            activity.runOnUiThread(() -> { Ui.alert(getContext(), "删除失败", message); reload(); });
                        }
                    });
                })
                .show();
    }

    private void copy(String value) {
        ClipboardManager cm = (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("deepseek-key", value));
        android.widget.Toast.makeText(getContext(), "已复制", android.widget.Toast.LENGTH_SHORT).show();
    }

    private static String formatDate(Object raw) {
        if (raw == null || "".equals(raw)) return "—";
        long ts;
        try {
            ts = raw instanceof Number ? ((Number) raw).longValue() : Long.parseLong(String.valueOf(raw));
        } catch (Exception e) {
            return String.valueOf(raw);
        }
        if (ts <= 0) return "—";
        // Values look like Unix seconds; normalize if milliseconds.
        if (ts > 100000000000L) ts /= 1000;
        java.text.SimpleDateFormat fmt = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.CHINA);
        fmt.setTimeZone(java.util.TimeZone.getDefault());
        return fmt.format(new java.util.Date(ts * 1000));
    }
}
