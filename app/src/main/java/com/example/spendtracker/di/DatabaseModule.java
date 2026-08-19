package com.example.spendtracker.di;

import android.content.Context;
import androidx.room.Room;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;
import com.example.spendtracker.data.local.dao.CategoryDao;
import com.example.spendtracker.data.local.dao.RegexPatternDao;
import com.example.spendtracker.data.local.dao.TransactionDao;
import com.example.spendtracker.data.local.database.SpendTrackerDatabase;
import com.example.spendtracker.domain.repository.SecurityRepository;

import javax.inject.Singleton;
import dagger.Module;
import dagger.Provides;
import dagger.hilt.InstallIn;
import dagger.hilt.android.qualifiers.ApplicationContext;
import dagger.hilt.components.SingletonComponent;
import net.sqlcipher.database.SupportFactory;

@Module
@InstallIn(SingletonComponent.class)
public class DatabaseModule {

    @Provides
    @Singleton
    public SpendTrackerDatabase provideDatabase(@ApplicationContext Context context, SecurityRepository securityRepository) {
        byte[] passphrase = securityRepository.getDatabasePassphrase();
        
        // ULTIMATE RECOVERY: If a backup ZIP exists in cache, it likely has the missing August data
        com.example.spendtracker.util.DatabaseEncryptionHelper.ultimateRecoveryFromCache(context, "spend_tracker_db", passphrase);

        // Migrate unencrypted DB if it exists (checkpointing WAL now included)
        com.example.spendtracker.util.DatabaseEncryptionHelper.migrateIfNecessary(context, "spend_tracker_db", passphrase);

        SupportFactory factory = new SupportFactory(passphrase);

        SpendTrackerDatabase db = Room.databaseBuilder(context, SpendTrackerDatabase.class, "spend_tracker_db")
                .openHelperFactory(factory)
                .addMigrations(MIGRATION_3_4, MIGRATION_4_5)
                .build();
        
        return db;
    }

    static final Migration MIGRATION_3_4 = new Migration(3, 4) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE categories ADD COLUMN type TEXT DEFAULT 'EXPENSE'");
        }
    };

    static final Migration MIGRATION_4_5 = new Migration(4, 5) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE transactions ADD COLUMN isRead INTEGER NOT NULL DEFAULT 1");
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
