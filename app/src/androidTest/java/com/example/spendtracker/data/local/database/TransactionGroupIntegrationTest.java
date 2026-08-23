package com.example.spendtracker.data.local.database;

import android.content.Context;
import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.example.spendtracker.data.local.dao.TransactionDao;
import com.example.spendtracker.data.local.dao.TransactionGroupDao;
import com.example.spendtracker.data.local.entity.TransactionEntity;
import com.example.spendtracker.data.local.entity.TransactionGroupCategoryEntity;
import com.example.spendtracker.data.local.entity.TransactionGroupEntity;
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

@RunWith(AndroidJUnit4.class)
public class TransactionGroupIntegrationTest {

    @Rule
    public InstantTaskExecutorRule instantTaskExecutorRule = new InstantTaskExecutorRule();

    private SpendTrackerDatabase db;
    private TransactionGroupDao groupDao;
    private TransactionDao transactionDao;

    @Before
    public void createDb() {
        Context context = ApplicationProvider.getApplicationContext();
        db = Room.inMemoryDatabaseBuilder(context, SpendTrackerDatabase.class)
                .allowMainThreadQueries()
                .build();
        groupDao = db.transactionGroupDao();
        transactionDao = db.transactionDao();
    }

    @After
    public void closeDb() {
        db.close();
    }

    @Test
    public void testCreateGroupAndAssociateTransactions() throws InterruptedException {
        // 1. Insert some transactions
        TransactionEntity t1 = new TransactionEntity(0, 100, "Food", "Lunch", "EXPENSE", 1000L, "Card", null, null, null, null, null, null, null, 0);
        TransactionEntity t2 = new TransactionEntity(0, 200, "Travel", "Cab", "EXPENSE", 2000L, "Cash", null, null, null, null, null, null, null, 0);
        TransactionEntity t3 = new TransactionEntity(0, 300, "Food", "Dinner", "EXPENSE", 3000L, "Card", null, null, null, null, null, null, null, 0);
        
        // Need to set status for t1, t2, t3 manually if the constructor doesn't set it to ACTIVE initially. 
        // Our constructor does set it to ACTIVE when domain maps to entity, but direct Entity instantiation defaults might vary.
        t1.status = "ACTIVE";
        t2.status = "ACTIVE";
        t3.status = "ACTIVE";

        long t1Id = transactionDao.insertTransaction(t1);
        long t2Id = transactionDao.insertTransaction(t2);
        long t3Id = transactionDao.insertTransaction(t3);

        // 2. Create a Transaction Group for "Food" between time 500 and 2500
        TransactionGroupEntity group = new TransactionGroupEntity(0, "Trip", 500L, 2500L);
        long groupId = groupDao.insertGroup(group);
        
        groupDao.insertGroupCategory(new TransactionGroupCategoryEntity((int) groupId, "Food"));

        // 3. Trigger association
        groupDao.associateTransactionsWithGroup((int) groupId, 500L, 2500L);

        // 4. Verify associations
        TransactionEntity updatedT1 = transactionDao.getTransactionByIdSync((int) t1Id);
        TransactionEntity updatedT2 = transactionDao.getTransactionByIdSync((int) t2Id);
        TransactionEntity updatedT3 = transactionDao.getTransactionByIdSync((int) t3Id);

        // t1 matches category "Food" and time (1000 is between 500 and 2500)
        assertEquals((int) groupId, updatedT1.transactionGroupId);
        
        // t2 fails category ("Travel" vs "Food")
        assertEquals(0, updatedT2.transactionGroupId);
        
        // t3 fails time (3000 is outside 500-2500)
        assertEquals(0, updatedT3.transactionGroupId);
    }
}
