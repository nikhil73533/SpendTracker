package com.example.spendtracker.ui.charts;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.Transformations;
import com.example.spendtracker.domain.model.Transaction;
import com.example.spendtracker.domain.repository.SecurityRepository;
import com.example.spendtracker.domain.repository.TransactionRepository;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

public class ChartsViewModelComparisonTest {
    @Rule public InstantTaskExecutorRule executor = new InstantTaskExecutorRule();

    @Test public void comparisonUpdatesForTypeDateAndLiveTransactionChanges() {
        TransactionRepository repository = mock(TransactionRepository.class);
        MutableLiveData<List<Transaction>> rows = new MutableLiveData<>(Collections.emptyList());
        when(repository.getTransactions()).thenReturn(rows);
        when(repository.getTransactionsInRange(anyLong(), anyLong())).thenReturn(rows);
        ChartsViewModel model = new ChartsViewModel(repository, mock(SecurityRepository.class));
        LiveData<DayNightTotals> comparison = model.getDayNightTotals();
        Observer<DayNightTotals> observer = ignored -> {};
        comparison.observeForever(observer);
        try {
            Transaction expense = new Transaction();
            Calendar day = Calendar.getInstance(); day.set(Calendar.HOUR_OF_DAY, 12);
            expense.setDate(day.getTimeInMillis()); expense.setType("EXPENSE"); expense.setAmount(50);
            Transaction income = new Transaction();
            income.setDate(day.getTimeInMillis()); income.setType("INCOME"); income.setAmount(200);
            rows.setValue(Arrays.asList(expense, income));
            assertEquals(50, comparison.getValue().day, 0);
            model.setTransactionType("INCOME");
            assertEquals(200, comparison.getValue().day, 0);
            clearInvocations(repository);
            model.movePrev();
            org.mockito.ArgumentCaptor<Long> start = org.mockito.ArgumentCaptor.forClass(Long.class);
            org.mockito.ArgumentCaptor<Long> end = org.mockito.ArgumentCaptor.forClass(Long.class);
            verify(repository, atLeastOnce()).getTransactionsInRange(start.capture(), end.capture());
            assertEquals(model.getCurrentMonthStart().getValue(), start.getValue());
            Calendar expectedEnd = Calendar.getInstance();
            expectedEnd.setTimeInMillis(start.getValue());
            expectedEnd.set(Calendar.DAY_OF_MONTH, expectedEnd.getActualMaximum(Calendar.DAY_OF_MONTH));
            expectedEnd.set(Calendar.HOUR_OF_DAY, 23);
            expectedEnd.set(Calendar.MINUTE, 59);
            expectedEnd.set(Calendar.SECOND, 59);
            assertEquals(expectedEnd.getTimeInMillis(), end.getValue().longValue());
            clearInvocations(repository);
            model.setGranularity(ChartsViewModel.Granularity.WEEKLY);
            verify(repository, atLeastOnce()).getTransactionsInRange(start.capture(), end.capture());
            expectedEnd.setTimeInMillis(start.getValue());
            expectedEnd.add(Calendar.DAY_OF_YEAR, 6);
            expectedEnd.set(Calendar.HOUR_OF_DAY, 23);
            expectedEnd.set(Calendar.MINUTE, 59);
            expectedEnd.set(Calendar.SECOND, 59);
            assertEquals(expectedEnd.getTimeInMillis(), end.getValue().longValue());
            rows.setValue(Collections.emptyList());
            assertEquals(0, comparison.getValue().day, 0);
            assertSame(comparison, model.getDayNightTotals());
        } finally { comparison.removeObserver(observer); }
    }

    @Test public void sevenDayChartRetainsItsObservedStreamForFirstLoadAndRefresh() {
        TransactionRepository repository = mock(TransactionRepository.class);
        MutableLiveData<List<Transaction>> rows = new MutableLiveData<>();
        when(repository.getTransactions()).thenAnswer(ignored -> Transformations.map(rows, value -> value));
        ChartsViewModel model = new ChartsViewModel(repository, mock(SecurityRepository.class));
        LiveData<List<Transaction>> all = model.getAllTransactions();
        Observer<List<Transaction>> observer = ignored -> {};
        all.observeForever(observer);
        try {
            rows.setValue(Collections.singletonList(new Transaction()));
            assertSame(all, model.getAllTransactions());
            assertEquals(1, model.getAllTransactions().getValue().size());
            rows.setValue(Collections.emptyList());
            assertTrue(model.getAllTransactions().getValue().isEmpty());
        } finally { all.removeObserver(observer); }
    }
}
