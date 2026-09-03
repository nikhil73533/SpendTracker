package com.example.spendtracker.domain.model.analytics;

/**
 * Financial overview for a selected period.
 * Transfers are always separated from income/expense calculations.
 */
public class AnalyticsSummary {
    private final double totalIncome;
    private final double totalExpense;
    private final double totalTransfer;
    private final double netBalance;

    private final int incomeCount;
    private final int expenseCount;
    private final int transferCount;
    private final int totalCount;

    private final double averageExpense;
    private final double averageIncome;
    private final double averageOverall;

    public AnalyticsSummary(double totalIncome, double totalExpense, double totalTransfer,
                            int incomeCount, int expenseCount, int transferCount,
                            double averageExpense, double averageIncome) {
        this.totalIncome = totalIncome;
        this.totalExpense = totalExpense;
        this.totalTransfer = totalTransfer;
        this.netBalance = totalIncome - totalExpense; // Transfers excluded
        this.incomeCount = incomeCount;
        this.expenseCount = expenseCount;
        this.transferCount = transferCount;
        this.totalCount = incomeCount + expenseCount + transferCount;
        this.averageExpense = averageExpense;
        this.averageIncome = averageIncome;
        // Average overall excludes transfers to avoid distortion
        int nonTransferCount = incomeCount + expenseCount;
        this.averageOverall = nonTransferCount > 0 ? (totalIncome + totalExpense) / nonTransferCount : 0;
    }

    public double getTotalIncome() { return totalIncome; }
    public double getTotalExpense() { return totalExpense; }
    public double getTotalTransfer() { return totalTransfer; }
    public double getNetBalance() { return netBalance; }
    public int getIncomeCount() { return incomeCount; }
    public int getExpenseCount() { return expenseCount; }
    public int getTransferCount() { return transferCount; }
    public int getTotalCount() { return totalCount; }
    public double getAverageExpense() { return averageExpense; }
    public double getAverageIncome() { return averageIncome; }
    public double getAverageOverall() { return averageOverall; }

    /** Returns true if there is enough data to display meaningful analytics. */
    public boolean hasData() { return totalCount > 0; }
    /** Returns true if income data is available for savings/ratio calculations. */
    public boolean hasIncomeData() { return incomeCount > 0 && totalIncome > 0; }
}
