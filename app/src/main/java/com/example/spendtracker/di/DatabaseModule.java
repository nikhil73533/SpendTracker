package com.example.spendtracker.di;

import android.content.Context;
import androidx.room.Room;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;
import com.example.spendtracker.data.local.dao.CategoryDao;
import com.example.spendtracker.data.local.dao.RegexPatternDao;
import com.example.spendtracker.data.local.dao.TransactionDao;
import com.example.spendtracker.data.local.dao.TransactionGroupDao;
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
                .addMigrations(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
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

    /**
     * Migration 5 → 6:
     * - Add transactionGroupId, status, deletedAt to transactions
     * - Create transaction_groups table
     * - Create transaction_group_categories table
     */
    static final Migration MIGRATION_5_6 = new Migration(5, 6) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            // Add new columns to transactions
            database.execSQL("ALTER TABLE transactions ADD COLUMN transactionGroupId INTEGER NOT NULL DEFAULT 0");
            database.execSQL("ALTER TABLE transactions ADD COLUMN status TEXT NOT NULL DEFAULT 'ACTIVE'");
            database.execSQL("ALTER TABLE transactions ADD COLUMN deletedAt INTEGER NOT NULL DEFAULT 0");

            // Create transaction_groups table
            database.execSQL("CREATE TABLE IF NOT EXISTS `transaction_groups` ("
                    + "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
                    + "`name` TEXT, "
                    + "`startDate` INTEGER NOT NULL, "
                    + "`endDate` INTEGER NOT NULL, "
                    + "`createdAt` INTEGER NOT NULL, "
                    + "`isActive` INTEGER NOT NULL)");
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_transaction_groups_startDate_endDate` ON `transaction_groups` (`startDate`, `endDate`)");
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_transaction_groups_createdAt` ON `transaction_groups` (`createdAt`)");

            // Create transaction_group_categories table
            database.execSQL("CREATE TABLE IF NOT EXISTS `transaction_group_categories` ("
                    + "`groupId` INTEGER NOT NULL, "
                    + "`categoryName` TEXT NOT NULL, "
                    + "PRIMARY KEY(`groupId`, `categoryName`), "
                    + "FOREIGN KEY(`groupId`) REFERENCES `transaction_groups`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)");
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_transaction_group_categories_groupId` ON `transaction_group_categories` (`groupId`)");

            // Create indices on transactions for the new columns
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_transactionGroupId` ON `transactions` (`transactionGroupId`)");
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_status` ON `transactions` (`status`)");
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

    @Provides
    public TransactionGroupDao provideTransactionGroupDao(SpendTrackerDatabase database) {
        return database.transactionGroupDao();
    }
}
