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
public class PredictionInsightsMigrationTest {
    @Test public void merchantCategoriesAreDistinctAndIncludeTransferHistory() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        SpendTrackerDatabase db = Room.inMemoryDatabaseBuilder(context, SpendTrackerDatabase.class).addCallback(com.example.spendtracker.data.local.database.AnalyticsMaterialization.CALLBACK).allowMainThreadQueries().build();
        try {
            for (String category : new String[]{"Food", " food ", "Travel", "Transfer"}) {
                TransactionEntity tx = new TransactionEntity(); tx.receiverName = "Test Merchant"; tx.category = category;
                tx.type = "Transfer".equals(category) ? "TRANSFER" : "EXPENSE";
                db.transactionDao().insertTransaction(tx);
            }
            TransactionEntity deleted = new TransactionEntity(); deleted.receiverName = "Test Merchant";
            deleted.category = "Shopping"; deleted.status = "DELETED"; db.transactionDao().insertTransaction(deleted);
            java.util.concurrent.CountDownLatch emitted = new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.atomic.AtomicReference<java.util.List<com.example.spendtracker.data.local.dao.TransactionDao.AccountSummary>> result = new java.util.concurrent.atomic.AtomicReference<>();
            androidx.lifecycle.LiveData<java.util.List<com.example.spendtracker.data.local.dao.TransactionDao.AccountSummary>> data = db.transactionDao().getUniqueAccounts();
            androidx.lifecycle.Observer<java.util.List<com.example.spendtracker.data.local.dao.TransactionDao.AccountSummary>> observer = rows -> { result.set(rows); emitted.countDown(); };
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> data.observeForever(observer));
            try {
                assertTrue(emitted.await(5, java.util.concurrent.TimeUnit.SECONDS));
                assertEquals(1, result.get().size()); assertEquals(3, result.get().get(0).categoryCount);
                assertTrue(result.get().get(0).categoryNames.contains("Transfer"));
            } finally { androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> data.removeObserver(observer)); }
        } finally { db.close(); }
    }

    @Test public void migrationPreservesHistoryWithoutInventingPredictions() {
        Context context = ApplicationProvider.getApplicationContext();
        String name = "prediction-migration-test.db";
        context.deleteDatabase(name);
        SpendTrackerDatabase old = Room.databaseBuilder(context, SpendTrackerDatabase.class, name).addCallback(com.example.spendtracker.data.local.database.AnalyticsMaterialization.CALLBACK).allowMainThreadQueries().build();
        TransactionEntity tx = new TransactionEntity(); tx.amount = 50; tx.category = "Food";
        int id = (int) old.transactionDao().insertTransaction(tx);
        old.getOpenHelper().getWritableDatabase().execSQL("ALTER TABLE transactions DROP COLUMN predictedCategory");
        old.getOpenHelper().getWritableDatabase().execSQL("ALTER TABLE transactions DROP COLUMN predictionFinalCategory");
        old.getOpenHelper().getWritableDatabase().execSQL("ALTER TABLE transactions DROP COLUMN bookmarked");
        old.close();
        try (android.database.sqlite.SQLiteDatabase raw = android.database.sqlite.SQLiteDatabase.openDatabase(
                context.getDatabasePath(name).getPath(), null, android.database.sqlite.SQLiteDatabase.OPEN_READWRITE)) { raw.setVersion(16); }
        SpendTrackerDatabase upgraded = Room.databaseBuilder(context, SpendTrackerDatabase.class, name).addCallback(com.example.spendtracker.data.local.database.AnalyticsMaterialization.CALLBACK)
                .allowMainThreadQueries().addMigrations(DatabaseModule.MIGRATION_16_17, DatabaseModule.MIGRATION_17_18, DatabaseModule.MIGRATION_18_19).build();
        try {
            TransactionEntity saved = upgraded.transactionDao().getTransactionByIdSync(id);
            assertEquals(50, saved.amount, 0); assertNull(saved.predictedCategory); assertNull(saved.predictionFinalCategory);
            saved.predictedCategory = "Food"; saved.predictionFinalCategory = "Travel";
            upgraded.transactionDao().updateTransaction(saved);
            TransactionEntity read = upgraded.transactionDao().getTransactionByIdSync(id);
            assertEquals("Food", read.predictedCategory); assertEquals("Travel", read.predictionFinalCategory);
        } finally { upgraded.close(); context.deleteDatabase(name); }
    }
}
