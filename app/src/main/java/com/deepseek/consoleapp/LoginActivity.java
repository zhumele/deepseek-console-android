package com.deepseek.consoleapp;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

public class LoginActivity extends Activity {
    private WebView webView;
    private final Handler handler = new Handler();
    private boolean polling;
    private boolean done;
    private boolean forceLogin;
    private String lastUrl = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(R.string.app_name);

        boolean force = getIntent().getBooleanExtra("force", false);
        if (!force && SecureTokenStore.load(this) != null) {
            startActivity(new Intent(this, MainActivity.class));
            finish();
            return;
        }
        forceLogin = force;
        if (force) CookieManager.getInstance().removeAllCookies(null);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.BG);
        root.setFitsSystemWindows(true);

        TextView title = Ui.text(this, "登录开放平台", 20, Ui.INK, true);
        title.setPadding(Ui.dp(this, 16), Ui.dp(this, 16), Ui.dp(this, 16), Ui.dp(this, 4));
        root.addView(title);
        TextView hint = Ui.muted(this, "在官方页面完成登录，应用会自动读取会话令牌。", 12);
        hint.setPadding(Ui.dp(this, 16), 0, Ui.dp(this, 16), Ui.dp(this, 10));
        root.addView(hint);

        ProgressBar progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 3)));
        root.addView(progress);

        webView = new WebView(this);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setUserAgentString(ApiClient.USER_AGENT);
        CookieManager.getInstance().setAcceptCookie(true);
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                lastUrl = url;
                progress.setProgress(100);
                if (forceLogin && url.startsWith(ApiClient.BASE)) {
                    // A rejected token may still be cached in WebView storage.
                    // Drop it once before giving the user a fresh sign-in page.
                    forceLogin = false;
                    webView.evaluateJavascript(
                            "(function(){try{localStorage.removeItem('userToken');"
                                    + "sessionStorage.removeItem('userToken');}catch(e){}return 'ok';})()",
                            value -> webView.reload());
                    return;
                }
                tryExtractToken();
                startPolling();
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return false;
            }
        });
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                progress.setProgress(newProgress);
            }
        });
        webView.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        root.addView(webView);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setPadding(Ui.dp(this, 12), Ui.dp(this, 8), Ui.dp(this, 12), Ui.dp(this, 12));
        Button manual = Ui.button(this, "手动粘贴令牌", false);
        Button retry = Ui.button(this, "刷新页面", false);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMargins(0, 0, Ui.dp(this, 8), 0);
        manual.setLayoutParams(lp);
        retry.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        manual.setOnClickListener(v -> showManualTokenDialog());
        retry.setOnClickListener(v -> webView.reload());
        actions.addView(manual);
        actions.addView(retry);
        root.addView(actions);

        setContentView(root);
        webView.loadUrl("https://platform.deepseek.com/sign_in?next=%2Fusage");
    }

    private void startPolling() {
        if (polling || done) return;
        polling = true;
        handler.postDelayed(new Runnable() {
            int tries = 0;

            @Override
            public void run() {
                if (done) return;
                tryExtractToken();
                tries++;
                if (tries < 40 && !done) handler.postDelayed(this, 1500);
                else polling = false;
            }
        }, 1200);
    }

    private void tryExtractToken() {
        if (done) return;
        // Base64-encode in JS so '+' '/' '=' survive the JS bridge untouched.
        String js = "(function(){"
                + "var raw=localStorage.getItem('userToken')||sessionStorage.getItem('userToken')||'';"
                + "if(!raw) return '';"
                + "var t='';try{var o=JSON.parse(raw);t=o.value||o.token||'';}catch(e){t=raw;}"
                + "if(!t) return '';"
                + "return btoa(unescape(encodeURIComponent(t)));"
                + "})()";
        webView.evaluateJavascript(js, value -> {
            if (done || value == null || "null".equals(value)) return;
            try {
                String b64 = new JSONObject("{\"v\":" + value + "}").getString("v");
                String token = new String(android.util.Base64.decode(b64, android.util.Base64.DEFAULT),
                        java.nio.charset.StandardCharsets.UTF_8);
                if (token != null && token.length() > 20) accept(token);
            } catch (Exception ignored) {
            }
        });
    }

    private void accept(String token) {
        if (done) return;
        done = true;
        SecureTokenStore.save(this, token);
        Toast.makeText(this, "登录成功", Toast.LENGTH_SHORT).show();
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }

    private void showManualTokenDialog() {
        EditText input = new EditText(this);
        input.setHint("粘贴 userToken 的 value");
        new AlertDialog.Builder(this)
                .setTitle("手动输入会话令牌")
                .setMessage("在浏览器登录 platform.deepseek.com 后，按 F12 打开控制台执行：\nJSON.parse(localStorage.getItem('userToken')).value\n复制结果粘贴到下方。")
                .setView(input)
                .setNegativeButton("取消", null)
                .setPositiveButton("登录", (d, w) -> {
                    String value = input.getText().toString().trim();
                    if (value.isEmpty()) {
                        Toast.makeText(this, "令牌不能为空", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    accept(value);
                })
                .show();
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
