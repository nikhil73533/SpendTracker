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

@Dao
public interface TransactionDao {
    @Query("SELECT * FROM transactions ORDER BY date DESC")
    LiveData<List<TransactionEntity>> getAllTransactions();

    @Query("SELECT * FROM transactions ORDER BY date DESC")
    List<TransactionEntity> getAllTransactionsSync();

    @Query("SELECT * FROM transactions WHERE date BETWEEN :start AND :end ORDER BY date DESC")
    LiveData<List<TransactionEntity>> getTransactionsInRange(long start, long end);

    @Query("SELECT * FROM transactions WHERE id = :id")
    LiveData<TransactionEntity> getTransactionById(int id);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertTransaction(TransactionEntity transaction);

    @Update
    void updateTransaction(TransactionEntity transaction);

    @Delete
    void deleteTransaction(TransactionEntity transaction);

    @Query("SELECT SUM(amount) FROM transactions WHERE type = 'INCOME' AND date BETWEEN :start AND :end")
    LiveData<Double> getTotalIncome(long start, long end);

    @Query("SELECT SUM(amount) FROM transactions WHERE type = 'EXPENSE' AND date BETWEEN :start AND :end")
    LiveData<Double> getTotalExpense(long start, long end);

    @Query("SELECT SUM(amount) FROM transactions WHERE sourceType = 'Account' AND date BETWEEN :start AND :end")
    LiveData<Double> getTotalAccountTransaction(long start, long end);

    @Query("SELECT category, SUM(amount) as total FROM transactions WHERE date BETWEEN :start AND :end GROUP BY category")
    LiveData<List<CategorySum>> getCategorySummaries(long start, long end);

    @Query("SELECT category, SUM(amount) as total FROM transactions WHERE type = 'EXPENSE' AND date BETWEEN :start AND :end GROUP BY category")
    LiveData<List<CategorySum>> getExpenseCategorySummaries(long start, long end);

    @Query("SELECT category, AVG(amount) as total FROM transactions WHERE type = 'EXPENSE' AND date BETWEEN :start AND :end GROUP BY category")
    LiveData<List<CategorySum>> getExpenseCategoryAverages(long start, long end);

    @Query("SELECT category, SUM(amount) as total FROM transactions WHERE type = 'INCOME' AND date BETWEEN :start AND :end GROUP BY category")
    LiveData<List<CategorySum>> getIncomeCategorySummaries(long start, long end);

    @Query("SELECT category, AVG(amount) as total FROM transactions WHERE type = 'INCOME' AND date BETWEEN :start AND :end GROUP BY category")
    LiveData<List<CategorySum>> getIncomeCategoryAverages(long start, long end);

    @Query("SELECT date as timestamp, SUM(amount) as total FROM transactions WHERE type = :type AND date BETWEEN :start AND :end GROUP BY (date / 86400000) ORDER BY date ASC")
    LiveData<List<TimeSum>> getDailyTotals(long start, long end, String type);

    @Query("SELECT MIN(date) as timestamp, SUM(amount) as total FROM transactions WHERE type = :type AND date BETWEEN :start AND :end GROUP BY strftime('%Y-%W', date / 1000, 'unixepoch') ORDER BY date ASC")
    LiveData<List<TimeSum>> getWeeklyTotals(long start, long end, String type);

    @Query("SELECT MIN(date) as timestamp, SUM(amount) as total FROM transactions WHERE type = :type AND date BETWEEN :start AND :end GROUP BY strftime('%Y-%m', date / 1000, 'unixepoch') ORDER BY date ASC")
    LiveData<List<TimeSum>> getMonthlyTotals(long start, long end, String type);

    @Query("SELECT MIN(date) as timestamp, SUM(amount) as total FROM transactions WHERE type = :type AND date BETWEEN :start AND :end GROUP BY strftime('%Y', date / 1000, 'unixepoch') ORDER BY date ASC")
    LiveData<List<TimeSum>> getAnnuallyTotals(long start, long end, String type);

    @Query("SELECT (CASE WHEN type = 'INCOME' THEN sender ELSE receiverName END) as name, upiId, MAX(date) as lastTransactionDate, SUM(CASE WHEN type = 'EXPENSE' THEN amount ELSE 0 END) as totalExpense, SUM(CASE WHEN type = 'INCOME' THEN amount ELSE 0 END) as totalIncome, SUM(CASE WHEN isRead = 0 THEN 1 ELSE 0 END) as unreadCount FROM transactions GROUP BY (CASE WHEN type = 'INCOME' THEN sender ELSE receiverName END) ORDER BY lastTransactionDate DESC")
    LiveData<List<AccountSummary>> getUniqueAccounts();

    @Query("SELECT (CASE WHEN strftime('%w', date/1000, 'unixepoch') IN ('0', '6') THEN 'Weekend' ELSE 'Weekday' END) as category, SUM(amount) as total FROM transactions WHERE date BETWEEN :start AND :end GROUP BY (CASE WHEN strftime('%w', date/1000, 'unixepoch') IN ('0', '6') THEN 'Weekend' ELSE 'Weekday' END)")
    LiveData<List<CategorySum>> getWeekdayWeekendTotals(long start, long end);

    @Query("SELECT bankName as category, SUM(amount) as total FROM transactions WHERE date BETWEEN :start AND :end GROUP BY bankName")
    LiveData<List<CategorySum>> getBankTotals(long start, long end);

    @Query("SELECT sourceType as category, SUM(amount) as total FROM transactions WHERE date BETWEEN :start AND :end GROUP BY sourceType")
    LiveData<List<CategorySum>> getSourceTypeTotals(long start, long end);

    @Query("UPDATE transactions SET isRead = 1 WHERE (receiverName = :accountName OR sender = :accountName)")
    void markAsRead(String accountName);

    @Query("SELECT * FROM transactions WHERE (receiverName = :accountName OR sender = :accountName) AND date BETWEEN :start AND :end ORDER BY date DESC")
    LiveData<List<TransactionEntity>> getAccountHistory(String accountName, long start, long end);

    @Query("UPDATE transactions SET category = :newName WHERE category = :oldName")
    void renameCategory(String oldName, String newName);

    @Query("SELECT DISTINCT name FROM (SELECT sender AS name FROM transactions WHERE sender IS NOT NULL AND sender != '' UNION SELECT receiverName AS name FROM transactions WHERE receiverName IS NOT NULL AND receiverName != '') ORDER BY name ASC")
    LiveData<List<String>> getUniqueContacts();

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
