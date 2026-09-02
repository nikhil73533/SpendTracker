package com.example.spendtracker.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;
import com.example.spendtracker.data.local.entity.TransactionEntity;
import java.util.List;

/**
 * Room Data Access Object for transactions. Handles filtering, time series aggregations,
 * and category/bank summaries with strict classification separation (Income vs Expense vs Transfer).
 */
@Dao
public interface TransactionDao {
    @Query("SELECT * FROM transactions WHERE status = 'ACTIVE' ORDER BY date DESC, id DESC")
    LiveData<List<TransactionEntity>> getAllTransactions();

    @Query("SELECT * FROM transactions WHERE status = 'ACTIVE' ORDER BY date DESC, id DESC")
    List<TransactionEntity> getAllTransactionsSync();

    @Query("SELECT * FROM transactions WHERE status = 'ACTIVE' AND date BETWEEN :start AND :end ORDER BY date DESC, id DESC")
    LiveData<List<TransactionEntity>> getTransactionsInRange(long start, long end);

    @Query("SELECT * FROM transactions WHERE id = :id")
    LiveData<TransactionEntity> getTransactionById(int id);

    @Query("SELECT * FROM transactions WHERE id = :id")
    TransactionEntity getTransactionByIdSync(int id);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insertTransaction(TransactionEntity transaction);

    @Update
    void updateTransaction(TransactionEntity transaction);

    @Delete
    void deleteTransaction(TransactionEntity transaction);

    @Query("SELECT SUM(amount) FROM transactions WHERE type = 'INCOME' AND type != 'TRANSFER' AND LOWER(category) NOT LIKE '%transfer%' AND status = 'ACTIVE' AND date BETWEEN :start AND :end")
    LiveData<Double> getTotalIncome(long start, long end);

    @Query("SELECT SUM(amount) FROM transactions WHERE type = 'EXPENSE' AND type != 'TRANSFER' AND LOWER(category) NOT LIKE '%transfer%' AND status = 'ACTIVE' AND date BETWEEN :start AND :end")
    LiveData<Double> getTotalExpense(long start, long end);

    @Query("SELECT SUM(amount) FROM transactions WHERE sourceType = 'Account' AND type != 'TRANSFER' AND LOWER(category) NOT LIKE '%transfer%' AND status = 'ACTIVE' AND date BETWEEN :start AND :end")
    LiveData<Double> getTotalAccountTransaction(long start, long end);

    @Query("SELECT category, SUM(amount) as total FROM transactions WHERE status = 'ACTIVE' AND type != 'TRANSFER' AND LOWER(category) NOT LIKE '%transfer%' AND date BETWEEN :start AND :end GROUP BY category")
    LiveData<List<CategorySum>> getCategorySummaries(long start, long end);

    @Query("SELECT category, SUM(amount) as total FROM transactions WHERE type = 'EXPENSE' AND type != 'TRANSFER' AND LOWER(category) NOT LIKE '%transfer%' AND status = 'ACTIVE' AND date BETWEEN :start AND :end GROUP BY category")
    LiveData<List<CategorySum>> getExpenseCategorySummaries(long start, long end);

    @Query("SELECT category, AVG(amount) as total FROM transactions WHERE type = 'EXPENSE' AND type != 'TRANSFER' AND LOWER(category) NOT LIKE '%transfer%' AND status = 'ACTIVE' AND date BETWEEN :start AND :end GROUP BY category")
    LiveData<List<CategorySum>> getExpenseCategoryAverages(long start, long end);

    @Query("SELECT category, SUM(amount) as total FROM transactions WHERE type = 'INCOME' AND type != 'TRANSFER' AND LOWER(category) NOT LIKE '%transfer%' AND status = 'ACTIVE' AND date BETWEEN :start AND :end GROUP BY category")
    LiveData<List<CategorySum>> getIncomeCategorySummaries(long start, long end);

    @Query("SELECT category, AVG(amount) as total FROM transactions WHERE type = 'INCOME' AND type != 'TRANSFER' AND LOWER(category) NOT LIKE '%transfer%' AND status = 'ACTIVE' AND date BETWEEN :start AND :end GROUP BY category")
    LiveData<List<CategorySum>> getIncomeCategoryAverages(long start, long end);

    @Query("SELECT date as timestamp, SUM(amount) as total FROM transactions WHERE type = :type AND type != 'TRANSFER' AND LOWER(category) NOT LIKE '%transfer%' AND status = 'ACTIVE' AND date BETWEEN :start AND :end GROUP BY (date / 86400000) ORDER BY date ASC")
    LiveData<List<TimeSum>> getDailyTotals(long start, long end, String type);

