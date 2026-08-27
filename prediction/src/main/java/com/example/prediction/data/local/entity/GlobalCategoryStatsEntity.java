package com.example.prediction.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Per-category global observation count (all merchants combined).
 * Used to compute P(category) — the global prior.
 */
@Entity(tableName = "global_category_stats")
public class GlobalCategoryStatsEntity {

    /** Composite key: "{transactionType}|{category}" */
    @PrimaryKey
    @NonNull
    public String id;

    @NonNull
    public String category;

    /** Transaction direction: "INCOME" or "EXPENSE". */
    @NonNull
    public String transactionType;

    /** Total number of confirmed samples for this category. */
    public int count;

    public GlobalCategoryStatsEntity() {}

    public GlobalCategoryStatsEntity(@NonNull String category, @NonNull String transactionType, int count) {
        this.category = category;
        this.transactionType = transactionType;
        this.count = count;
        this.id = transactionType + "|" + category;
    }
}
