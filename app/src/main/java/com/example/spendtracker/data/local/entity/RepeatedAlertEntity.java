package com.example.spendtracker.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * Room entity for storing detected repeated/duplicate transaction alerts.
 * Each alert represents a pair of transactions that match by merchant + amount
 * within a configurable time window.
 */
@Entity(tableName = "repeated_transaction_alerts")
public class RepeatedAlertEntity {
    @PrimaryKey(autoGenerate = true)
    public int id;

    /** Merchant / receiver name that matched. */
    public String merchantName;

    /** Transaction amount (both transactions had this amount or close to it). */
    public double amount;

    /** Date of the first (earlier) transaction. */
    public long firstTransactionDate;

    /** Date of the second (later/newer) transaction. */
    public long secondTransactionDate;

    /** ID of the first transaction. */
    public int firstTransactionId;

    /** ID of the second transaction. */
    public int secondTransactionId;

    /** Whether this alert is enabled (user can disable per-alert). */
    @ColumnInfo(defaultValue = "1")
    public boolean enabled = true;

    /** Whether user has dismissed/reviewed this alert. */
    @ColumnInfo(defaultValue = "0")
    public boolean dismissed = false;

    /** Timestamp when this alert was created. */
    public long createdAt;

    /** Optional category from the transaction. */
    public String category;

    public RepeatedAlertEntity() {}

    public RepeatedAlertEntity(String merchantName, double amount,
                               long firstTransactionDate, long secondTransactionDate,
                               int firstTransactionId, int secondTransactionId,
                               String category) {
        this.merchantName = merchantName;
        this.amount = amount;
        this.firstTransactionDate = firstTransactionDate;
        this.secondTransactionDate = secondTransactionDate;
        this.firstTransactionId = firstTransactionId;
        this.secondTransactionId = secondTransactionId;
        this.category = category;
        this.enabled = true;
        this.dismissed = false;
        this.createdAt = System.currentTimeMillis();
    }
}
