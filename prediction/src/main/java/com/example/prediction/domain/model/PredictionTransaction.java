package com.example.prediction.domain.model;

public class PredictionTransaction {
    public final String merchantName;
    public final String upiId;
    public final double amount;
    public final String type;
    public final long timestamp;

    public PredictionTransaction(String merchantName, String upiId, double amount, String type, long timestamp) {
        this.merchantName = merchantName;
        this.upiId = upiId;
        this.amount = amount;
        this.type = type;
        this.timestamp = timestamp;
    }
}
