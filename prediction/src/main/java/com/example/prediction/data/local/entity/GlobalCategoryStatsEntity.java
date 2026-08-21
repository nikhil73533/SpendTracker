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

    @PrimaryKey
    @NonNull
    public String category;

    /** Total number of confirmed samples for this category. */
    public int count;

    public GlobalCategoryStatsEntity() {}

    public GlobalCategoryStatsEntity(@NonNull String category, int count) {
        this.category = category;
        this.count = count;
    }
}
