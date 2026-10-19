package com.example.prediction.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/** One explicit label per transaction; re-editing replaces the old training example. */
@Entity(tableName = "category_feedback")
public class CategoryFeedbackEntity {
    @PrimaryKey @NonNull public String id = "";
    @NonNull public String merchant = "";
    @NonNull public String tokens = "";
    @NonNull public String type = "EXPENSE";
    @NonNull public String category = "";
    public long updatedAt;
}
