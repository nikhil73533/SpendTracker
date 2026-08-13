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

    @Query("SELECT receiverName as name, upiId, MAX(date) as lastTransactionDate, SUM(CASE WHEN type = 'EXPENSE' THEN amount ELSE 0 END) as totalExpense FROM transactions GROUP BY COALESCE(NULLIF(upiId, ''), receiverName) ORDER BY lastTransactionDate DESC")
    LiveData<List<AccountSummary>> getUniqueAccounts();

    @Query("SELECT * FROM transactions WHERE COALESCE(NULLIF(upiId, ''), receiverName) = :accountId AND date BETWEEN :start AND :end ORDER BY date ASC")
    LiveData<List<TransactionEntity>> getAccountHistory(String accountId, long start, long end);

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
    }
}
