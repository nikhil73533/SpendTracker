package com.example.spendtracker.di;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;
import com.example.spendtracker.data.local.dao.CategoryDao;
import com.example.spendtracker.data.local.dao.RegexPatternDao;
import com.example.spendtracker.data.local.dao.TransactionDao;
import com.example.spendtracker.data.local.dao.TransactionGroupDao;
import com.example.spendtracker.data.local.dao.RepeatedAlertDao;
import com.example.spendtracker.data.local.dao.BillAlertDao;
import com.example.spendtracker.data.local.database.SpendTrackerDatabase;
import com.example.spendtracker.domain.repository.SecurityRepository;
import java.io.File;

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
    @MainDatabase
    public SpendTrackerDatabase provideDatabase(@ApplicationContext Context context, SecurityRepository securityRepository) {
        byte[] passphrase = securityRepository.getDatabasePassphrase();
        
        File dbFile = context.getDatabasePath("spend_tracker_db");
        // If DB is missing or empty (< 10KB), try to recover from external backup if available
        if (!dbFile.exists() || dbFile.length() < 10240) {
            File extBackup = new File(context.getExternalFilesDir(null), "backup.zip");
            if (extBackup.exists()) {
                android.util.Log.e("RECOVERY_CORE", "DB is missing or empty. Staging external backup for recovery.");
                try {
                    com.example.spendtracker.util.StorageHelper.copyFile(extBackup, new File(context.getCacheDir(), "backup.zip"));
                } catch (java.io.IOException e) {
                    e.printStackTrace();
                }
            }
        }

        // ULTIMATE RECOVERY: If a backup ZIP exists in cache, it likely has the missing August data
        com.example.spendtracker.util.DatabaseEncryptionHelper.ultimateRecoveryFromCache(context, "spend_tracker_db", passphrase);

        // Migrate unencrypted DB if it exists (checkpointing WAL now included)
        com.example.spendtracker.util.DatabaseEncryptionHelper.migrateIfNecessary(context, "spend_tracker_db", passphrase);

        SupportFactory factory = new SupportFactory(passphrase);

        SpendTrackerDatabase db = Room.databaseBuilder(context, SpendTrackerDatabase.class, "spend_tracker_db")
                .openHelperFactory(factory)
                .addMigrations(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13)
                .addCallback(SANITIZE_CALLBACK)
                .fallbackToDestructiveMigrationOnDowngrade()
                .build();
        
        return db;
    }

    @Provides
    @Singleton
    @ClonedDatabase
    public SpendTrackerDatabase provideClonedDatabase(@ApplicationContext Context context, SecurityRepository securityRepository) {
        byte[] passphrase = securityRepository.getDatabasePassphrase();
        SupportFactory factory = new SupportFactory(passphrase);

        return Room.databaseBuilder(context, SpendTrackerDatabase.class, "spend_tracker_db_cloned")
                .openHelperFactory(factory)
                .addMigrations(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13)
                .addCallback(SANITIZE_CALLBACK)
                .fallbackToDestructiveMigrationOnDowngrade()
                .build();
    }

    private static final RoomDatabase.Callback SANITIZE_CALLBACK = new RoomDatabase.Callback() {
        @Override
        public void onOpen(@NonNull SupportSQLiteDatabase db) {
            super.onOpen(db);
            try {
                db.beginTransaction();
                db.execSQL("UPDATE transactions SET status = 'ACTIVE' WHERE status IS NULL OR status = '';");
                db.execSQL("UPDATE transactions SET categoryEmoji = '' WHERE categoryEmoji IS NULL;");
                db.execSQL("UPDATE transactions SET fromAccount = '' WHERE fromAccount IS NULL;");
                db.execSQL("UPDATE transactions SET toAccount = '' WHERE toAccount IS NULL;");
                db.execSQL("UPDATE transactions SET fees = 0.0 WHERE fees IS NULL;");
                db.execSQL("UPDATE transactions SET transactionGroupId = 0 WHERE transactionGroupId IS NULL;");
                db.execSQL("UPDATE transactions SET deletedAt = 0 WHERE deletedAt IS NULL;");
                db.execSQL("UPDATE transactions SET isRead = 1 WHERE isRead IS NULL;");
                db.execSQL("UPDATE transactions SET category = 'Uncategorized' WHERE category IS NULL OR category = '';");
                db.execSQL("UPDATE transactions SET type = 'EXPENSE' WHERE type IS NULL OR type = '';");
                db.execSQL("UPDATE transactions SET description = '' WHERE description IS NULL;");
                db.execSQL("UPDATE transactions SET source = '' WHERE source IS NULL;");
                db.execSQL("UPDATE transactions SET sender = '' WHERE sender IS NULL;");
                db.execSQL("UPDATE transactions SET upiId = '' WHERE upiId IS NULL;");
                db.execSQL("UPDATE transactions SET receiverName = '' WHERE receiverName IS NULL;");
                db.execSQL("UPDATE transactions SET bankName = '' WHERE bankName IS NULL;");
                db.execSQL("UPDATE transactions SET sourceType = '' WHERE sourceType IS NULL;");
                db.execSQL("UPDATE categories SET type = 'EXPENSE' WHERE type IS NULL OR type = '';");
                db.execSQL("UPDATE categories SET isDefault = 0 WHERE isDefault IS NULL;");
                db.execSQL("UPDATE categories SET name = '' WHERE name IS NULL;");
                db.execSQL("UPDATE categories SET icon = '' WHERE icon IS NULL;");
                db.execSQL("UPDATE categories SET unlimitedWeekly = 1 WHERE unlimitedWeekly IS NULL;");
                db.execSQL("UPDATE categories SET weeklyBudget = 0.0 WHERE weeklyBudget IS NULL;");
                db.execSQL("UPDATE categories SET unlimitedMonthly = 1 WHERE unlimitedMonthly IS NULL;");
                db.execSQL("UPDATE categories SET monthlyBudget = 0.0 WHERE monthlyBudget IS NULL;");
                db.execSQL("UPDATE categories SET unlimitedAnnually = 1 WHERE unlimitedAnnually IS NULL;");
                db.execSQL("UPDATE categories SET annuallyBudget = 0.0 WHERE annuallyBudget IS NULL;");
                db.execSQL("UPDATE categories SET notificationsEnabled = 1 WHERE notificationsEnabled IS NULL;");
                db.execSQL("UPDATE transaction_groups SET tag = '' WHERE tag IS NULL;");
                db.execSQL("UPDATE transaction_groups SET name = '' WHERE name IS NULL;");
                db.execSQL("UPDATE repeated_transaction_alerts SET enabled = 1 WHERE enabled IS NULL;");
                db.execSQL("UPDATE repeated_transaction_alerts SET dismissed = 0 WHERE dismissed IS NULL;");
                db.execSQL("UPDATE repeated_transaction_alerts SET merchantName = '' WHERE merchantName IS NULL;");
                db.execSQL("UPDATE repeated_transaction_alerts SET category = '' WHERE category IS NULL;");
                db.setTransactionSuccessful();
            } catch (Exception e) {
                android.util.Log.e("RECOVERY_CORE", "Database sanitization error: " + e.getMessage());
            } finally {
                db.endTransaction();
            }
        }
    };

    /**
     * Migration 9 → 10:
     * Robust recreation of the transactions table to fix schema inconsistencies
     * and missing columns/defaults from previous migrations.
     */
    static final Migration MIGRATION_9_10 = new Migration(9, 10) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            android.util.Log.e("RECOVERY_CORE", "Starting Migration 9 -> 10 (Robust Table Recreation)");
            
            // ── TRANSACTIONS TABLE ───────────────────────────────────────────
            database.execSQL("CREATE TABLE IF NOT EXISTS `transactions_new` ("
                    + "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
                    + "`amount` REAL NOT NULL, "
                    + "`category` TEXT, "
                    + "`categoryEmoji` TEXT DEFAULT '', "
                    + "`description` TEXT, "
                    + "`type` TEXT, "
                    + "`date` INTEGER NOT NULL, "
                    + "`source` TEXT, "
                    + "`sender` TEXT, "
                    + "`upiId` TEXT, "
                    + "`receiverName` TEXT, "
                    + "`bankName` TEXT, "
                    + "`sourceType` TEXT, "
                    + "`isRead` INTEGER NOT NULL DEFAULT 1, "
                    + "`fromAccount` TEXT DEFAULT '', "
                    + "`toAccount` TEXT DEFAULT '', "
                    + "`fees` REAL NOT NULL DEFAULT 0.0, "
                    + "`transactionGroupId` INTEGER NOT NULL DEFAULT 0, "
                    + "`status` TEXT NOT NULL DEFAULT 'ACTIVE', "
                    + "`deletedAt` INTEGER NOT NULL DEFAULT 0)");

            String[] txnTarget = {"id", "amount", "category", "categoryEmoji", "description", "type", "date", "source", "sender", "upiId", "receiverName", "bankName", "sourceType", "isRead", "fromAccount", "toAccount", "fees", "transactionGroupId", "status", "deletedAt"};
            java.util.List<String> txnExisting = getColumnNames(database, "transactions");
            
            StringBuilder txnSelect = new StringBuilder();
            for (int i = 0; i < txnTarget.length; i++) {
                String col = txnTarget[i];
                if (txnExisting.contains(col)) txnSelect.append("`").append(col).append("` ");
                else {
                    if (col.equals("categoryEmoji")) txnSelect.append("'' ");
                    else if (col.equals("isRead")) txnSelect.append("1 ");
                    else if (col.equals("fromAccount")) txnSelect.append("'' ");
                    else if (col.equals("toAccount")) txnSelect.append("'' ");
                    else if (col.equals("fees")) txnSelect.append("0.0 ");
                    else if (col.equals("transactionGroupId")) txnSelect.append("0 ");
                    else if (col.equals("status")) txnSelect.append("'ACTIVE' ");
                    else if (col.equals("deletedAt")) txnSelect.append("0 ");
                    else txnSelect.append("NULL ");
                }
                txnSelect.append("AS `").append(col).append("`").append(i < txnTarget.length - 1 ? ", " : "");
            }
            database.execSQL("INSERT INTO `transactions_new` (" + String.join(", ", txnTarget) + ") SELECT " + txnSelect + " FROM `transactions` ");
            database.execSQL("DROP TABLE `transactions` ");
            database.execSQL("ALTER TABLE `transactions_new` RENAME TO `transactions` ");
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_transactionGroupId` ON `transactions` (`transactionGroupId`)");
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_status` ON `transactions` (`status`)");

            // ── CATEGORIES TABLE ─────────────────────────────────────────────
            database.execSQL("CREATE TABLE IF NOT EXISTS `categories_new` ("
                    + "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
                    + "`name` TEXT, "
                    + "`icon` TEXT, "
                    + "`isDefault` INTEGER NOT NULL, "
                    + "`type` TEXT DEFAULT 'EXPENSE')");

            String[] catTarget = {"id", "name", "icon", "isDefault", "type"};
            java.util.List<String> catExisting = getColumnNames(database, "categories");
            
            StringBuilder catSelect = new StringBuilder();
            for (int i = 0; i < catTarget.length; i++) {
                String col = catTarget[i];
                if (catExisting.contains(col)) catSelect.append("`").append(col).append("` ");
                else {
                    if (col.equals("type")) catSelect.append("'EXPENSE' ");
                    else if (col.equals("isDefault")) catSelect.append("0 ");
                    else catSelect.append("NULL ");
                }
                catSelect.append("AS `").append(col).append("`").append(i < catTarget.length - 1 ? ", " : "");
            }
            database.execSQL("INSERT INTO `categories_new` (" + String.join(", ", catTarget) + ") SELECT " + catSelect + " FROM `categories` ");
            database.execSQL("DROP TABLE `categories` ");
            database.execSQL("ALTER TABLE `categories_new` RENAME TO `categories` ");

            android.util.Log.e("RECOVERY_CORE", "Migration 9 -> 10 Complete.");
        }

        private java.util.List<String> getColumnNames(SupportSQLiteDatabase database, String tableName) {
            java.util.List<String> names = new java.util.ArrayList<>();
            try (android.database.Cursor cursor = database.query("PRAGMA table_info(" + tableName + ")")) {
                while (cursor.moveToNext()) names.add(cursor.getString(cursor.getColumnIndexOrThrow("name")));
            }
            return names;
        }
    };

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

    /**
     * Migration 6 → 7:
     * - Add fromAccount, toAccount, fees to transactions
     */
    static final Migration MIGRATION_6_7 = new Migration(6, 7) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            // Using try-catch to handle cases where columns might already exist due to incomplete previous migrations or manual edits
            try {
                database.execSQL("ALTER TABLE transactions ADD COLUMN fromAccount TEXT");
            } catch (Exception e) {
                android.util.Log.w("RECOVERY_CORE", "Column fromAccount already exists or error adding it: " + e.getMessage());
            }
            try {
                database.execSQL("ALTER TABLE transactions ADD COLUMN toAccount TEXT");
            } catch (Exception e) {
                android.util.Log.w("RECOVERY_CORE", "Column toAccount already exists or error adding it: " + e.getMessage());
            }
            try {
                database.execSQL("ALTER TABLE transactions ADD COLUMN fees REAL NOT NULL DEFAULT 0.0");
            } catch (Exception e) {
                android.util.Log.w("RECOVERY_CORE", "Column fees already exists or error adding it: " + e.getMessage());
            }

            // Fix for missing tag column in transaction_groups
            try {
                database.execSQL("ALTER TABLE transaction_groups ADD COLUMN tag TEXT DEFAULT ''");
            } catch (Exception e) {
                android.util.Log.w("RECOVERY_CORE", "Column tag already exists or error adding it: " + e.getMessage());
            }
        }
    };

    /**
     * Migration 7 → 8:
     * - Create repeated_transaction_alerts table
     */
    static final Migration MIGRATION_7_8 = new Migration(7, 8) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS `repeated_transaction_alerts` ("
                    + "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
                    + "`merchantName` TEXT, "
                    + "`amount` REAL NOT NULL, "
                    + "`firstTransactionDate` INTEGER NOT NULL, "
                    + "`secondTransactionDate` INTEGER NOT NULL, "
                    + "`firstTransactionId` INTEGER NOT NULL, "
                    + "`secondTransactionId` INTEGER NOT NULL, "
                    + "`enabled` INTEGER NOT NULL DEFAULT 1, "
                    + "`dismissed` INTEGER NOT NULL DEFAULT 0, "
                    + "`createdAt` INTEGER NOT NULL, "
                    + "`category` TEXT)");
        }
    };

    /**
     * Migration 8 → 9:
     * - Add categoryEmoji to transactions
     */
    static final Migration MIGRATION_8_9 = new Migration(8, 9) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            try {
                database.execSQL("ALTER TABLE transactions ADD COLUMN categoryEmoji TEXT DEFAULT ''");
            } catch (Exception e) {
                android.util.Log.w("RECOVERY_CORE", "Column categoryEmoji already exists or error adding it: " + e.getMessage());
            }
        }
    };

    /**
     * Migration 10 → 11:
     * - Create bill_alerts table
     */
    static final Migration MIGRATION_10_11 = new Migration(10, 11) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS `bill_alerts` ("
                    + "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
                    + "`sender` TEXT, "
                    + "`template` TEXT, "
                    + "`lastMessage` TEXT, "
                    + "`occurrenceCount` INTEGER NOT NULL, "
                    + "`lastSeen` INTEGER NOT NULL, "
                    + "`amount` REAL NOT NULL, "
                    + "`isResolved` INTEGER NOT NULL)");
        }
    };

    /**
     * Migration 11 → 12:
     * - Add budget range columns and notificationsEnabled to categories
     */
    static final Migration MIGRATION_11_12 = new Migration(11, 12) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            try { database.execSQL("ALTER TABLE categories ADD COLUMN unlimitedWeekly INTEGER NOT NULL DEFAULT 1"); } catch (Exception ignored) {}
            try { database.execSQL("ALTER TABLE categories ADD COLUMN weeklyBudget REAL NOT NULL DEFAULT 0.0"); } catch (Exception ignored) {}
            try { database.execSQL("ALTER TABLE categories ADD COLUMN unlimitedMonthly INTEGER NOT NULL DEFAULT 1"); } catch (Exception ignored) {}
            try { database.execSQL("ALTER TABLE categories ADD COLUMN monthlyBudget REAL NOT NULL DEFAULT 0.0"); } catch (Exception ignored) {}
            try { database.execSQL("ALTER TABLE categories ADD COLUMN unlimitedAnnually INTEGER NOT NULL DEFAULT 1"); } catch (Exception ignored) {}
            try { database.execSQL("ALTER TABLE categories ADD COLUMN annuallyBudget REAL NOT NULL DEFAULT 0.0"); } catch (Exception ignored) {}
            try { database.execSQL("ALTER TABLE categories ADD COLUMN notificationsEnabled INTEGER NOT NULL DEFAULT 1"); } catch (Exception ignored) {}
        }
    };

    /**
     * Migration 12 → 13:
     * Persist statement provenance so that imports can be reviewed, traced, and safely
     * deduplicated without changing the existing internal auto-increment transaction ID.
     */
    static final Migration MIGRATION_12_13 = new Migration(12, 13) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE transactions ADD COLUMN sourceTransactionId TEXT");
            database.execSQL("ALTER TABLE transactions ADD COLUMN referenceNumber TEXT");
            database.execSQL("ALTER TABLE transactions ADD COLUMN direction TEXT NOT NULL DEFAULT 'UNKNOWN'");
            database.execSQL("ALTER TABLE transactions ADD COLUMN timestampPrecision TEXT NOT NULL DEFAULT 'DATE_TIME'");
            database.execSQL("ALTER TABLE transactions ADD COLUMN importBatchId TEXT");
            database.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_status_date_type_category ON transactions (status, date, type, category)");
            // SQLite unique indexes allow multiple NULLs, preserving existing manual rows.
            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_transactions_sourceTransactionId ON transactions (sourceTransactionId)");
        }
    };

    @Provides
    @MainDatabase
    public TransactionDao provideTransactionDao(@MainDatabase SpendTrackerDatabase database) {
        return database.transactionDao();
    }

    @Provides
    @ClonedDatabase
    public TransactionDao provideClonedTransactionDao(@ClonedDatabase SpendTrackerDatabase database) {
        return database.transactionDao();
    }

    @Provides
    public CategoryDao provideCategoryDao(@MainDatabase SpendTrackerDatabase database) {
        return database.categoryDao();
    }

    @Provides
    public RegexPatternDao provideRegexPatternDao(@MainDatabase SpendTrackerDatabase database) {
        return database.regexPatternDao();
    }

    @Provides
    public TransactionGroupDao provideTransactionGroupDao(@MainDatabase SpendTrackerDatabase database) {
        return database.transactionGroupDao();
    }

    @Provides
    public RepeatedAlertDao provideRepeatedAlertDao(@MainDatabase SpendTrackerDatabase database) {
        return database.repeatedAlertDao();
    }

    @Provides
    public BillAlertDao provideBillAlertDao(@MainDatabase SpendTrackerDatabase database) {
        return database.billAlertDao();
    }
}
