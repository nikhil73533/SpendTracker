package com.example.prediction.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Stores per-category observation counts for a normalized merchant key.
 * Each row = one (merchantKey, type, category) pair.
 */
@Entity(tableName = "merchant_category_stats")
public class MerchantCategoryStatsEntity {

    /** Composite key: "{merchantKey}|{transactionType}|{category}" */
    @PrimaryKey
    @NonNull
    public String id;

    @NonNull
    public String merchantKey;

    @NonNull
    public String category;

    /** Transaction direction: "INCOME" or "EXPENSE". */
    @NonNull
    public String transactionType;

    /** Raw observation count for this merchant+category combination. */
    public int count;

    /** Last corrected timestamp (epoch millis). */
    public long lastSeenMs;

    public MerchantCategoryStatsEntity() {}

    public MerchantCategoryStatsEntity(@NonNull String merchantKey, @NonNull String category, @NonNull String transactionType, int count, long lastSeenMs) {
        this.merchantKey = merchantKey;
        this.category = category;
        this.transactionType = transactionType;
        this.count = count;
        this.lastSeenMs = lastSeenMs;
        this.id = merchantKey + "|" + transactionType + "|" + category;
    }
}
