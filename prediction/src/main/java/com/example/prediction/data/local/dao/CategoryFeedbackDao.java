package com.example.prediction.data.local.dao;

import androidx.room.*;
import com.example.prediction.data.local.entity.CategoryFeedbackEntity;
import java.util.List;

@Dao
public interface CategoryFeedbackDao {
    @Query("SELECT * FROM category_feedback ORDER BY updatedAt, id")
    List<CategoryFeedbackEntity> getAll();
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void put(CategoryFeedbackEntity feedback);
    @Query("DELETE FROM category_feedback WHERE id = :id")
    void delete(String id);
    @Query("DELETE FROM category_feedback")
    void deleteAll();
}
