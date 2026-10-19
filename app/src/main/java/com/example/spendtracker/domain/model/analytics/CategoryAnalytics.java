package com.example.spendtracker.domain.model.analytics;

/**
 * Analytics for a single category: total amount, count, average, and growth percentage.
 */
public class CategoryAnalytics {
    private final String categoryName;
    private final double totalAmount;
    private final int transactionCount;
    private final double averageTransaction;
    private final double growthPercent;
    private final boolean hasGrowthData;

    public CategoryAnalytics(String categoryName, double totalAmount, int transactionCount) {
        this(categoryName, totalAmount, transactionCount, 0, false);
    }

    public CategoryAnalytics(String categoryName, double totalAmount, int transactionCount,
                             double growthPercent, boolean hasGrowthData) {
        this.categoryName = categoryName;
        this.totalAmount = totalAmount;
        this.transactionCount = transactionCount;
        this.averageTransaction = transactionCount > 0 ? totalAmount / transactionCount : 0;
        this.growthPercent = growthPercent;
        this.hasGrowthData = hasGrowthData;
    }

    public String getCategoryName() { return categoryName; }
    public double getTotalAmount() { return totalAmount; }
    public int getTransactionCount() { return transactionCount; }
    public double getAverageTransaction() { return averageTransaction; }
    public double getGrowthPercent() { return growthPercent; }
    public boolean hasGrowthData() { return hasGrowthData; }
}
