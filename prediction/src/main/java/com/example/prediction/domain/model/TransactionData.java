package com.example.prediction.domain.model;

public class TransactionData {
    public final String description;
    public final String receiverName;
    public final double amount;
    public final String type;
    public final long timestamp;
    public final String upiId;

    public TransactionData(String description, String receiverName, double amount, String type, long timestamp, String upiId) {
        this.description = description;
        this.receiverName = receiverName;
        this.amount = amount;
        this.type = type;
        this.timestamp = timestamp;
        this.upiId = upiId;
    }
}
