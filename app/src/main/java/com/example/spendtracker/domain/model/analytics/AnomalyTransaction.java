package com.example.spendtracker.domain.model.analytics;

/**
 * A transaction flagged as potentially unusual, with reason and deviation info.
 */
public class AnomalyTransaction {
    private final int transactionId;
    private final double amount;
    private final String merchantName;
    private final String category;
    private final long date;
    private final String reason;
    private final double expectedAmount; // historical average for this merchant/category
    private final double deviationMultiple; // how many times the expected amount

    public AnomalyTransaction(int transactionId, double amount, String merchantName,
                              String category, long date, String reason,
                              double expectedAmount, double deviationMultiple) {
        this.transactionId = transactionId;
        this.amount = amount;
        this.merchantName = merchantName;
        this.category = category;
        this.date = date;
        this.reason = reason;
        this.expectedAmount = expectedAmount;
        this.deviationMultiple = deviationMultiple;
    }

    public int getTransactionId() { return transactionId; }
    public double getAmount() { return amount; }
    public String getMerchantName() { return merchantName; }
    public String getCategory() { return category; }
    public long getDate() { return date; }
    public String getReason() { return reason; }
    public double getExpectedAmount() { return expectedAmount; }
    public double getDeviationMultiple() { return deviationMultiple; }
}
