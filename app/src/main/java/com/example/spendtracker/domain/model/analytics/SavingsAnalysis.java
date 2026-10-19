package com.example.spendtracker.domain.model.analytics;

/** Recorded cash flow, not a claim about a user's bank balance or investment returns. */
public final class SavingsAnalysis {
    public final AnalyticsSummary current;
    public final AnalyticsSummary previous;
    public final ComparisonWindow comparison;

    public SavingsAnalysis(AnalyticsSummary current, AnalyticsSummary previous, ComparisonWindow comparison) {
        this.current = current;
        this.previous = previous;
        this.comparison = comparison;
    }

    public boolean hasSavingsRate() { return current.hasIncomeData(); }
    public boolean hasCurrentCashFlow() { return current.getIncomeCount() + current.getExpenseCount() > 0; }
    public boolean hasPreviousCashFlow() { return previous.getIncomeCount() + previous.getExpenseCount() > 0; }
    public double savingsRate() { return hasSavingsRate() ? current.getNetBalance() / current.getTotalIncome() * 100 : Double.NaN; }
    public double expenseRatio() { return hasSavingsRate() ? current.getTotalExpense() / current.getTotalIncome() * 100 : Double.NaN; }
    public double surplusChange() { return current.getNetBalance() - previous.getNetBalance(); }
    public boolean hasRateComparison() { return current.hasIncomeData() && previous.hasIncomeData(); }
    public double rateChangePoints() { return hasRateComparison()
            ? savingsRate() - previous.getNetBalance() / previous.getTotalIncome() * 100 : Double.NaN; }
}
