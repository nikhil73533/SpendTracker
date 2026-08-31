package com.example.spendtracker.data.local.database;

import androidx.room.Database;
import androidx.room.RoomDatabase;
import com.example.spendtracker.data.local.dao.CategoryDao;
import com.example.spendtracker.data.local.dao.RegexPatternDao;
import com.example.spendtracker.data.local.dao.TransactionDao;
import com.example.spendtracker.data.local.dao.TransactionGroupDao;
import com.example.spendtracker.data.local.entity.CategoryEntity;
import com.example.spendtracker.data.local.entity.RegexPatternEntity;
import com.example.spendtracker.data.local.entity.TransactionEntity;
import com.example.spendtracker.data.local.entity.TransactionGroupCategoryEntity;
import com.example.spendtracker.data.local.entity.TransactionGroupEntity;
import com.example.spendtracker.data.local.entity.RepeatedAlertEntity;
import com.example.spendtracker.data.local.dao.RepeatedAlertDao;
import com.example.spendtracker.data.local.entity.BillAlertEntity;
import com.example.spendtracker.data.local.dao.BillAlertDao;

@Database(entities = {
    TransactionEntity.class,
    CategoryEntity.class,
    RegexPatternEntity.class,
    TransactionGroupEntity.class,
    TransactionGroupCategoryEntity.class,
    RepeatedAlertEntity.class,
    BillAlertEntity.class
}, version = 11, exportSchema = false)
public abstract class SpendTrackerDatabase extends RoomDatabase {
    public abstract TransactionDao transactionDao();
    public abstract CategoryDao categoryDao();
    public abstract RegexPatternDao regexPatternDao();
    public abstract TransactionGroupDao transactionGroupDao();
    public abstract RepeatedAlertDao repeatedAlertDao();
    public abstract BillAlertDao billAlertDao();
}
