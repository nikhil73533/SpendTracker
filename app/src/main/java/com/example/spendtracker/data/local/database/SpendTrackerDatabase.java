package com.example.spendtracker.data.local.database;

import androidx.room.Database;
import androidx.room.RoomDatabase;
import com.example.spendtracker.data.local.dao.CategoryDao;
import com.example.spendtracker.data.local.dao.RegexPatternDao;
import com.example.spendtracker.data.local.dao.TransactionDao;
import com.example.spendtracker.data.local.entity.CategoryEntity;
import com.example.spendtracker.data.local.entity.RegexPatternEntity;
import com.example.spendtracker.data.local.entity.TransactionEntity;

@Database(entities = {TransactionEntity.class, CategoryEntity.class, RegexPatternEntity.class}, version = 4, exportSchema = false)
public abstract class SpendTrackerDatabase extends RoomDatabase {
    public abstract TransactionDao transactionDao();
    public abstract CategoryDao categoryDao();
    public abstract RegexPatternDao regexPatternDao();
}
