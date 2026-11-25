package com.deepseek.consoleapp;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.ViewGroup;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.json.JSONObject;

/** Hosts official DeepSeek pages: login, top-up, invoices and other flows that must stay on the website. */
public class WebActivity extends Activity {
    private WebView webView;
    private boolean tokenInjected;
    private String topupAmount;
    private String topupPayment;
    private boolean topupApplied;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String url = getIntent().getStringExtra("url");
        if (url == null || url.isEmpty()) url = "https://platform.deepseek.com/usage";
        topupAmount = getIntent().getStringExtra("topup_amount");
        topupPayment = getIntent().getStringExtra("topup_payment");

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.BG);

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setBackgroundColor(Color.WHITE);
        // Reserve space for the status bar manually. Using root.setFitsSystemWindows
        // here conflicts with the WebView's own insets and inflates the bar to ~940px.
        int statusBarHeight = 0;
        int sbRes = getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (sbRes > 0) statusBarHeight = getResources().getDimensionPixelSize(sbRes);
        bar.setPadding(Ui.dp(this, 14), Ui.dp(this, 12) + statusBarHeight,
                Ui.dp(this, 14), Ui.dp(this, 12));
        bar.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView back = Ui.text(this, "‹ 返回", 15, Ui.BLUE, false);
        back.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(this, 64), ViewGroup.LayoutParams.WRAP_CONTENT));
        back.setOnClickListener(v -> finish());
        TextView label = Ui.text(this, "DeepSeek 开放平台", 15, Ui.INK, true);
        label.setGravity(android.view.Gravity.CENTER_VERTICAL);
        label.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        bar.addView(back);
        bar.addView(label);
        root.addView(bar);

        ProgressBar progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 3)));
        root.addView(progress);

        webView = new WebView(this);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        // Render the mobile layout at device width. The desktop UA + overview mode
        // made official pages zoom out and occupy only the top half of the screen.
        settings.setLoadWithOverviewMode(false);
        settings.setUseWideViewPort(false);
        settings.setTextZoom(100);
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return handleNavigation(request.getUrl().toString());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return handleNavigation(url);
            }

            @Override
            public void onPageFinished(WebView view, String pageUrl) {
                progress.setProgress(100);
                if (!tokenInjected && pageUrl.contains("platform.deepseek.com")) {
                    tokenInjected = true;
                    injectToken(() -> webView.reload());
                } else if (tokenInjected && !topupApplied && pageUrl.contains("/top_up")) {
                    topupApplied = true;
                    applyTopUpSelection();
                }
            }

            private boolean handleNavigation(String url) {
                if (url == null) return false;
                // Payment SDKs open external apps via custom schemes. WebView cannot
                // handle these itself, so forward them to the system (Alipay/WeChat).
                String lower = url.toLowerCase();
                if (lower.startsWith("alipay:") || lower.startsWith("alipays:")
                        || lower.startsWith("weixin:") || lower.startsWith("wechat:")
                        || lower.startsWith("wxpay:") || lower.startsWith("tenpay:")
                        || lower.startsWith("alipayqr:")) {
                    try {
                        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(intent);
                    } catch (Exception e) {
                        android.util.Log.d("DscNav", "no app for payment scheme");
                    }
                    return true;
                }
                // intent:// scheme (Chrome-style app launch, used by some payment pages)
                if (lower.startsWith("intent://")) {
                    try {
                        Intent intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME);
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(intent);
                        return true;
                    } catch (Exception e) {
                        android.util.Log.d("DscNav", "intent parse failed: " + e.getMessage());
                    }
                }
                // Payment cashier pages: Alipay/WeChat cashier HTTPS URLs cannot render
                // in our WebView (SSL errors / they need to hand off to the native app).
                // Open them externally so the payment app or system browser handles them.
                if (lower.contains("alipay.com/cashier") || lower.contains("mclient.alipay.com")
                        || lower.contains("open.alipay.com") || lower.contains("mobile.alipay.com")
                        || lower.contains("cashier.wechat.com") || lower.contains("pay.weixin.qq.com")
                        || lower.contains("wxpay.qq.com")) {
                    try {
                        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(intent);
                        return true;
                    } catch (Exception e) {
                        android.util.Log.d("DscNav", "no browser for cashier: " + e.getMessage());
                    }
                }
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
        setContentView(root);
        webView.loadUrl(url);
    }

    /** Pre-select the requested amount and payment method on the official top-up page. */
    private void applyTopUpSelection() {
        final String amount = topupAmount;
        final String payment = topupPayment;
        webView.postDelayed(() -> {
            StringBuilder js = new StringBuilder("(function(){try{");
            js.append("function pick(name,val){var els=document.querySelectorAll('input[name=\"'+name+'\"]');");
            js.append("for(var i=0;i<els.length;i++){if(els[i].value===val){");
            js.append("els[i].checked=true;");
            js.append("els[i].dispatchEvent(new Event('input',{bubbles:true}));");
            js.append("els[i].dispatchEvent(new Event('change',{bubbles:true}));");
            js.append("els[i].click();return true;}}return false;}");
            js.append("var r={};");
            if (amount != null && !amount.isEmpty()) {
                js.append("r.amount=pick('topUpAmount',").append(org.json.JSONObject.quote(amount)).append(");");
            }
            if (payment != null && !payment.isEmpty()) {
                js.append("r.payment=pick('paymentMethod',").append(org.json.JSONObject.quote(payment)).append(");");
            }
            js.append("return JSON.stringify(r);}catch(e){return 'ERR '+e;}})()");
            webView.evaluateJavascript(js.toString(), result ->
                    android.util.Log.d("DscTop", "applyTopUp=" + result));
        }, 800);
    }

    private void injectToken(Runnable after) {
        String token = SecureTokenStore.load(this);
        if (token == null) {
            after.run();
            return;
        }
        String encoded;
        try {
            encoded = new JSONObject().put("value", token).put("__version", "0").toString();
        } catch (Exception e) {
            after.run();
            return;
        }
        // Always overwrite: the page may hold a stale {"value":null} session.
        // Keep both localStorage and sessionStorage in sync with the valid token.
        String js = "(function(){try{"
                + "var v=" + JSONObject.quote(encoded) + ";"
                + "localStorage.setItem('userToken',v);"
                + "sessionStorage.setItem('userToken',v);"
                + "return 'set';}catch(e){return 'error';}})()";
        webView.evaluateJavascript(js, value -> after.run());
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }
}
