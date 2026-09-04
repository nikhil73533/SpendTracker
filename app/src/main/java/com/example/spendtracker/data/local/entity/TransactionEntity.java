package com.example.spendtracker.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "transactions",
        indices = {
            @Index(value = {"transactionGroupId"}),
            @Index(value = {"status"}),
            @Index(value = {"status", "date", "type", "category"}),
            @Index(value = {"sourceTransactionId"}, unique = true)
        })
public class TransactionEntity {
    @PrimaryKey(autoGenerate = true)
    public int id;
    public double amount;
    public String category = "";
    @ColumnInfo(defaultValue = "")
    public String categoryEmoji = "";
    public String description = "";
    public String type = "EXPENSE";
    public long date;
    public String source = "";
    public String sender = "";
    public String upiId = "";
    public String receiverName = "";
    public String bankName = "";
    public String sourceType = "";
    @ColumnInfo(defaultValue = "1")
    public boolean isRead = true;
    
    // Transfer specific fields
    @ColumnInfo(defaultValue = "")
    public String fromAccount = "";
    @ColumnInfo(defaultValue = "")
    public String toAccount = "";
    @ColumnInfo(defaultValue = "0.0")
    public double fees = 0.0;

    // Transaction Group association (nullable)
    @ColumnInfo(defaultValue = "0")
    public int transactionGroupId = 0;

    // Soft delete: "ACTIVE" or "DELETED"
    @NonNull
    @ColumnInfo(defaultValue = "ACTIVE")
    public String status = "ACTIVE";

    // Deletion timestamp (0 means not deleted)
    @ColumnInfo(defaultValue = "0")
    public long deletedAt = 0L;

    // Statement-import metadata. The nullable sourceTransactionId permits historical/manual
    // transactions while giving imported rows a database-enforced duplicate key.
    public String sourceTransactionId;
    public String referenceNumber;
    @NonNull
    @ColumnInfo(defaultValue = "'UNKNOWN'")
    public String direction = "UNKNOWN";
    @NonNull
    @ColumnInfo(defaultValue = "'DATE_TIME'")
    public String timestampPrecision = "DATE_TIME";
    public String importBatchId;

    public TransactionEntity() {}

    public TransactionEntity(int id, double amount, String category, String description, String type, long date, String source, String sender, String upiId, String receiverName, String bankName, String sourceType) {
        this(id, amount, category, "", description, type, date, source, sender, upiId, receiverName, bankName, sourceType, "", "", 0.0);
    }

    public TransactionEntity(int id, double amount, String category, String description, String type, long date, String source, String sender, String upiId, String receiverName, String bankName, String sourceType, String fromAccount, String toAccount, double fees) {
        this(id, amount, category, "", description, type, date, source, sender, upiId, receiverName, bankName, sourceType, fromAccount, toAccount, fees);
    }

    public TransactionEntity(int id, double amount, String category, String categoryEmoji, String description, String type, long date, String source, String sender, String upiId, String receiverName, String bankName, String sourceType, String fromAccount, String toAccount, double fees) {
        this.id = id;
        this.amount = amount;
        this.category = category != null ? category : "";
        this.categoryEmoji = categoryEmoji != null ? categoryEmoji : "";
        this.description = description != null ? description : "";
        this.type = type != null ? type : "EXPENSE";
        this.date = date;
        this.source = source != null ? source : "";
        this.sender = sender != null ? sender : "";
        this.upiId = upiId != null ? upiId : "";
        this.receiverName = receiverName != null ? receiverName : "";
        this.bankName = bankName != null ? bankName : "";
        this.sourceType = sourceType != null ? sourceType : "";
        this.fromAccount = fromAccount != null ? fromAccount : "";
        this.toAccount = toAccount != null ? toAccount : "";
        this.fees = fees;
        this.status = "ACTIVE";
        this.deletedAt = 0;
        this.transactionGroupId = 0;
    }
}
