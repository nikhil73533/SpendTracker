package com.example.spendtracker.domain.model.analytics;

import com.example.spendtracker.data.local.entity.TransactionEntity;
import java.util.List;

public class LargeTransactionSummary {
    private final double threshold;
    private final int count;
    private final double totalAmount;
    private final List<TransactionEntity> transactions;

    public LargeTransactionSummary(double threshold, int count, double totalAmount, List<TransactionEntity> transactions) {
        this.threshold = threshold;
        this.count = count;
        this.totalAmount = totalAmount;
        this.transactions = transactions;
    }

    public double getThreshold() { return threshold; }
    public int getCount() { return count; }
    public double getTotalAmount() { return totalAmount; }
    public List<TransactionEntity> getTransactions() { return transactions; }
}
