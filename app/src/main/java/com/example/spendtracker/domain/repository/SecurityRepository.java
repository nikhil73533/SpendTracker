package com.example.spendtracker.domain.repository;

import androidx.lifecycle.LiveData;

public interface SecurityRepository {
    LiveData<Boolean> isPrivacyModeEnabled();
    void setPrivacyModeEnabled(boolean enabled);
    byte[] getDatabasePassphrase();
    String maskAmount(double amount);
    String maskPII(String value);
}
