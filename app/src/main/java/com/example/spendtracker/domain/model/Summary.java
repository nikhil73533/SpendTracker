package com.example.spendtracker.domain.model;

import java.util.Map;

public class Summary {
    private double totalIncome;
    private double totalExpense;
    private double totalTransfer;
    private double totalAccountTransaction;
    private Map<String, Double> expenseBreakdown;
    private Map<String, Double> expenseAverages;
    private Map<String, Double> incomeBreakdown;
    private Map<String, Double> incomeAverages;

    public Summary(double totalIncome, double totalExpense, double totalAccountTransaction, 
                   Map<String, Double> expenseBreakdown, Map<String, Double> expenseAverages,
                   Map<String, Double> incomeBreakdown, Map<String, Double> incomeAverages) {
        this(totalIncome, totalExpense, 0.0, totalAccountTransaction, expenseBreakdown, expenseAverages, incomeBreakdown, incomeAverages);
    }

    public Summary(double totalIncome, double totalExpense, double totalTransfer, double totalAccountTransaction, 
                   Map<String, Double> expenseBreakdown, Map<String, Double> expenseAverages,
                   Map<String, Double> incomeBreakdown, Map<String, Double> incomeAverages) {
        this.totalIncome = totalIncome;
        this.totalExpense = totalExpense;
        this.totalTransfer = totalTransfer;
        this.totalAccountTransaction = totalAccountTransaction;
        this.expenseBreakdown = expenseBreakdown;
        this.expenseAverages = expenseAverages;
        this.incomeBreakdown = incomeBreakdown;
        this.incomeAverages = incomeAverages;
    }

    public double getTotalIncome() { return totalIncome; }
    public double getTotalExpense() { return totalExpense; }
    public double getTotalTransfer() { return totalTransfer; }
    public double getTotalAccountTransaction() { return totalAccountTransaction; }
    public double getNetBalance() { return totalIncome - totalExpense; }
    public Map<String, Double> getExpenseBreakdown() { return expenseBreakdown; }
    public Map<String, Double> getExpenseAverages() { return expenseAverages; }
    public Map<String, Double> getIncomeBreakdown() { return incomeBreakdown; }
    public Map<String, Double> getIncomeAverages() { return incomeAverages; }
}
