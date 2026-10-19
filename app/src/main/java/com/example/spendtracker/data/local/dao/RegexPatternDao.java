package com.example.spendtracker.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;
import com.example.spendtracker.data.local.entity.RegexPatternEntity;
import java.util.List;

@Dao
public interface RegexPatternDao {
    @Query("SELECT * FROM regex_patterns")
    LiveData<List<RegexPatternEntity>> getAllPatterns();

    @Query("SELECT * FROM regex_patterns")
    List<RegexPatternEntity> getAllPatternsSync();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertPattern(RegexPatternEntity pattern);

    @Update
    void updatePattern(RegexPatternEntity pattern);

    @Delete
    void deletePattern(RegexPatternEntity pattern);
}
