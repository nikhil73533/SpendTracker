package com.example.spendtracker.data.local.database;

import android.content.Context;
import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.example.spendtracker.data.local.dao.TransactionDao;
import com.example.spendtracker.data.local.entity.TransactionEntity;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.util.List;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import com.example.spendtracker.util.LiveDataTestUtil;

@RunWith(AndroidJUnit4.class)
public class SoftDeleteIntegrationTest {

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    private SpendTrackerDatabase db;
    private TransactionDao transactionDao;

    @Before
    public void createDb() {
        Context context = ApplicationProvider.getApplicationContext();
        db = Room.inMemoryDatabaseBuilder(context, SpendTrackerDatabase.class)
                .allowMainThreadQueries()
                .build();
        transactionDao = db.transactionDao();
    }

    @After
    public void closeDb() {
        db.close();
    }

    @Test
    public void testSoftDeleteAndRestore() throws InterruptedException {
        // Insert a transaction
        TransactionEntity t1 = new TransactionEntity(0, 100, "Food", "Lunch", "EXPENSE", 1000L, "Card", null, null, null, null, null, null, null, 0);
        t1.status = "ACTIVE";
        long id = transactionDao.insertTransaction(t1);

        // Verify it appears in active queries
        List<TransactionEntity> active = transactionDao.getTransactionsSync();
        assertEquals(1, active.size());

        // Soft delete it
        long deletedTime = System.currentTimeMillis();
        transactionDao.softDeleteTransaction((int) id, deletedTime);

        // Verify it no longer appears in active queries
        List<TransactionEntity> activeAfterDelete = transactionDao.getTransactionsSync();
        assertEquals(0, activeAfterDelete.size());

        // Verify it appears in deleted transactions
        List<TransactionEntity> deleted = LiveDataTestUtil.getOrAwaitValue(transactionDao.getDeletedTransactions());
        assertEquals(1, deleted.size());
        assertEquals("DELETED", deleted.get(0).status);
        assertEquals(deletedTime, deleted.get(0).deletedAt);

        // Restore it
        transactionDao.restoreTransaction((int) id);

        // Verify it is active again
        List<TransactionEntity> activeAfterRestore = transactionDao.getTransactionsSync();
        assertEquals(1, activeAfterRestore.size());
        assertEquals("ACTIVE", activeAfterRestore.get(0).status);
        assertEquals(0, activeAfterRestore.get(0).deletedAt);
    }
}
