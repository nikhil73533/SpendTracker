package com.example.prediction.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Stores per-category observation counts for a normalized merchant key.
 * Each row = one (merchantKey, category) pair.
 *
 * <p>Primary key is composite: (merchantKey, category).
 * Room requires a single @PrimaryKey column, so we use a composite string.
 */
@Entity(tableName = "merchant_category_stats")
public class MerchantCategoryStatsEntity {

    /** Composite key: "{merchantKey}|{category}" */
    @PrimaryKey
    @NonNull
    public String id;

    @NonNull
    public String merchantKey;

    @NonNull
    public String category;

    /** Raw observation count for this merchant+category combination. */
    public int count;

    /** Last corrected timestamp (epoch millis). Used to weight recent corrections higher. */
    public long lastSeenMs;

    public MerchantCategoryStatsEntity() {}

    public MerchantCategoryStatsEntity(@NonNull String merchantKey, @NonNull String category, int count, long lastSeenMs) {
        this.merchantKey = merchantKey;
        this.category = category;
        this.count = count;
        this.lastSeenMs = lastSeenMs;
        this.id = merchantKey + "|" + category;
    }
}
