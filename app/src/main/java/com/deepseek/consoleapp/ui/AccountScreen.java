package com.deepseek.consoleapp.ui;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.http.SslError;
import android.webkit.CookieManager;
import android.webkit.SslErrorHandler;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.deepseek.consoleapp.LoginActivity;
import com.deepseek.consoleapp.MainActivity;
import com.deepseek.consoleapp.SecureTokenStore;
import com.deepseek.consoleapp.Ui;
import com.deepseek.consoleapp.WebActivity;

public class AccountScreen extends BaseScreen {
    public AccountScreen(MainActivity activity) {
        super(activity);
    }

    @Override
    public void reload() {
        clear();
        title("账号管理");
        subtitle("令牌保存在本机加密存储中，仅用于访问 DeepSeek 开放平台。");

        LinearLayout status = Ui.card(getContext());
        status.addView(Ui.text(getContext(), "登录状态", 14, Ui.MUTED, false));
        status.addView(Ui.space(getContext(), 6));
        String token = activity.token();
        status.addView(Ui.text(getContext(), token == null ? "未登录" : "已登录", 20, token == null ? 0xFFC0392B : 0xFF20865A, true));
        if (token != null) {
            status.addView(Ui.space(getContext(), 10));
            TextView masked = Ui.text(getContext(), "会话令牌：" + Ui.mask(token), 12, Ui.MUTED, false);
            status.addView(masked);
        }
        content.addView(status);

        Button relogin = Ui.button(getContext(), "重新登录 / 刷新令牌", true);
        relogin.setOnClickListener(v -> {
            Intent intent = new Intent(getContext(), LoginActivity.class);
            intent.putExtra("force", true);
            getContext().startActivity(intent);
            activity.finish();
        });
        content.addView(relogin);
        content.addView(Ui.space(getContext(), 10));

        Button manual = Ui.button(getContext(), "手动粘贴会话令牌", false);
        manual.setOnClickListener(v -> showTokenDialog());
        content.addView(manual);
        content.addView(Ui.space(getContext(), 10));

        Button official = Ui.button(getContext(), "打开官方开放平台", false);
        official.setOnClickListener(v -> {
            Intent intent = new Intent(getContext(), WebActivity.class);
            intent.putExtra("url", "https://platform.deepseek.com/usage");
            getContext().startActivity(intent);
        });
        content.addView(official);
        content.addView(Ui.space(getContext(), 10));

        Button logout = Ui.button(getContext(), "退出登录并清除令牌", false);
        logout.setOnClickListener(v -> confirmLogout());
        content.addView(logout);

        LinearLayout note = Ui.card(getContext());
        note.addView(Ui.space(getContext(), 8));
        note.addView(Ui.text(getContext(), "关于", 14, Ui.INK, true));
        note.addView(Ui.space(getContext(), 6));
        note.addView(Ui.muted(getContext(),
                "本应用使用 DeepSeek 开放平台的私有接口，接口可能随官网改版而变化。"
                        + "登录、支付与发票以官方页面为准；本应用不会保存支付信息，也不会上传你的令牌到任何第三方。", 11));
        content.addView(note);
    }

    private void showTokenDialog() {
        EditText input = new EditText(getContext());
        input.setHint("粘贴 userToken 的 value");
        new AlertDialog.Builder(getContext())
                .setTitle("手动设置会话令牌")
                .setMessage("在浏览器登录 platform.deepseek.com 后，打开开发者工具，执行：\nJSON.parse(localStorage.getItem('userToken')).value\n复制结果粘贴到下方。")
                .setView(input)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", (d, w) -> {
                    String value = input.getText().toString().trim();
                    if (value.isEmpty()) return;
                    SecureTokenStore.save(getContext(), value);
                    activity.setToken(value);
                    Ui.alert(getContext(), "已保存", "会话令牌已更新。");
                })
                .show();
    }

    private void confirmLogout() {
        new AlertDialog.Builder(getContext())
                .setTitle("退出登录")
                .setMessage("将清除本机保存的会话令牌和 WebView Cookie，确定继续？")
                .setNegativeButton("取消", null)
                .setPositiveButton("清除", (d, w) -> {
                    SecureTokenStore.clear(getContext());
                    try { CookieManager.getInstance().removeAllCookies(null); } catch (Exception ignored) { }
                    Intent intent = new Intent(getContext(), LoginActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    getContext().startActivity(intent);
                })
                .show();
    }
}
