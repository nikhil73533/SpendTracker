package com.example.spendtracker.data.local.entity;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "transactions",
        indices = {
            @Index(value = {"transactionGroupId"}),
            @Index(value = {"status"})
        })
public class TransactionEntity {
    @PrimaryKey(autoGenerate = true)
    public int id;
    public double amount;
    public String category;
    public String description;
    public String type;
    public long date;
    public String source;
    public String sender;
    public String upiId;
    public String receiverName;
    public String bankName;
    public String sourceType;
    public boolean isRead = true;
    
    // Transfer specific fields
    public String fromAccount;
    public String toAccount;
    public double fees;

    // Transaction Group association (nullable)
    @ColumnInfo(defaultValue = "0")
    public int transactionGroupId;

    // Soft delete: "ACTIVE" or "DELETED"
    @ColumnInfo(defaultValue = "ACTIVE")
    public String status = "ACTIVE";

    // Deletion timestamp (0 means not deleted)
    @ColumnInfo(defaultValue = "0")
    public long deletedAt;

    public TransactionEntity() {}

    public TransactionEntity(int id, double amount, String category, String description, String type, long date, String source, String sender, String upiId, String receiverName, String bankName, String sourceType) {
        this(id, amount, category, description, type, date, source, sender, upiId, receiverName, bankName, sourceType, null, null, 0.0);
    }

    public TransactionEntity(int id, double amount, String category, String description, String type, long date, String source, String sender, String upiId, String receiverName, String bankName, String sourceType, String fromAccount, String toAccount, double fees) {
        this.id = id;
        this.amount = amount;
        this.category = category;
        this.description = description;
        this.type = type;
        this.date = date;
        this.source = source;
        this.sender = sender;
        this.upiId = upiId;
        this.receiverName = receiverName;
        this.bankName = bankName;
        this.sourceType = sourceType;
        this.fromAccount = fromAccount;
        this.toAccount = toAccount;
        this.fees = fees;
        this.status = "ACTIVE";
        this.deletedAt = 0;
        this.transactionGroupId = 0;
    }
}
