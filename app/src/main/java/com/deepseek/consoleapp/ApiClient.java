package com.deepseek.consoleapp;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Thin HTTP client for DeepSeek Platform's private dashboard endpoints. */
public final class ApiClient {
    public static final String BASE = "https://platform.deepseek.com";
    public static final String USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) "
            + "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36";
    /** Diagnostics only: when true, dump raw API responses to logcat. */
    public static volatile boolean DEBUG_DUMP = false;

    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(4);

    public interface Callback {
        void onSuccess(JSONObject data);

        void onError(String message, int code);
    }

    private ApiClient() {
    }

    public static void get(String path, String token, Callback callback) {
        request("GET", path, token, null, callback);
    }

    public static void post(String path, String token, JSONObject body, Callback callback) {
        request("POST", path, token, body, callback);
    }

    private static void request(String method, String path, String token, JSONObject body, Callback callback) {
        EXECUTOR.execute(() -> {
            HttpURLConnection connection = null;
            try {
                String url = path.startsWith("http") ? path : BASE + path;
                connection = (HttpURLConnection) new URL(url).openConnection();
                connection.setRequestMethod(method);
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(20000);
                connection.setRequestProperty("Accept", "application/json, text/plain, */*");
                connection.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8");
                connection.setRequestProperty("User-Agent", USER_AGENT);
                connection.setRequestProperty("Origin", BASE);
                connection.setRequestProperty("Referer", BASE + "/usage");
                connection.setRequestProperty("x-app-version", "1.0.0");
                connection.setRequestProperty("x-client-platform", "web");
                connection.setRequestProperty("Authorization", "Bearer " + token);
                try {
                    String cookies = android.webkit.CookieManager.getInstance().getCookie(BASE);
                    if (cookies != null && !cookies.isEmpty()) connection.setRequestProperty("Cookie", cookies);
                } catch (Throwable ignored) {
                }

                if (body != null) {
                    connection.setDoOutput(true);
                    connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                    OutputStreamWriter writer = new OutputStreamWriter(connection.getOutputStream(), StandardCharsets.UTF_8);
                    writer.write(body.toString());
                    writer.flush();
                    writer.close();
                }

                int status = connection.getResponseCode();
                BufferedReader reader = new BufferedReader(new InputStreamReader(
                        status >= 400 ? connection.getErrorStream() : connection.getInputStream(),
                        StandardCharsets.UTF_8));
                StringBuilder raw = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) raw.append(line);
                reader.close();

                if (raw.length() == 0) {
                    fail(callback, "HTTP " + status + "：响应为空", status);
                    return;
                }

                if (DEBUG_DUMP) {
                    android.util.Log.d("DscDump", path.split("\\?")[0] + " RAW=" + raw.toString());
                }
                JSONObject response = new JSONObject(raw.toString());
                int code = response.optInt("code", 0);
                int bizCode = code;
                JSONObject data = response.optJSONObject("data");
                if (data != null && data.has("biz_code")) bizCode = data.optInt("biz_code", bizCode);

                if (status < 200 || status >= 300) {
                    fail(callback, response.optString("msg", "HTTP " + status), status);
                    return;
                }
                if (code != 0 || bizCode != 0) {
                    int finalCode = code != 0 ? code : bizCode;
                    String message = response.optString("msg", response.optString("message", "接口返回错误"));
                    if (finalCode == 40002 || finalCode == 40003) message = "登录已过期，请重新登录（" + finalCode + "）";
                    fail(callback, message, finalCode);
                    return;
                }
                if (data == null) {
                    fail(callback, "响应缺少 data 字段", code);
                    return;
                }
                callback.onSuccess(data);
            } catch (Exception e) {
                if (DEBUG_DUMP) android.util.Log.d("DscDump", "EXC on " + path + " : " + e.toString());
                fail(callback, e.getMessage() == null ? "网络请求失败" : e.getMessage(), -1);
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }

    /** Most dashboard responses put the real payload in data.biz_data. */
    public static JSONObject biz(JSONObject data) {
        if (data == null) return null;
        JSONObject inner = data.optJSONObject("biz_data");
        if (inner != null) return inner;
        if (data.optJSONArray("biz_data") != null) {
            try {
                return new JSONObject().put("items", data.optJSONArray("biz_data"));
            } catch (Exception e) {
                return data;
            }
        }
        return data;
    }

    private static void fail(Callback callback, String message, int code) {
        AuthWatcher.onApiError(code);
        if (callback != null) callback.onError(message, code);
    }
}