    @Query("SELECT MIN(date) as timestamp, SUM(amount) as total FROM transactions WHERE type = :type AND type != 'TRANSFER' AND LOWER(category) NOT LIKE '%transfer%' AND status = 'ACTIVE' AND date BETWEEN :start AND :end GROUP BY strftime('%Y-%W', date / 1000, 'unixepoch', 'localtime') ORDER BY date ASC")
    LiveData<List<TimeSum>> getWeeklyTotals(long start, long end, String type);

    @Query("SELECT MIN(date) as timestamp, SUM(amount) as total FROM transactions WHERE type = :type AND type != 'TRANSFER' AND LOWER(category) NOT LIKE '%transfer%' AND status = 'ACTIVE' AND date BETWEEN :start AND :end GROUP BY strftime('%Y-%m', date / 1000, 'unixepoch', 'localtime') ORDER BY date ASC")
    LiveData<List<TimeSum>> getMonthlyTotals(long start, long end, String type);

    @Query("SELECT MIN(date) as timestamp, SUM(amount) as total FROM transactions WHERE type = :type AND type != 'TRANSFER' AND LOWER(category) NOT LIKE '%transfer%' AND status = 'ACTIVE' AND date BETWEEN :start AND :end GROUP BY strftime('%Y', date / 1000, 'unixepoch', 'localtime') ORDER BY date ASC")
    LiveData<List<TimeSum>> getAnnuallyTotals(long start, long end, String type);

    @Query("SELECT COALESCE(SUM(amount), 0.0) FROM transactions WHERE LOWER(category) = LOWER(:category) AND status = 'ACTIVE' AND type = 'EXPENSE' AND date BETWEEN :start AND :end")
    double getCategoryTotalInRangeSync(String category, long start, long end);

    @Query("SELECT (CASE WHEN type = 'INCOME' THEN sender ELSE receiverName END) as name, upiId, MAX(date) as lastTransactionDate, SUM(CASE WHEN type = 'EXPENSE' THEN amount ELSE 0 END) as totalExpense, SUM(CASE WHEN type = 'INCOME' THEN amount ELSE 0 END) as totalIncome, SUM(CASE WHEN isRead = 0 THEN 1 ELSE 0 END) as unreadCount FROM transactions WHERE status = 'ACTIVE' AND type != 'TRANSFER' AND LOWER(category) NOT LIKE '%transfer%' GROUP BY (CASE WHEN type = 'INCOME' THEN sender ELSE receiverName END) ORDER BY lastTransactionDate DESC")
    LiveData<List<AccountSummary>> getUniqueAccounts();

    @Query("SELECT (CASE WHEN strftime('%w', date/1000, 'unixepoch', 'localtime') IN ('0', '6') THEN 'Weekend' ELSE 'Weekday' END) as category, SUM(amount) as total FROM transactions WHERE type = :type AND type != 'TRANSFER' AND LOWER(category) NOT LIKE '%transfer%' AND status = 'ACTIVE' AND date BETWEEN :start AND :end GROUP BY (CASE WHEN strftime('%w', date/1000, 'unixepoch', 'localtime') IN ('0', '6') THEN 'Weekend' ELSE 'Weekday' END)")
    LiveData<List<CategorySum>> getWeekdayWeekendTotals(long start, long end, String type);

    @Query("SELECT bankName as category, SUM(amount) as total FROM transactions WHERE type = :type AND type != 'TRANSFER' AND LOWER(category) NOT LIKE '%transfer%' AND status = 'ACTIVE' AND date BETWEEN :start AND :end GROUP BY bankName")
    LiveData<List<CategorySum>> getBankTotals(long start, long end, String type);

    @Query("SELECT sourceType as category, SUM(amount) as total FROM transactions WHERE type = :type AND type != 'TRANSFER' AND LOWER(category) NOT LIKE '%transfer%' AND status = 'ACTIVE' AND date BETWEEN :start AND :end GROUP BY sourceType")
    LiveData<List<CategorySum>> getSourceTypeTotals(long start, long end, String type);

    @Query("SELECT SUM(amount) FROM transactions WHERE type = 'EXPENSE' AND type != 'TRANSFER' AND LOWER(category) NOT LIKE '%transfer%' AND sourceType = 'Credit Card' AND status = 'ACTIVE' AND date BETWEEN :start AND :end")
    LiveData<Double> getCreditCardExpense(long start, long end);

