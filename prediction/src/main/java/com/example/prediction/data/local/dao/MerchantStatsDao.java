package com.example.prediction.data.local.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;
import com.example.prediction.data.local.entity.MerchantStatsEntity;

@Dao
public interface MerchantStatsDao {
    @Query("SELECT * FROM merchant_stats WHERE merchantName = :name LIMIT 1")
    MerchantStatsEntity getStatsForMerchant(String name);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(MerchantStatsEntity stats);

    @Update
    void update(MerchantStatsEntity stats);
}
