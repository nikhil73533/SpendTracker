package com.example.spendtracker.ui.dashboard;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.MutableLiveData;

import com.example.spendtracker.domain.model.Transaction;
import com.example.spendtracker.domain.repository.TransactionRepository;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class SuspiciousTransactionTest {

    @Rule
    public InstantTaskExecutorRule instantExecutorRule = new InstantTaskExecutorRule();

    @Mock private TransactionRepository repository;
    @Mock private android.content.Context context;
    
    private DashboardViewModel viewModel;
    private MutableLiveData<List<Transaction>> transactionsLive;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        when(context.getApplicationContext()).thenReturn(context);
        transactionsLive = new MutableLiveData<>();
        when(repository.getTransactions()).thenReturn(transactionsLive);
        
        viewModel = new DashboardViewModel(repository, null, context);
    }

    @Test
    public void testGetSuspiciousTransactions_filtersByThreshold() {
        Transaction t1 = new Transaction();
        t1.setConfidenceScore(0.9); // Not suspicious
        
        Transaction t2 = new Transaction();
        t2.setConfidenceScore(0.5); // Suspicious (below 0.6)
        
        Transaction t3 = new Transaction();
        t3.setConfidenceScore(0.4); // Suspicious
        
        Transaction t4 = new Transaction();
        t4.setConfidenceScore(1.0); // Not suspicious

        transactionsLive.setValue(Arrays.asList(t1, t2, t3, t4));
        
        androidx.lifecycle.LiveData<List<Transaction>> flagged = viewModel.getSuspiciousTransactions();
        androidx.lifecycle.Observer<List<Transaction>> observer = ignored -> {};
        flagged.observeForever(observer);
        assertEquals(2, flagged.getValue().size());
        viewModel.setSuspiciousThreshold(0.45);
        assertEquals(1, flagged.getValue().size());
        t3.setConfidenceScore(1.0);
        Transaction unknown = new Transaction(); unknown.setConfidenceScore(0);
        transactionsLive.setValue(Arrays.asList(t1, t2, t3, t4, unknown));
        assertEquals(Arrays.asList(unknown), flagged.getValue());
        flagged.removeObserver(observer);
    }
}
