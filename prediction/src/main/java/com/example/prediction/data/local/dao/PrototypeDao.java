package com.example.prediction.data.local.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import com.example.prediction.data.local.entity.PrototypeEntity;
import java.util.List;

@Dao
public interface PrototypeDao {
    @Insert
    void insert(PrototypeEntity prototype);

    @Query("SELECT * FROM prototypes")
    List<PrototypeEntity> getAllPrototypes();

    @Query("SELECT * FROM prototypes WHERE type = :type")
    List<PrototypeEntity> getPrototypesByType(String type);

    @Query("DELETE FROM prototypes")
    void deleteAll();
}
