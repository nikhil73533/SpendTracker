package com.example.spendtracker.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;
import com.example.spendtracker.data.local.entity.CategoryEntity;
import java.util.List;

/**
 * Data Access Object for category entities and budget properties.
 */
@Dao
public interface CategoryDao {
    @Query("SELECT * FROM categories")
    LiveData<List<CategoryEntity>> getAllCategories();

    @Query("SELECT * FROM categories")
    List<CategoryEntity> getAllCategoriesSync();

    @Query("SELECT * FROM categories WHERE type = :type")
    LiveData<List<CategoryEntity>> getCategoriesByType(String type);

    @Query("SELECT * FROM categories WHERE type = :type")
    List<CategoryEntity> getCategoriesByTypeSync(String type);

    @Query("SELECT * FROM categories WHERE LOWER(name) = LOWER(:name) LIMIT 1")
    CategoryEntity getCategoryByNameSync(String name);

    @Query("SELECT * FROM categories WHERE id = :id LIMIT 1")
    CategoryEntity getCategoryByIdSync(int id);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertCategory(CategoryEntity category);

    @Update
    void updateCategory(CategoryEntity category);

    @Delete
    void deleteCategory(CategoryEntity category);
}
