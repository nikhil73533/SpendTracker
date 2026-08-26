package com.example.spendtracker.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;
import com.example.spendtracker.data.local.entity.RepeatedAlertEntity;
import java.util.List;

@Dao
public interface RepeatedAlertDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(RepeatedAlertEntity alert);

    @Update
    void update(RepeatedAlertEntity alert);

    @Delete
    void delete(RepeatedAlertEntity alert);

    @Query("SELECT * FROM repeated_transaction_alerts WHERE dismissed = 0 ORDER BY createdAt DESC")
    LiveData<List<RepeatedAlertEntity>> getActiveAlerts();

    @Query("SELECT * FROM repeated_transaction_alerts ORDER BY createdAt DESC")
    LiveData<List<RepeatedAlertEntity>> getAllAlerts();

    @Query("SELECT * FROM repeated_transaction_alerts WHERE id = :id")
    RepeatedAlertEntity getAlertByIdSync(int id);

    @Query("UPDATE repeated_transaction_alerts SET dismissed = 1 WHERE id = :id")
    void dismissAlert(int id);

    @Query("UPDATE repeated_transaction_alerts SET enabled = :enabled WHERE id = :id")
    void setAlertEnabled(int id, boolean enabled);

    @Query("DELETE FROM repeated_transaction_alerts WHERE dismissed = 1")
    void clearDismissedAlerts();

    /** Check if an alert already exists for this transaction pair to avoid duplicates. */
    @Query("SELECT COUNT(*) FROM repeated_transaction_alerts WHERE firstTransactionId = :txId1 AND secondTransactionId = :txId2")
    int alertExistsForPair(int txId1, int txId2);

    /** Find potential duplicate transactions: same merchant, same amount, within time window. */
    @Query("SELECT * FROM transactions WHERE status = 'ACTIVE' AND LOWER(TRIM(receiverName)) = LOWER(TRIM(:merchantName)) AND ABS(amount - :amount) < 0.01 AND id != :excludeId AND date BETWEEN :startDate AND :endDate")
    List<com.example.spendtracker.data.local.entity.TransactionEntity> findPotentialDuplicates(
            String merchantName, double amount, int excludeId, long startDate, long endDate);
}
