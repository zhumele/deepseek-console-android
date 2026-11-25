package com.deepseek.consoleapp;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Stores the DeepSeek Platform session token in SharedPreferences, encrypted
 * with a device-backed AES key from AndroidKeyStore.
 */
public final class SecureTokenStore {
    private static final String PREF = "dsc_secure_prefs";
    private static final String KEY = "platform_token_v1";
    private static final String ALIAS = "dsc_platform_token_key";

    private SecureTokenStore() {
    }

    public static void save(Context context, String token) {
        if (context == null || token == null || token.trim().isEmpty()) return;
        try {
            Cipher cipher = cipher(context, Cipher.ENCRYPT_MODE, null);
            byte[] iv = cipher.getIV();
            byte[] encrypted = cipher.doFinal(token.trim().getBytes("UTF-8"));
            String payload = Base64.encodeToString(iv, Base64.NO_WRAP) + "."
                    + Base64.encodeToString(encrypted, Base64.NO_WRAP);
            prefs(context).edit().putString(KEY, payload).apply();
        } catch (Exception e) {
            // Fallback keeps the app usable on devices where KeyStore is unavailable.
            prefs(context).edit().putString(KEY, "plain." + token.trim()).apply();
        }
    }

    public static String load(Context context) {
        if (context == null) return null;
        String payload = prefs(context).getString(KEY, null);
        if (payload == null || payload.isEmpty()) return null;
        try {
            if (payload.startsWith("plain.")) return payload.substring(6);
            String[] parts = payload.split("\\.", 2);
            if (parts.length != 2) return null;
            byte[] iv = Base64.decode(parts[0], Base64.NO_WRAP);
            byte[] encrypted = Base64.decode(parts[1], Base64.NO_WRAP);
            Cipher cipher = cipher(context, Cipher.DECRYPT_MODE, iv);
            return new String(cipher.doFinal(encrypted), "UTF-8");
        } catch (Exception e) {
            return null;
        }
    }

    public static void clear(Context context) {
        if (context == null) return;
        prefs(context).edit().remove(KEY).apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    private static Cipher cipher(Context context, int mode, byte[] iv) throws Exception {
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);
        if (!keyStore.containsAlias(ALIAS)) {
            KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
            KeyGenParameterSpec spec = new KeyGenParameterSpec.Builder(
                    ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build();
            generator.init(spec);
            generator.generateKey();
        }
        SecretKey key = (SecretKey) keyStore.getKey(ALIAS, null);
        Cipher cipher = Cipher.getInstance(KeyProperties.KEY_ALGORITHM_AES + "/"
                + KeyProperties.BLOCK_MODE_GCM + "/" + KeyProperties.ENCRYPTION_PADDING_NONE);
        if (mode == Cipher.ENCRYPT_MODE) {
            cipher.init(Cipher.ENCRYPT_MODE, key);
        } else {
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, iv));
        }
        return cipher;
    }
}
