package com.example.spendtracker.domain.model.analytics;

/**
 * Month-over-month comparison data for spending, income, counts, and averages.
 */
public class MonthlyComparison {
    private final String currentLabel;
    private final String previousLabel;

    private final double currentExpense;
    private final double previousExpense;
    private final double expenseChangePercent;

    private final double currentIncome;
    private final double previousIncome;
    private final double incomeChangePercent;

    private final int currentCount;
    private final int previousCount;
    private final double countChangePercent;

    private final double currentAverage;
    private final double previousAverage;
    private final double averageChangePercent;

    private final double currentNet;
    private final double previousNet;

    public MonthlyComparison(String currentLabel, String previousLabel,
                             double currentExpense, double previousExpense,
                             double currentIncome, double previousIncome,
                             int currentCount, int previousCount) {
        this.currentLabel = currentLabel;
        this.previousLabel = previousLabel;
        this.currentExpense = currentExpense;
        this.previousExpense = previousExpense;
        this.expenseChangePercent = safePercent(currentExpense, previousExpense);
        this.currentIncome = currentIncome;
        this.previousIncome = previousIncome;
        this.incomeChangePercent = safePercent(currentIncome, previousIncome);
        this.currentCount = currentCount;
        this.previousCount = previousCount;
        this.countChangePercent = safePercent(currentCount, previousCount);
        this.currentAverage = currentCount > 0 ? currentExpense / currentCount : 0;
        this.previousAverage = previousCount > 0 ? previousExpense / previousCount : 0;
        this.averageChangePercent = safePercent(currentAverage, previousAverage);
        this.currentNet = currentIncome - currentExpense;
        this.previousNet = previousIncome - previousExpense;
    }

    private static double safePercent(double current, double previous) {
        if (previous == 0) return current > 0 ? 100 : 0;
        return ((current - previous) / previous) * 100;
    }

    public String getCurrentLabel() { return currentLabel; }
    public String getPreviousLabel() { return previousLabel; }
    public double getCurrentExpense() { return currentExpense; }
    public double getPreviousExpense() { return previousExpense; }
    public double getExpenseChangePercent() { return expenseChangePercent; }
    public double getCurrentIncome() { return currentIncome; }
    public double getPreviousIncome() { return previousIncome; }
    public double getIncomeChangePercent() { return incomeChangePercent; }
    public int getCurrentCount() { return currentCount; }
    public int getPreviousCount() { return previousCount; }
    public double getCountChangePercent() { return countChangePercent; }
    public double getCurrentAverage() { return currentAverage; }
    public double getPreviousAverage() { return previousAverage; }
    public double getAverageChangePercent() { return averageChangePercent; }
    public double getCurrentNet() { return currentNet; }
    public double getPreviousNet() { return previousNet; }

    public boolean hasPreviousData() { return previousCount > 0 || previousExpense > 0; }
}
