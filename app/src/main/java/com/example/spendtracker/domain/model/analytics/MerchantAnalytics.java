package com.example.spendtracker.domain.model.analytics;

/**
 * Analytics for a single merchant: total amount, count, average, and recurrence classification.
 */
public class MerchantAnalytics {
    public enum RecurrenceType {
        RECURRING,   // Regular pattern detected (monthly/weekly/biweekly)
        FREQUENT,    // Many transactions but no clear pattern
        OCCASIONAL,  // A few transactions
        ONE_TIME     // Single transaction
    }

    private final String merchantName;
    private final double totalAmount;
    private final int transactionCount;
    private final double averageTransaction;
    private final RecurrenceType recurrenceType;
    private final String recurrencePattern; // e.g., "Monthly", "Weekly", null

    public MerchantAnalytics(String merchantName, double totalAmount, int transactionCount,
                             RecurrenceType recurrenceType, String recurrencePattern) {
        this.merchantName = merchantName;
        this.totalAmount = totalAmount;
        this.transactionCount = transactionCount;
        this.averageTransaction = transactionCount > 0 ? totalAmount / transactionCount : 0;
        this.recurrenceType = recurrenceType;
        this.recurrencePattern = recurrencePattern;
    }

    public String getMerchantName() { return merchantName; }
    public double getTotalAmount() { return totalAmount; }
    public int getTransactionCount() { return transactionCount; }
    public double getAverageTransaction() { return averageTransaction; }
    public RecurrenceType getRecurrenceType() { return recurrenceType; }
    public String getRecurrencePattern() { return recurrencePattern; }
}
