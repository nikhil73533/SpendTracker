package com.example.spendtracker.domain.model;

public class DailyTrend {
    private long timestamp;
    private double amount;

    public DailyTrend(long timestamp, double amount) {
        this.timestamp = timestamp;
        this.amount = amount;
    }

    public long getTimestamp() { return timestamp; }
    public double getAmount() { return amount; }
}
