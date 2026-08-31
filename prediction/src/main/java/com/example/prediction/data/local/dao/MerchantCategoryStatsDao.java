package com.example.prediction.data.local.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;
import com.example.prediction.data.local.entity.MerchantCategoryStatsEntity;
import java.util.List;

@Dao
public interface MerchantCategoryStatsDao {

    @Query("SELECT * FROM merchant_category_stats WHERE merchantKey = :merchantKey AND transactionType = :type")
    List<MerchantCategoryStatsEntity> getStatsForMerchant(String merchantKey, String type);

    @Query("SELECT * FROM merchant_category_stats WHERE merchantKey = :merchantKey AND transactionType = :transactionType")
    List<MerchantCategoryStatsEntity> getStatsForMerchantByType(String merchantKey, String transactionType);

    @Query("SELECT * FROM merchant_category_stats WHERE id = :id LIMIT 1")
    MerchantCategoryStatsEntity getById(String id);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(MerchantCategoryStatsEntity entity);

    @Update
    void update(MerchantCategoryStatsEntity entity);

    @Query("DELETE FROM merchant_category_stats")
    void deleteAll();
}
