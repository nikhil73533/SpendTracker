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

    // ── Advanced Analytics Queries ─────────────────────────────────────────

    // Transaction counts by type (sync for analytics service)
    @Query("SELECT COUNT(*) FROM transactions WHERE type = 'EXPENSE' AND type != 'TRANSFER' AND LOWER(category) NOT LIKE '%transfer%' AND status = 'ACTIVE' AND date BETWEEN :start AND :end")
    int getExpenseCountSync(long start, long end);

    @Query("SELECT COUNT(*) FROM transactions WHERE type = 'INCOME' AND type != 'TRANSFER' AND LOWER(category) NOT LIKE '%transfer%' AND status = 'ACTIVE' AND date BETWEEN :start AND :end")
    int getIncomeCountSync(long start, long end);

    @Query("SELECT COUNT(*) FROM transactions WHERE (type = 'TRANSFER' OR LOWER(category) LIKE '%transfer%') AND status = 'ACTIVE' AND date BETWEEN :start AND :end")
    int getTransferCountSync(long start, long end);

    @Query("SELECT COUNT(*) FROM transactions WHERE status = 'ACTIVE' AND date BETWEEN :start AND :end")
    int getTotalCountSync(long start, long end);

    // Averages (sync)
    @Query("SELECT AVG(amount) FROM transactions WHERE type = 'EXPENSE' AND type != 'TRANSFER' AND LOWER(category) NOT LIKE '%transfer%' AND status = 'ACTIVE' AND date BETWEEN :start AND :end")
    Double getAverageExpenseSync(long start, long end);

    @Query("SELECT AVG(amount) FROM transactions WHERE type = 'INCOME' AND type != 'TRANSFER' AND LOWER(category) NOT LIKE '%transfer%' AND status = 'ACTIVE' AND date BETWEEN :start AND :end")
    Double getAverageIncomeSync(long start, long end);

    // Totals (sync)
    @Query("SELECT COALESCE(SUM(amount), 0.0) FROM transactions WHERE type = 'EXPENSE' AND type != 'TRANSFER' AND LOWER(category) NOT LIKE '%transfer%' AND status = 'ACTIVE' AND date BETWEEN :start AND :end")
    double getTotalExpenseSync(long start, long end);

    @Query("SELECT COALESCE(SUM(amount), 0.0) FROM transactions WHERE type = 'INCOME' AND type != 'TRANSFER' AND LOWER(category) NOT LIKE '%transfer%' AND status = 'ACTIVE' AND date BETWEEN :start AND :end")
    double getTotalIncomeSync(long start, long end);

    @Query("SELECT COALESCE(SUM(amount), 0.0) FROM transactions WHERE (type = 'TRANSFER' OR LOWER(category) LIKE '%transfer%') AND status = 'ACTIVE' AND date BETWEEN :start AND :end")
    double getTotalTransferAmountSync(long start, long end);

    // Highest transactions
    @Query("SELECT * FROM transactions WHERE type = 'EXPENSE' AND type != 'TRANSFER' AND LOWER(category) NOT LIKE '%transfer%' AND status = 'ACTIVE' ORDER BY amount DESC LIMIT 1")
    TransactionEntity getHighestExpenseEverSync();

    @Query("SELECT * FROM transactions WHERE type = 'INCOME' AND type != 'TRANSFER' AND LOWER(category) NOT LIKE '%transfer%' AND status = 'ACTIVE' ORDER BY amount DESC LIMIT 1")
    TransactionEntity getHighestIncomeEverSync();

    @Query("SELECT * FROM transactions WHERE (type = 'TRANSFER' OR LOWER(category) LIKE '%transfer%') AND status = 'ACTIVE' ORDER BY amount DESC LIMIT 1")
    TransactionEntity getHighestTransferEverSync();

    @Query("SELECT * FROM transactions WHERE status = 'ACTIVE' AND date BETWEEN :start AND :end ORDER BY amount DESC LIMIT :limit")
    List<TransactionEntity> getTopTransactionsSync(long start, long end, int limit);

    // Category analytics (sync, expenses only)
    @Query("SELECT category, SUM(amount) as total FROM transactions WHERE type = 'EXPENSE' AND type != 'TRANSFER' AND LOWER(category) NOT LIKE '%transfer%' AND status = 'ACTIVE' AND date BETWEEN :start AND :end GROUP BY category ORDER BY total DESC")
    List<CategorySum> getExpenseCategorySummariesSync(long start, long end);

    @Query("SELECT category, COUNT(*) as total FROM transactions WHERE type = 'EXPENSE' AND type != 'TRANSFER' AND LOWER(category) NOT LIKE '%transfer%' AND status = 'ACTIVE' AND date BETWEEN :start AND :end GROUP BY category ORDER BY total DESC")
    List<CategorySum> getExpenseCategoryCountsSync(long start, long end);

    // Monthly totals/counts (sync, for trend analysis)
    @Query("SELECT strftime('%Y-%m', date/1000, 'unixepoch', 'localtime') as category, SUM(amount) as total FROM transactions WHERE type = :type AND type != 'TRANSFER' AND LOWER(category) NOT LIKE '%transfer%' AND status = 'ACTIVE' AND date BETWEEN :start AND :end GROUP BY strftime('%Y-%m', date/1000, 'unixepoch', 'localtime') ORDER BY category ASC")
    List<CategorySum> getMonthlyTotalsSync(long start, long end, String type);

    @Query("SELECT strftime('%Y-%m', date/1000, 'unixepoch', 'localtime') as category, COUNT(*) as total FROM transactions WHERE type = :type AND type != 'TRANSFER' AND LOWER(category) NOT LIKE '%transfer%' AND status = 'ACTIVE' AND date BETWEEN :start AND :end GROUP BY strftime('%Y-%m', date/1000, 'unixepoch', 'localtime') ORDER BY category ASC")
    List<CategorySum> getMonthlyCountsSync(long start, long end, String type);

    // Merchant analytics
    @Query("SELECT receiverName as category, SUM(amount) as total FROM transactions WHERE type = 'EXPENSE' AND type != 'TRANSFER' AND LOWER(category) NOT LIKE '%transfer%' AND status = 'ACTIVE' AND receiverName IS NOT NULL AND receiverName != '' AND date BETWEEN :start AND :end GROUP BY receiverName ORDER BY total DESC LIMIT :limit")
    List<CategorySum> getTopMerchantsByAmountSync(long start, long end, int limit);

    @Query("SELECT receiverName as category, COUNT(*) as total FROM transactions WHERE type = 'EXPENSE' AND type != 'TRANSFER' AND LOWER(category) NOT LIKE '%transfer%' AND status = 'ACTIVE' AND receiverName IS NOT NULL AND receiverName != '' AND date BETWEEN :start AND :end GROUP BY receiverName ORDER BY total DESC LIMIT :limit")
    List<CategorySum> getTopMerchantsByFrequencySync(long start, long end, int limit);

    // Bank/Account analytics (sync)
    @Query("SELECT bankName as category, SUM(amount) as total FROM transactions WHERE type = :type AND type != 'TRANSFER' AND LOWER(category) NOT LIKE '%transfer%' AND status = 'ACTIVE' AND bankName IS NOT NULL AND bankName != '' AND date BETWEEN :start AND :end GROUP BY bankName")
    List<CategorySum> getBankTotalsSync(long start, long end, String type);

    // Active non-transfer transactions in range (sync, for time-based analytics)
    @Query("SELECT * FROM transactions WHERE status = 'ACTIVE' AND type != 'TRANSFER' AND LOWER(category) NOT LIKE '%transfer%' AND date BETWEEN :start AND :end ORDER BY date ASC")
    List<TransactionEntity> getActiveNonTransferInRangeSync(long start, long end);

    // All active transactions in range (sync, includes transfers)
    @Query("SELECT * FROM transactions WHERE status = 'ACTIVE' AND date BETWEEN :start AND :end ORDER BY date ASC")
    List<TransactionEntity> getActiveTransactionsInRangeSync(long start, long end);

    // Day-of-week spending
    @Query("SELECT strftime('%w', date/1000, 'unixepoch', 'localtime') as category, SUM(amount) as total FROM transactions WHERE type = 'EXPENSE' AND type != 'TRANSFER' AND LOWER(category) NOT LIKE '%transfer%' AND status = 'ACTIVE' AND date BETWEEN :start AND :end GROUP BY strftime('%w', date/1000, 'unixepoch', 'localtime')")
    List<CategorySum> getDayOfWeekTotalsSync(long start, long end);

    @Query("SELECT strftime('%w', date/1000, 'unixepoch', 'localtime') as category, COUNT(*) as total FROM transactions WHERE type = 'EXPENSE' AND type != 'TRANSFER' AND LOWER(category) NOT LIKE '%transfer%' AND status = 'ACTIVE' AND date BETWEEN :start AND :end GROUP BY strftime('%w', date/1000, 'unixepoch', 'localtime')")
    List<CategorySum> getDayOfWeekCountsSync(long start, long end);

    // Daily expense totals (for highest spending day)
    @Query("SELECT strftime('%Y-%m-%d', date/1000, 'unixepoch', 'localtime') as category, SUM(amount) as total FROM transactions WHERE type = 'EXPENSE' AND type != 'TRANSFER' AND LOWER(category) NOT LIKE '%transfer%' AND status = 'ACTIVE' AND date BETWEEN :start AND :end GROUP BY strftime('%Y-%m-%d', date/1000, 'unixepoch', 'localtime') ORDER BY total DESC")
    List<CategorySum> getDailyExpenseTotalsSync(long start, long end);

    // First and last transaction dates
    @Query("SELECT MIN(date) FROM transactions WHERE status = 'ACTIVE'")
    Long getFirstTransactionDateSync();

    @Query("SELECT MAX(date) FROM transactions WHERE status = 'ACTIVE'")
    Long getLastTransactionDateSync();

    // Recurring merchant candidates
    @Query("SELECT receiverName as category, COUNT(*) as total FROM transactions WHERE type = 'EXPENSE' AND type != 'TRANSFER' AND LOWER(category) NOT LIKE '%transfer%' AND status = 'ACTIVE' AND receiverName IS NOT NULL AND receiverName != '' GROUP BY receiverName HAVING COUNT(*) >= :minCount ORDER BY total DESC")
    List<CategorySum> getRecurringMerchantCandidatesSync(int minCount);

    // Merchant transaction history
    @Query("SELECT * FROM transactions WHERE receiverName = :merchantName AND status = 'ACTIVE' AND type != 'TRANSFER' AND LOWER(category) NOT LIKE '%transfer%' ORDER BY date ASC")
    List<TransactionEntity> getMerchantTransactionsSync(String merchantName);

    // Category totals (sync, for category growth comparison)
    @Query("SELECT category, SUM(amount) as total FROM transactions WHERE type = 'EXPENSE' AND type != 'TRANSFER' AND LOWER(category) NOT LIKE '%transfer%' AND status = 'ACTIVE' AND date BETWEEN :start AND :end GROUP BY category")
    List<CategorySum> getCategoryTotalsSync(long start, long end);

    // Account × Category cross-dimensional
    @Query("SELECT bankName || ' → ' || category as category, SUM(amount) as total FROM transactions WHERE type = 'EXPENSE' AND type != 'TRANSFER' AND LOWER(category) NOT LIKE '%transfer%' AND status = 'ACTIVE' AND bankName IS NOT NULL AND bankName != '' AND date BETWEEN :start AND :end GROUP BY bankName, category ORDER BY bankName, total DESC")
    List<CategorySum> getAccountCategoryCrossSync(long start, long end);

    // Merchant average amount
    @Query("SELECT receiverName as category, AVG(amount) as total FROM transactions WHERE type = 'EXPENSE' AND type != 'TRANSFER' AND LOWER(category) NOT LIKE '%transfer%' AND status = 'ACTIVE' AND receiverName IS NOT NULL AND receiverName != '' GROUP BY receiverName")
    List<CategorySum> getMerchantAveragesSync();

    // Category average amount
    @Query("SELECT category, AVG(amount) as total FROM transactions WHERE type = 'EXPENSE' AND type != 'TRANSFER' AND LOWER(category) NOT LIKE '%transfer%' AND status = 'ACTIVE' GROUP BY category")
    List<CategorySum> getCategoryAveragesSync();

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
