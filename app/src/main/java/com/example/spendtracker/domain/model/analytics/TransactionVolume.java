package com.example.spendtracker.domain.model.analytics;

/**
 * Transaction volume metrics for a specific period.
 * Separates income, expense, and transfer counts/amounts.
 */
public class TransactionVolume {
    private final String periodLabel;
    private final int totalCount;
    private final int expenseCount;
    private final int incomeCount;
    private final int transferCount;
    private final double totalExpense;
    private final double totalIncome;
    private final double totalTransfer;
    private final double averageExpense;
    private final double averageIncome;

    public TransactionVolume(String periodLabel, int totalCount,
                             int expenseCount, int incomeCount, int transferCount,
                             double totalExpense, double totalIncome, double totalTransfer) {
        this.periodLabel = periodLabel;
        this.totalCount = totalCount;
        this.expenseCount = expenseCount;
        this.incomeCount = incomeCount;
        this.transferCount = transferCount;
        this.totalExpense = totalExpense;
        this.totalIncome = totalIncome;
        this.totalTransfer = totalTransfer;
        this.averageExpense = expenseCount > 0 ? totalExpense / expenseCount : 0;
        this.averageIncome = incomeCount > 0 ? totalIncome / incomeCount : 0;
    }

    public String getPeriodLabel() { return periodLabel; }
    public int getTotalCount() { return totalCount; }
    public int getExpenseCount() { return expenseCount; }
    public int getIncomeCount() { return incomeCount; }
    public int getTransferCount() { return transferCount; }
    public double getTotalExpense() { return totalExpense; }
    public double getTotalIncome() { return totalIncome; }
    public double getTotalTransfer() { return totalTransfer; }
    public double getAverageExpense() { return averageExpense; }
    public double getAverageIncome() { return averageIncome; }
}
