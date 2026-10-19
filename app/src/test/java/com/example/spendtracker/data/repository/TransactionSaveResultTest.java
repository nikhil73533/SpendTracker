package com.example.spendtracker.data.repository;
import android.content.Context;
import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.MutableLiveData;
import com.example.spendtracker.billing.*;
import com.example.spendtracker.data.local.dao.*;
import com.example.spendtracker.data.local.database.SpendTrackerDatabase;
import com.example.spendtracker.domain.model.Transaction;
import org.junit.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
public class TransactionSaveResultTest {
    @Rule public InstantTaskExecutorRule main=new InstantTaskExecutorRule();
    private TransactionDao dao, clone;
    private PremiumRepository premium;
    private TransactionRepositoryImpl repository;
    @Before public void setup() {
        dao=mock(TransactionDao.class); clone=mock(TransactionDao.class); premium=mock(PremiumRepository.class);
        when(dao.getAllTransactions()).thenReturn(new MutableLiveData<>(Collections.emptyList()));
        when(dao.insertTransactionsIgnore(anyList())).thenReturn(Collections.singletonList(42L));
        when(premium.hasAccess(any())).thenReturn(true);
    }
    private String save() throws Exception {
        repository=new TransactionRepositoryImpl(mock(Context.class),dao,clone,mock(CategoryDao.class),
                mock(TransactionGroupDao.class),mock(RepeatedAlertDao.class),premium,mock(SpendTrackerDatabase.class));
        Transaction transaction=new Transaction(); transaction.setAmount(50); transaction.setType("EXPENSE"); transaction.setCategory("Other");
        transaction.setSourceTransactionId("sms:test");
        CountDownLatch done=new CountDownLatch(1); AtomicReference<String> error=new AtomicReference<>();
        repository.saveTransaction(transaction,false,value->{error.set(value);done.countDown();});
        assertTrue(done.await(3,TimeUnit.SECONDS)); return error.get();
    }
    @Test public void primaryFailureIsReturnedInsteadOfCrashing() throws Exception {
        when(dao.insertTransactionsIgnore(anyList())).thenThrow(new IllegalStateException("disk full"));
        assertNotNull(save());
    }
    @Test public void cloneFailureDoesNotInvalidateCommittedTransaction() throws Exception {
        when(clone.insertTransaction(any())).thenThrow(new IllegalStateException("clone damaged"));
        assertNull(save()); verify(dao,timeout(1000)).insertTransactionsIgnore(anyList());
    }
    @Test public void duplicateRetryDoesNotReplaceExistingRowEvenAtQuota() throws Exception {
        when(dao.countBySourceId("sms:test")).thenReturn(1);
        when(premium.hasAccess(any())).thenReturn(false); when(dao.getLifetimeTransactionCountSync()).thenReturn(200);
        assertNull(save()); verify(dao,never()).insertTransactionsIgnore(anyList());
    }
    @Test public void quotaReportsErrorWithoutBackgroundToast() throws Exception {
        when(premium.hasAccess(any())).thenReturn(false); when(dao.getLifetimeTransactionCountSync()).thenReturn(200);
        assertTrue(save().contains("Free plan limit")); verify(dao,never()).insertTransactionsIgnore(anyList());
    }
}
