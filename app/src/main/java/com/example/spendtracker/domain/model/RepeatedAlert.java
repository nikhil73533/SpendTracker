package com.example.spendtracker.domain.model;

public class RepeatedAlert {
    private int id;
    private String merchantName;
    private double amount;
    private long firstTransactionDate;
    private long secondTransactionDate;
    private int firstTransactionId;
    private int secondTransactionId;
    private boolean enabled;
    private boolean dismissed;
    private long createdAt;
    private String category;

    public RepeatedAlert(int id, String merchantName, double amount, long firstTransactionDate,
                         long secondTransactionDate, int firstTransactionId, int secondTransactionId,
                         boolean enabled, boolean dismissed, long createdAt, String category) {
        this.id = id;
        this.merchantName = merchantName;
        this.amount = amount;
        this.firstTransactionDate = firstTransactionDate;
        this.secondTransactionDate = secondTransactionDate;
        this.firstTransactionId = firstTransactionId;
        this.secondTransactionId = secondTransactionId;
        this.enabled = enabled;
        this.dismissed = dismissed;
        this.createdAt = createdAt;
        this.category = category;
    }

    public int getId() { return id; }
    public String getMerchantName() { return merchantName; }
    public double getAmount() { return amount; }
    public long getFirstTransactionDate() { return firstTransactionDate; }
    public long getSecondTransactionDate() { return secondTransactionDate; }
    public int getFirstTransactionId() { return firstTransactionId; }
    public int getSecondTransactionId() { return secondTransactionId; }
    public boolean isEnabled() { return enabled; }
    public boolean isDismissed() { return dismissed; }
    public long getCreatedAt() { return createdAt; }
    public String getCategory() { return category; }

    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public void setDismissed(boolean dismissed) { this.dismissed = dismissed; }
}
