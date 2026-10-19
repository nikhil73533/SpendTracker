package com.example.prediction.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "merchant_stats")
public class MerchantStatsEntity {
    @PrimaryKey
    @NonNull
    public String merchantName;
    
    public int frequency;
    public double totalAmount;
    public double averageAmount;
    public String preferredCategory;
    public String lastCategory;
    public long lastTransactionDate;

    public MerchantStatsEntity(@NonNull String merchantName) {
        this.merchantName = merchantName;
    }
}
