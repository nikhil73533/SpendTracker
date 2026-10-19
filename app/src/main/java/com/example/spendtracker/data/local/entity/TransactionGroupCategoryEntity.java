package com.example.spendtracker.data.local.entity;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.annotation.NonNull;

@Entity(tableName = "transaction_group_categories",
        primaryKeys = {"groupId", "categoryName"},
        foreignKeys = @ForeignKey(
            entity = TransactionGroupEntity.class,
            parentColumns = "id",
            childColumns = "groupId",
            onDelete = ForeignKey.CASCADE
        ),
        indices = @Index("groupId"))
public class TransactionGroupCategoryEntity {
    public int groupId;
    @NonNull
    public String categoryName;

    public TransactionGroupCategoryEntity() {}

    public TransactionGroupCategoryEntity(int groupId, @NonNull String categoryName) {
        this.groupId = groupId;
        this.categoryName = categoryName;
    }
}
