package com.example.spendtracker.util;

/** Immutable UPI payment usage for the current local day and calendar month. */
public final class UpiUsage {
    private final double dailyAmount;
    private final double monthlyAmount;
    private final int dailyPaymentCount;
    private final int monthlyPaymentCount;
    private final double dailyLimit;
    private final double monthlyLimit;

    public UpiUsage(double dailyAmount, double monthlyAmount, int dailyPaymentCount, int monthlyPaymentCount,
                    double dailyLimit, double monthlyLimit) {
        this.dailyAmount = dailyAmount;
        this.monthlyAmount = monthlyAmount;
        this.dailyPaymentCount = dailyPaymentCount;
        this.monthlyPaymentCount = monthlyPaymentCount;
        this.dailyLimit = dailyLimit;
        this.monthlyLimit = monthlyLimit;
    }

    public double getDailyAmount() { return dailyAmount; }
    public double getMonthlyAmount() { return monthlyAmount; }
    public int getDailyPaymentCount() { return dailyPaymentCount; }
    public int getMonthlyPaymentCount() { return monthlyPaymentCount; }
    public double getDailyLimit() { return dailyLimit; }
    public double getMonthlyLimit() { return monthlyLimit; }
    public boolean hasDailyLimit() { return dailyLimit > 0; }
    public boolean hasMonthlyLimit() { return monthlyLimit > 0; }
    public int getDailyPercent() { return percent(dailyAmount, dailyLimit); }
    public int getMonthlyPercent() { return percent(monthlyAmount, monthlyLimit); }
    public boolean isDailyNearLimit() { return hasDailyLimit() && getDailyPercent() >= 80; }
    public boolean isMonthlyNearLimit() { return hasMonthlyLimit() && getMonthlyPercent() >= 80; }

    private static int percent(double value, double limit) {
        if (limit <= 0 || !Double.isFinite(value)) return 0;
        return (int) Math.round(Math.max(0, value * 100.0 / limit));
    }
}
