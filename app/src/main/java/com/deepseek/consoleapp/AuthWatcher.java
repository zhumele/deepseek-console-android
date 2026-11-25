package com.deepseek.consoleapp;

import android.content.Intent;

import java.util.concurrent.atomic.AtomicBoolean;
import java.lang.ref.WeakReference;

/**
 * Central handler for authentication failures (40002/40003) returned by the
 * DeepSeek dashboard APIs. Once a session is rejected, the stored token is
 * unusable, so we drop it and bring the user back to the official sign-in page
 * instead of leaving every tab showing a stale "login expired" error.
 */
public final class AuthWatcher {
    private static final AtomicBoolean HANDLED = new AtomicBoolean(false);
    private static WeakReference<MainActivity> ACTIVITY = new WeakReference<>(null);

    private AuthWatcher() {
    }

    public static void watch(MainActivity activity) {
        ACTIVITY = new WeakReference<>(activity);
        HANDLED.set(false);
    }

    public static void onApiError(int code) {
        if (code != 40002 && code != 40003) return;
        if (!HANDLED.compareAndSet(false, true)) return;
        MainActivity activity = ACTIVITY.get();
        if (activity == null || activity.isFinishing()) return;
        activity.runOnUiThread(() -> {
            SecureTokenStore.clear(activity);
            Intent intent = new Intent(activity, LoginActivity.class);
            intent.putExtra("force", true);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            activity.startActivity(intent);
            activity.finish();
        });
    }
}
