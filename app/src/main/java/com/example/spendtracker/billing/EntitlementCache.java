package com.example.spendtracker.billing;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKeys;
import dagger.hilt.android.qualifiers.ApplicationContext;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class EntitlementCache {
    private static final String PREFS = "premium_entitlement";
    private static final String STATUS = "status";
    private static final String PRODUCT_ID = "product_id";
    private static final String TYPE = "type";
    private static final String TOKEN_HASH = "token_hash";
    private static final String VERIFIED_AT = "verified_at";
    private static final String EXPIRES_AT = "expires_at";
    private final SharedPreferences preferences;

    @Inject
    public EntitlementCache(@ApplicationContext Context context) {
        SharedPreferences encrypted = null;
        try {
            String key = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC);
            encrypted = EncryptedSharedPreferences.create(PREFS, key, context,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM);
        } catch (Exception ignored) { }
        preferences = encrypted;
    }

    public PremiumState read() {
        if (preferences == null) return PremiumState.free();
        try {
            PremiumState.Status status = PremiumState.Status.valueOf(preferences.getString(STATUS, PremiumState.Status.FREE.name()));
            String type = preferences.getString(TYPE, "");
            PremiumPurchaseType purchaseType = type.isEmpty() ? null : PremiumPurchaseType.valueOf(type);
            return new PremiumState(status, preferences.getString(PRODUCT_ID, ""), purchaseType,
                    preferences.getString(TOKEN_HASH, ""), preferences.getLong(VERIFIED_AT, 0L),
                    preferences.getLong(EXPIRES_AT, 0L));
        } catch (RuntimeException ignored) {
            return PremiumState.free();
        }
    }

    public void write(PremiumState state) {
        if (preferences == null) return;
        preferences.edit().putString(STATUS, state.status.name()).putString(PRODUCT_ID, state.productId)
                .putString(TYPE, state.purchaseType == null ? "" : state.purchaseType.name())
                .putString(TOKEN_HASH, state.purchaseTokenHash).putLong(VERIFIED_AT, state.lastVerifiedAt)
                .putLong(EXPIRES_AT, state.expiresAt).apply();
    }

    public void clear() {
        if (preferences != null) preferences.edit().clear().apply();
    }

    public static String hashToken(String purchaseToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(purchaseToken.getBytes(StandardCharsets.UTF_8));
            return Base64.encodeToString(digest, Base64.NO_WRAP);
        } catch (Exception ignored) {
            return "";
        }
    }
}
