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
public class BookmarkMigrationTest {
    @Test public void version18UpgradePreservesTransactionsAndBookmarksPersistAcrossReopen() {
        Context context=ApplicationProvider.getApplicationContext(); String name="bookmark-migration-test.db";
        context.deleteDatabase(name);
        try {
            SpendTrackerDatabase seed=Room.databaseBuilder(context,SpendTrackerDatabase.class,name).allowMainThreadQueries().build();
            int id;
            try {
                TransactionEntity row=new TransactionEntity(); row.amount=10; row.type="INCOME"; row.sender="CEL EBA";
                id=(int)seed.transactionDao().insertTransaction(row);
                seed.getOpenHelper().getWritableDatabase().execSQL("ALTER TABLE transactions DROP COLUMN bookmarked");
            } finally { seed.close(); }
            try(android.database.sqlite.SQLiteDatabase raw=android.database.sqlite.SQLiteDatabase.openDatabase(context.getDatabasePath(name).getPath(),null,android.database.sqlite.SQLiteDatabase.OPEN_READWRITE)) { raw.setVersion(18); }
            SpendTrackerDatabase upgraded=Room.databaseBuilder(context,SpendTrackerDatabase.class,name).addMigrations(DatabaseModule.MIGRATION_18_19).allowMainThreadQueries().build();
            try {
                TransactionEntity saved=upgraded.transactionDao().getTransactionByIdSync(id);
                assertEquals(10,saved.amount,0); assertEquals("CEL EBA",saved.sender); assertFalse(saved.bookmarked);
                upgraded.transactionDao().setBookmarked(id,true);
            } finally { upgraded.close(); }
            SpendTrackerDatabase reopened=Room.databaseBuilder(context,SpendTrackerDatabase.class,name).allowMainThreadQueries().build();
            try { assertTrue(reopened.transactionDao().getTransactionByIdSync(id).bookmarked); }
            finally { reopened.close(); }
        } finally { context.deleteDatabase(name); }
    }
}
