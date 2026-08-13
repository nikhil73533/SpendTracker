package com.example.spendtracker.di;

import android.content.Context;
import androidx.room.Room;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;
import com.example.spendtracker.data.local.dao.CategoryDao;
import com.example.spendtracker.data.local.dao.RegexPatternDao;
import com.example.spendtracker.data.local.dao.TransactionDao;
import com.example.spendtracker.data.local.database.SpendTrackerDatabase;

import javax.inject.Singleton;
import dagger.Module;
import dagger.Provides;
import dagger.hilt.InstallIn;
import dagger.hilt.android.qualifiers.ApplicationContext;
import dagger.hilt.components.SingletonComponent;

@Module
@InstallIn(SingletonComponent.class)
public class DatabaseModule {

    @Provides
    @Singleton
    public SpendTrackerDatabase provideDatabase(@ApplicationContext Context context) {
        return Room.databaseBuilder(context, SpendTrackerDatabase.class, "spend_tracker_db")
                .addMigrations(MIGRATION_3_4)
                .build();
    }

    static final Migration MIGRATION_3_4 = new Migration(3, 4) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE categories ADD COLUMN type TEXT DEFAULT 'EXPENSE'");
        }
    };

    @Provides
    public TransactionDao provideTransactionDao(SpendTrackerDatabase database) {
        return database.transactionDao();
    }

    @Provides
    public CategoryDao provideCategoryDao(SpendTrackerDatabase database) {
        return database.categoryDao();
    }

    @Provides
    public RegexPatternDao provideRegexPatternDao(SpendTrackerDatabase database) {
        return database.regexPatternDao();
    }
}
