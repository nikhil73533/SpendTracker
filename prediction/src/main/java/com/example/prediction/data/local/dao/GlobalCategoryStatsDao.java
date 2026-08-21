package com.example.prediction.data.local.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;
import com.example.prediction.data.local.entity.GlobalCategoryStatsEntity;
import java.util.List;

@Dao
public interface GlobalCategoryStatsDao {

    @Query("SELECT * FROM global_category_stats")
    List<GlobalCategoryStatsEntity> getAll();

    @Query("SELECT * FROM global_category_stats WHERE category = :category LIMIT 1")
    GlobalCategoryStatsEntity getByCategory(String category);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(GlobalCategoryStatsEntity entity);

    @Update
    void update(GlobalCategoryStatsEntity entity);

    @Query("DELETE FROM global_category_stats")
    void deleteAll();
}