    @Query("SELECT SUM(amount) FROM transactions WHERE type = 'EXPENSE' AND type != 'TRANSFER' AND LOWER(category) NOT LIKE '%transfer%' AND sourceType = 'Account' AND status = 'ACTIVE' AND date BETWEEN :start AND :end")
    LiveData<Double> getAccountExpense(long start, long end);

    @Query("SELECT (COALESCE(SUM(CASE WHEN (type = 'INCOME' AND LOWER(category) LIKE '%transfer%') OR (type = 'TRANSFER' AND sender IS NOT NULL AND sender != '' AND (toAccount IS NULL OR toAccount = '')) THEN amount ELSE 0 END), 0.0) - COALESCE(SUM(CASE WHEN (type = 'EXPENSE' AND LOWER(category) LIKE '%transfer%') OR (type = 'TRANSFER' AND ((toAccount IS NOT NULL AND toAccount != '') OR (sender IS NULL OR sender = ''))) THEN amount ELSE 0 END), 0.0)) FROM transactions WHERE status = 'ACTIVE' AND date BETWEEN :start AND :end")
    LiveData<Double> getTransferTotal(long start, long end);

    @Query("SELECT SUM(amount) FROM transactions WHERE status = 'ACTIVE' AND date BETWEEN :start AND :end AND ((type = 'EXPENSE' AND LOWER(category) LIKE '%transfer%') OR (type = 'TRANSFER' AND ((toAccount IS NOT NULL AND toAccount != '') OR (sender IS NULL OR sender = ''))))")
    LiveData<Double> getTransferOutgoing(long start, long end);

    @Query("SELECT SUM(amount) FROM transactions WHERE status = 'ACTIVE' AND date BETWEEN :start AND :end AND ((type = 'INCOME' AND LOWER(category) LIKE '%transfer%') OR (type = 'TRANSFER' AND sender IS NOT NULL AND sender != '' AND (toAccount IS NULL OR toAccount = '')))")
    LiveData<Double> getTransferIncoming(long start, long end);

    @Query("UPDATE transactions SET isRead = 1 WHERE (receiverName = :accountName OR sender = :accountName)")
    void markAsRead(String accountName);

    @Query("SELECT * FROM transactions WHERE (receiverName = :accountName OR sender = :accountName) AND status = 'ACTIVE' AND type != 'TRANSFER' AND LOWER(category) NOT LIKE '%transfer%' AND date BETWEEN :start AND :end ORDER BY date DESC, id DESC")
    LiveData<List<TransactionEntity>> getAccountHistory(String accountName, long start, long end);

    @Query("UPDATE transactions SET category = :newName WHERE category = :oldName")
    void renameCategory(String oldName, String newName);

    @Query("SELECT DISTINCT name FROM (SELECT sender AS name FROM transactions WHERE sender IS NOT NULL AND sender != '' AND status = 'ACTIVE' UNION SELECT receiverName AS name FROM transactions WHERE receiverName IS NOT NULL AND receiverName != '' AND status = 'ACTIVE') ORDER BY name ASC")
    LiveData<List<String>> getUniqueContacts();

    // ── Soft delete / Trash ─────────────────────────────────────────────────
    @Query("UPDATE transactions SET status = 'DELETED', deletedAt = :deletedAt WHERE id = :id")
    void softDeleteTransaction(int id, long deletedAt);

    @Query("UPDATE transactions SET status = 'ACTIVE', deletedAt = 0 WHERE id = :id")
    void restoreTransaction(int id);

    @Query("SELECT * FROM transactions WHERE status = 'DELETED' ORDER BY deletedAt DESC, id DESC")
    LiveData<List<TransactionEntity>> getDeletedTransactions();

    @Query("DELETE FROM transactions WHERE id = :id")
    void permanentlyDeleteTransaction(int id);

    // ── Transaction Group association helpers ────────────────────────────────
    @Query("UPDATE transactions SET transactionGroupId = :groupId WHERE id = :transactionId")
    void setTransactionGroupId(int transactionId, int groupId);

    @Query("SELECT transactionGroupId FROM transactions WHERE id = :transactionId")
    int getTransactionGroupId(int transactionId);

    class CategorySum {
        public String category;
        public double total;
    }

    class TimeSum {
        public long timestamp;
        public double total;
    }

    class AccountSummary {
        public String name;
        public String upiId;
        public long lastTransactionDate;
        public double totalExpense;
        public double totalIncome;
        public int unreadCount;
    }
}
