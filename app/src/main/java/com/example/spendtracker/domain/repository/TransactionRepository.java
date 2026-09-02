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
    LiveData<List<com.example.spendtracker.data.local.entity.CategoryEntity>> getCategoryEntities();
    void addCategory(String name, String type);
    void saveCategory(com.example.spendtracker.data.local.entity.CategoryEntity category);
    void deleteCategory(String name);
    void renameCategory(String oldName, String newName);
    LiveData<List<com.example.spendtracker.domain.model.DailyTrend>> getDailyTotals(long start, long end, String type);
    LiveData<List<com.example.spendtracker.domain.model.DailyTrend>> getWeeklyTotals(long start, long end, String type);
    LiveData<List<com.example.spendtracker.domain.model.DailyTrend>> getMonthlyTotals(long start, long end, String type);
    LiveData<List<com.example.spendtracker.domain.model.DailyTrend>> getAnnuallyTotals(long start, long end, String type);
    List<Transaction> getTransactionsSync();

    LiveData<List<com.example.spendtracker.data.local.dao.TransactionDao.AccountSummary>> getUniqueAccounts();
    LiveData<List<Transaction>> getAccountHistory(String accountId, long start, long end);

    /** Type-filtered weekday/weekend totals (pass "EXPENSE" or "INCOME"). */
    LiveData<List<com.example.spendtracker.data.local.dao.TransactionDao.CategorySum>> getWeekdayWeekendTotals(long start, long end, String type);
    /** Type-filtered bank totals. */
    LiveData<List<com.example.spendtracker.data.local.dao.TransactionDao.CategorySum>> getBankTotals(long start, long end, String type);
    /** Type-filtered source-type totals. */
    LiveData<List<com.example.spendtracker.data.local.dao.TransactionDao.CategorySum>> getSourceTypeTotals(long start, long end, String type);

    LiveData<Double> getTotalCardExpense(long start, long end);
    LiveData<Double> getTotalAccountExpense(long start, long end);
    LiveData<Double> getTotalTransfer(long start, long end);
    LiveData<Double> getTotalTransferIncoming(long start, long end);
    LiveData<Double> getTotalTransferOutgoing(long start, long end);

    void markAsRead(String accountName);
    LiveData<List<String>> getUniqueContacts();

    // ── Trash / Soft Delete ──────────────────────────────────────────────────
    void softDeleteTransaction(int transactionId);
    void restoreTransaction(int transactionId);
    LiveData<List<Transaction>> getDeletedTransactions();
    void permanentlyDeleteTransaction(int transactionId);

    // ── Cloned Database Sync ────────────────────────────────────────────────
    void restoreFromClone();
}
