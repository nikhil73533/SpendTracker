package com.example.spendtracker.domain.repository;

import androidx.lifecycle.LiveData;
import com.example.spendtracker.domain.model.Summary;
import com.example.spendtracker.domain.model.Transaction;
import java.util.List;

public interface TransactionRepository {
    LiveData<List<Transaction>> getTransactions();
    LiveData<List<Transaction>> getTransactionsInRange(long start, long end);
    void addTransaction(Transaction transaction);
    void updateTransaction(Transaction transaction);
    void deleteTransaction(Transaction transaction);
    LiveData<Transaction> getTransactionById(int id);
    LiveData<Summary> getSummary(long startDate, long endDate);
    LiveData<List<String>> getCategories();
    LiveData<List<String>> getCategoriesByType(String type);
    void addCategory(String name, String type);
    void deleteCategory(String name);
    LiveData<List<com.example.spendtracker.domain.model.DailyTrend>> getDailyTotals(long start, long end, String type);
    LiveData<List<com.example.spendtracker.domain.model.DailyTrend>> getWeeklyTotals(long start, long end, String type);
    LiveData<List<com.example.spendtracker.domain.model.DailyTrend>> getMonthlyTotals(long start, long end, String type);
    LiveData<List<com.example.spendtracker.domain.model.DailyTrend>> getAnnuallyTotals(long start, long end, String type);
    List<Transaction> getTransactionsSync();
    
    LiveData<List<com.example.spendtracker.data.local.dao.TransactionDao.AccountSummary>> getUniqueAccounts();
    LiveData<List<Transaction>> getAccountHistory(String accountId, long start, long end);
}
