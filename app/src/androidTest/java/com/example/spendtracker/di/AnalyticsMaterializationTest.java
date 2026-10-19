package com.example.spendtracker.di;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.example.spendtracker.data.local.database.AnalyticsMaterialization;
import com.example.spendtracker.data.local.database.SpendTrackerDatabase;
import com.example.spendtracker.data.local.entity.TransactionEntity;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class AnalyticsMaterializationTest {
    @Test public void granularCashFlowExcludesTransfersDeletedRowsAndEndBoundary() {
        SpendTrackerDatabase db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), SpendTrackerDatabase.class)
                .addCallback(AnalyticsMaterialization.CALLBACK).allowMainThreadQueries().build();
        try {
            for (int i = 0; i < 6; i++) {
                TransactionEntity row = new TransactionEntity();
                row.date = i == 5 ? 200 : 150;
                row.amount = i == 0 ? 1000 : i == 1 ? 200 : 8000;
                row.type = i == 0 ? "INCOME" : i == 2 ? "TRANSFER" : "EXPENSE";
                row.category = i == 3 ? "Account Transfer" : "Food";
                row.status = i == 4 ? "DELETED" : "ACTIVE";
                db.transactionDao().insertTransaction(row);
            }
            com.example.spendtracker.domain.service.AnalyticsService service =
                    new com.example.spendtracker.domain.service.AnalyticsService(db.transactionDao());
            for (com.example.spendtracker.domain.model.analytics.AnalyticsGranularity granularity
                    : com.example.spendtracker.domain.model.analytics.AnalyticsGranularity.values()) {
                com.example.spendtracker.domain.model.analytics.TimeSeriesPoint point =
                        service.getIncomeExpenseTrend(100, 200, granularity).get(0);
                assertEquals(1000, point.getValue(), .001);
                assertEquals(200, point.getSecondaryValue(), .001);
                assertEquals(80, service.getSavingsAnalysis(100, 200, granularity).savingsRate(), .001);
            }
        } finally { db.close(); }
    }
    @Test public void summariesFollowReplaceReviewDeleteAndRestore() {
        SpendTrackerDatabase db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), SpendTrackerDatabase.class)
                .addCallback(AnalyticsMaterialization.CALLBACK).allowMainThreadQueries().build();
        try {
            TransactionEntity first = new TransactionEntity();
            first.date = 86401000L; first.amount = 20; first.category = "Food"; first.receiverName = "Cafe";
            first.id = (int) db.transactionDao().insertTransaction(first);
            TransactionEntity second = new TransactionEntity();
            second.date = 86402000L; second.amount = 30; second.category = "Food"; second.receiverName = "Cafe";
            second.id = (int) db.transactionDao().insertTransaction(second);
            assertEquals(50, db.transactionDao().getTotalExpenseSync(0, Long.MAX_VALUE), .001);
            first.amount = 12;
            db.transactionDao().insertTransaction(first); // REPLACE, preserving the second contribution
            assertEquals(42, db.transactionDao().getTotalExpenseSync(0, Long.MAX_VALUE), .001);
            first.category = "Transfer";
            db.transactionDao().updateTransaction(first);
            assertEquals(30, db.transactionDao().getTotalExpenseSync(0, Long.MAX_VALUE), .001);
            assertEquals(12, db.transactionDao().getTotalTransferAmountSync(0, Long.MAX_VALUE), .001);
            db.transactionDao().softDeleteTransaction(second.id, 1);
            assertEquals(0, db.transactionDao().getTotalExpenseSync(0, Long.MAX_VALUE), .001);
            db.transactionDao().restoreTransaction(second.id);
            assertEquals(30, db.transactionDao().getTotalExpenseSync(0, Long.MAX_VALUE), .001);
            assertEquals(30, db.transactionDao().getTotalExpenseSync(86402000L, 86402000L), .001);
            assertEquals(0, db.transactionDao().getTotalExpenseSync(86402001L, 172799999L), .001);
        } finally { db.close(); }
    }

    @Test public void migrationBackfillsExistingTransactionsWithoutChangingThem() {
        android.content.Context context = ApplicationProvider.getApplicationContext();
        String name = "analytics-materialization-migration-test.db";
        context.deleteDatabase(name);
        try {
            SpendTrackerDatabase original = Room.databaseBuilder(context, SpendTrackerDatabase.class, name)
                    .allowMainThreadQueries().build();
            try {
                TransactionEntity row = new TransactionEntity(); row.date = 86401000; row.amount = 45; row.category = "Food";
                original.transactionDao().insertTransaction(row);
                original.getOpenHelper().getWritableDatabase().execSQL("ALTER TABLE transactions DROP COLUMN bookmarked");
                for (String kind : new String[]{"type", "category", "merchant"})
                    original.getOpenHelper().getWritableDatabase().execSQL("DROP TABLE analytics_daily_" + kind);
            } finally { original.close(); }
            try (android.database.sqlite.SQLiteDatabase raw = android.database.sqlite.SQLiteDatabase.openDatabase(
                    context.getDatabasePath(name).getPath(), null, android.database.sqlite.SQLiteDatabase.OPEN_READWRITE)) { raw.setVersion(17); }
            SpendTrackerDatabase migrated = Room.databaseBuilder(context, SpendTrackerDatabase.class, name)
                    .addMigrations(DatabaseModule.MIGRATION_17_18, DatabaseModule.MIGRATION_18_19).addCallback(AnalyticsMaterialization.CALLBACK)
                    .allowMainThreadQueries().build();
            try {
                assertEquals(45, migrated.transactionDao().getTotalExpenseSync(0, Long.MAX_VALUE), .001);
                assertEquals(1, migrated.transactionDao().getAllTransactionsSync().size());
                assertEquals(1, migrated.transactionDao().getExpenseCountSync(0, Long.MAX_VALUE));
            } finally { migrated.close(); }
        } finally { context.deleteDatabase(name); }
    }
}
