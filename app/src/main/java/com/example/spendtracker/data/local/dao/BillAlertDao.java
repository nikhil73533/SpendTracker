package com.example.spendtracker.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;
import com.example.spendtracker.data.local.entity.BillAlertEntity;
import java.util.List;

@Dao
public interface BillAlertDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(BillAlertEntity alert);

    @Update
    void update(BillAlertEntity alert);

    @Query("SELECT * FROM bill_alerts WHERE template = :template AND sender = :sender LIMIT 1")
    BillAlertEntity findByTemplate(String template, String sender);

    @Query("SELECT * FROM bill_alerts WHERE isResolved = 0 ORDER BY lastSeen DESC")
    LiveData<List<BillAlertEntity>> getActiveAlerts();

    @Query("SELECT * FROM bill_alerts ORDER BY lastSeen DESC")
    LiveData<List<BillAlertEntity>> getAllAlerts();

    @Query("UPDATE bill_alerts SET isResolved = 1 WHERE id = :id")
    void resolveAlert(int id);

    @Query("SELECT * FROM bill_alerts WHERE isResolved = 0")
    List<BillAlertEntity> getActiveAlertsSync();

    @Query("SELECT * FROM bill_alerts WHERE id = :id")
    BillAlertEntity getById(int id);

    @Query("SELECT * FROM bill_alerts WHERE sender = :sender AND template = :template AND dueEpochDay = :due LIMIT 1")
    BillAlertEntity findBill(String sender, String template, long due);

    @Query("DELETE FROM bill_alerts WHERE id = :id")
    void delete(int id);
}
