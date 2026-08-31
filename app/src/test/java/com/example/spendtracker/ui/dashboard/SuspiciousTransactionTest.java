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
        
        // Use default threshold (0.6)
        viewModel.getSuspiciousTransactions().observeForever(list -> {
            assertEquals(2, list.size());
        });
        
        // Change threshold to 0.45
        viewModel.setSuspiciousThreshold(0.45);
        viewModel.getSuspiciousTransactions().observeForever(list -> {
            assertEquals(1, list.size()); // Only t3 should match
        });
    }
}
