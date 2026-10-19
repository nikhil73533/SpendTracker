package com.example.spendtracker.ui.dashboard;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.MutableLiveData;

import com.example.spendtracker.domain.model.Summary;
import com.example.spendtracker.domain.repository.TransactionRepository;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.concurrent.ExecutorService;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

public class TransferCalculationTest {

    @Rule
    public InstantTaskExecutorRule instantExecutorRule = new InstantTaskExecutorRule();

    @Mock private TransactionRepository repository;
    @Mock private android.content.Context context;
    
    private DashboardViewModel viewModel;

    @Before
    public void setup() {
        MockitoAnnotations.openMocks(this);
        when(context.getApplicationContext()).thenReturn(context);
        
        MutableLiveData<DashboardViewModel.DateRange> dateRangeLive = new MutableLiveData<>(new DashboardViewModel.DateRange(0, 1000, "Test"));
        
        when(repository.getSummary(anyLong(), anyLong())).thenReturn(new MutableLiveData<>(
            new Summary(5000, 2000, 1000, null, null, null, null)
        ));
        
        MutableLiveData<Double> cardExpLive = new MutableLiveData<>(500.0);
        MutableLiveData<Double> acctExpLive = new MutableLiveData<>(1500.0);
        MutableLiveData<Double> transferLive = new MutableLiveData<>(500.0); // old gross transfer
        MutableLiveData<Double> transferInLive = new MutableLiveData<>(800.0);
        MutableLiveData<Double> transferOutLive = new MutableLiveData<>(300.0);

        when(repository.getTotalCardExpense(anyLong(), anyLong())).thenReturn(cardExpLive);
        when(repository.getTotalAccountExpense(anyLong(), anyLong())).thenReturn(acctExpLive);
        when(repository.getTotalTransfer(anyLong(), anyLong())).thenReturn(transferLive);
        when(repository.getTotalTransferIncoming(anyLong(), anyLong())).thenReturn(transferInLive);
        when(repository.getTotalTransferOutgoing(anyLong(), anyLong())).thenReturn(transferOutLive);
        
        viewModel = new DashboardViewModel(repository, null, context);
        // Force the date range to trigger getTotalPageData logic
        viewModel.setFilter(DashboardViewModel.FilterType.MONTHLY); 
    }

    @Test
    public void testNetTransferCalculation() {
        viewModel.getTotalPageData().observeForever(data -> {
            assertNotNull(data);
            // Net Transfer = Incoming (800) - Outgoing (300) = 500
            assertEquals(500.0, data.transfers, 0.01);
            assertEquals(800.0, data.transferIncoming, 0.01);
            assertEquals(300.0, data.transferOutgoing, 0.01);
        });
    }
}
