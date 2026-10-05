package com.example.spendtracker.data.repository;

import com.example.spendtracker.data.local.dao.TransactionDao;
import com.example.spendtracker.domain.model.Summary;
import java.util.*;

final class DashboardSummaryMapper {
    private DashboardSummaryMapper() { }
    static Summary from(List<TransactionDao.SummaryRow> rows) {
        double income = 0, expense = 0, transfer = 0, account = 0;
        Map<String, Double> expenses = new LinkedHashMap<>(), expenseAverages = new LinkedHashMap<>();
        Map<String, Double> incomes = new LinkedHashMap<>(), incomeAverages = new LinkedHashMap<>();
        for (TransactionDao.SummaryRow row : rows == null ? Collections.<TransactionDao.SummaryRow>emptyList() : rows) {
            if ("__META".equals(row.type)) { transfer = row.total; account = row.accountTotal; }
            if ("EXPENSE".equals(row.type)) {
                expense += row.total; expenses.put(row.category, row.total);
                expenseAverages.put(row.category, row.transactionCount == 0 ? 0 : row.total / row.transactionCount);
            }
            if ("INCOME".equals(row.type)) {
                income += row.total; incomes.put(row.category, row.total);
                incomeAverages.put(row.category, row.transactionCount == 0 ? 0 : row.total / row.transactionCount);
            }
        }
        return new Summary(income, expense, transfer, account, expenses, expenseAverages, incomes, incomeAverages);
    }
}
