package com.example.spendtracker.data.repository;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKeys;
import com.example.spendtracker.domain.repository.SecurityRepository;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import javax.inject.Inject;
import javax.inject.Singleton;
import dagger.hilt.android.qualifiers.ApplicationContext;

@Singleton
public class SecurityRepositoryImpl implements SecurityRepository {
    private static final String PREF_NAME = "secure_prefs";
    private static final String KEY_PRIVACY_MODE = "privacy_mode";
    private static final String KEY_DB_PASSPHRASE = "db_passphrase";

    private SharedPreferences sharedPreferences;
    private final MutableLiveData<Boolean> privacyMode = new MutableLiveData<>(true);

    @Inject
    public SecurityRepositoryImpl(@ApplicationContext Context context) {
        try {
            String masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC);
            sharedPreferences = EncryptedSharedPreferences.create(
                    PREF_NAME,
                    masterKeyAlias,
                    context,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );
            
            // Privacy mode starts as TRUE for session safety, or persistent?
            // "by default all the ammount ... show ***"
            // Let's keep it TRUE on every app launch for maximum safety.
            privacyMode.setValue(true);
            
        } catch (GeneralSecurityException | IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public LiveData<Boolean> isPrivacyModeEnabled() {
        return privacyMode;
    }

    @Override
    public void setPrivacyModeEnabled(boolean enabled) {
        privacyMode.setValue(enabled);
    }

    @Override
    public byte[] getDatabasePassphrase() {
        if (sharedPreferences == null) return "default_passphrase_1234567890123456".getBytes();
        
        String savedPass = sharedPreferences.getString(KEY_DB_PASSPHRASE, null);
        if (savedPass == null) {
            byte[] pass = new byte[32];
            new SecureRandom().nextBytes(pass);
            savedPass = android.util.Base64.encodeToString(pass, android.util.Base64.NO_WRAP);
            sharedPreferences.edit().putString(KEY_DB_PASSPHRASE, savedPass).apply();
        }
        return android.util.Base64.decode(savedPass, android.util.Base64.NO_WRAP);
    }

    @Override
    public String maskAmount(double amount) {
        Boolean masked = privacyMode.getValue();
        if (masked != null && masked) {
            return "***";
        }
        return String.format(java.util.Locale.getDefault(), "₹ %.2f", amount);
    }

    @Override
    public String maskPII(String value) {
        if (value == null || value.isEmpty()) return "";
        Boolean masked = privacyMode.getValue();
        if (masked != null && masked) {
            if (value.length() <= 2) return "**";
            return value.substring(0, 1) + "***" + value.substring(value.length() - 1);
        }
        return value;
    }
}
