package com.example.spendtracker.data.local.entity;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "transactions")
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
    
    // Transfer specific fields
    public String fromAccount;
    public String toAccount;
    public double fees;

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
    }
}
