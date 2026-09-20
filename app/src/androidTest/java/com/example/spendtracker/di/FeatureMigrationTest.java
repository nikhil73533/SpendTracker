package com.example.spendtracker.di;

import android.content.Context;
import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.example.spendtracker.data.local.database.SpendTrackerDatabase;
import com.example.spendtracker.data.local.entity.TransactionEntity;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class FeatureMigrationTest {
    @Test public void version14UpgradePreservesRowsAndBackfillsDirection() {
        Context context = ApplicationProvider.getApplicationContext();
        String name = "feature-migration-test.db";
        context.deleteDatabase(name);
        SpendTrackerDatabase old = Room.databaseBuilder(context, SpendTrackerDatabase.class, name).allowMainThreadQueries().build();
        TransactionEntity tx = new TransactionEntity(); tx.amount = 100; tx.type = "INCOME"; tx.category = "Transfer";
        int id = (int) old.transactionDao().insertTransaction(tx);
        old.getOpenHelper().getWritableDatabase().execSQL("ALTER TABLE transactions DROP COLUMN confidenceScore");
        old.close();
        try (android.database.sqlite.SQLiteDatabase raw = android.database.sqlite.SQLiteDatabase.openDatabase(
                context.getDatabasePath(name).getPath(), null, android.database.sqlite.SQLiteDatabase.OPEN_READWRITE)) {
            raw.setVersion(14);
        }
        SpendTrackerDatabase upgraded = Room.databaseBuilder(context, SpendTrackerDatabase.class, name)
                .allowMainThreadQueries().addMigrations(DatabaseModule.MIGRATION_14_15).build();
        try {
            TransactionEntity saved = upgraded.transactionDao().getTransactionByIdSync(id);
            assertEquals(100, saved.amount, 0); assertEquals("CREDIT", saved.direction);
            assertEquals(1, saved.confidenceScore, 0);
        } finally { upgraded.close(); context.deleteDatabase(name); }
    }
}
