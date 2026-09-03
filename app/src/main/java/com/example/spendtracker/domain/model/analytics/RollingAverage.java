package com.example.spendtracker.domain.model.analytics;

/**
 * Rolling average data for N-month windows (3, 6, 12 months).
 */
public class RollingAverage {
    private final int months;
    private final double averageMonthlySpending;
    private final double averageMonthlyCount;
    private final double averageTransactionAmount;
    private final double currentMonthSpending;
    private final double differencePercent;
    private final boolean hasSufficientData;

    public RollingAverage(int months, double averageMonthlySpending, double averageMonthlyCount,
                          double averageTransactionAmount, double currentMonthSpending,
                          boolean hasSufficientData) {
        this.months = months;
        this.averageMonthlySpending = averageMonthlySpending;
        this.averageMonthlyCount = averageMonthlyCount;
        this.averageTransactionAmount = averageTransactionAmount;
        this.currentMonthSpending = currentMonthSpending;
        this.hasSufficientData = hasSufficientData;
        if (hasSufficientData && averageMonthlySpending > 0) {
            this.differencePercent = ((currentMonthSpending - averageMonthlySpending) / averageMonthlySpending) * 100;
        } else {
            this.differencePercent = 0;
        }
    }

    public int getMonths() { return months; }
    public double getAverageMonthlySpending() { return averageMonthlySpending; }
    public double getAverageMonthlyCount() { return averageMonthlyCount; }
    public double getAverageTransactionAmount() { return averageTransactionAmount; }
    public double getCurrentMonthSpending() { return currentMonthSpending; }
    public double getDifferencePercent() { return differencePercent; }
    public boolean hasSufficientData() { return hasSufficientData; }
}
