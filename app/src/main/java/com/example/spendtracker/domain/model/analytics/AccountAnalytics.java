package com.example.spendtracker.domain.model.analytics;

/**
 * Analytics for a single bank/account/card: totals by transaction type.
 */
public class AccountAnalytics {
    private final String accountName;
    private final double totalExpense;
    private final double totalIncome;
    private final double totalTransfer;
    private final int transactionCount;
    private final double averageTransaction;

    public AccountAnalytics(String accountName, double totalExpense, double totalIncome,
                            double totalTransfer, int transactionCount) {
        this.accountName = accountName;
        this.totalExpense = totalExpense;
        this.totalIncome = totalIncome;
        this.totalTransfer = totalTransfer;
        this.transactionCount = transactionCount;
        this.averageTransaction = transactionCount > 0
                ? (totalExpense + totalIncome + totalTransfer) / transactionCount : 0;
    }

    public String getAccountName() { return accountName; }
    public double getTotalExpense() { return totalExpense; }
    public double getTotalIncome() { return totalIncome; }
    public double getTotalTransfer() { return totalTransfer; }
    public int getTransactionCount() { return transactionCount; }
    public double getAverageTransaction() { return averageTransaction; }
}
