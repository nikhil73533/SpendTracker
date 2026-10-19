package com.example.spendtracker.ui.dashboard;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.MutableLiveData;
import com.example.spendtracker.domain.model.Transaction;
import com.example.spendtracker.domain.repository.TransactionRepository;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class YearNavigationTest {
    @Rule public InstantTaskExecutorRule executor = new InstantTaskExecutorRule();

    private DashboardViewModel model(TransactionRepository repository) {
        android.content.Context context = mock(android.content.Context.class);
        when(context.getApplicationContext()).thenReturn(context);
        DashboardViewModel vm = new DashboardViewModel(repository, null, context);
        vm.setCalendarFilter(date(2024, Calendar.SEPTEMBER, 15), "September");
        vm.clearCalendarDaySelected();
        return vm;
    }

    @Test public void yearNavigationCoversLeapYearAndRestoresMonth() {
        DashboardViewModel vm = model(mock(TransactionRepository.class));
        vm.setFilter(DashboardViewModel.FilterType.YEARLY);
        assertEquals(date(2024, Calendar.JANUARY, 1), vm.getDateRange().getValue().start);
        assertEquals(date(2025, Calendar.JANUARY, 1) - 1, vm.getDateRange().getValue().end);
        vm.moveNext();
        assertEquals("2025", vm.getDateRange().getValue().label);
        vm.movePrev();
        vm.setFilter(DashboardViewModel.FilterType.YEARLY);
        vm.setFilter(DashboardViewModel.FilterType.TRANSACTION_GROUP);
        vm.setFilter(DashboardViewModel.FilterType.DAILY);
        assertEquals(date(2024, Calendar.SEPTEMBER, 1), vm.getDateRange().getValue().start);
        vm.moveNext();
        assertEquals(date(2024, Calendar.OCTOBER, 1), vm.getDateRange().getValue().start);
    }

    @Test public void monthlyRowsFollowSelectedYearAndAreNewestFirst() {
        TransactionRepository repo = mock(TransactionRepository.class);
        List<Transaction> transactions = Arrays.asList(
                transaction(2023, Calendar.DECEMBER), transaction(2024, Calendar.JANUARY),
                transaction(2024, Calendar.DECEMBER), transaction(2025, Calendar.JANUARY));
        when(repo.getTransactions()).thenReturn(new MutableLiveData<>(transactions));
        DashboardViewModel vm = model(repo);
        vm.setFilter(DashboardViewModel.FilterType.YEARLY);
        androidx.lifecycle.LiveData<List<MonthlySummaryAdapter.MonthSummary>> rows = vm.getMonthlySummaries();
        androidx.lifecycle.Observer<List<MonthlySummaryAdapter.MonthSummary>> observer = value -> {};
        rows.observeForever(observer);
        try {
            assertEquals(2, rows.getValue().size());
            assertEquals(date(2024, Calendar.DECEMBER, 1), rows.getValue().get(0).monthTimestamp);
            vm.moveNext();
            assertEquals(1, rows.getValue().size());
            assertEquals(date(2025, Calendar.JANUARY, 1), rows.getValue().get(0).monthTimestamp);
            vm.moveNext();
            assertTrue(rows.getValue().isEmpty());
        } finally { rows.removeObserver(observer); }
    }

    private Transaction transaction(int year, int month) {
        Transaction t = mock(Transaction.class);
        when(t.getDate()).thenReturn(date(year, month, 1));
        when(t.getType()).thenReturn("EXPENSE");
        when(t.getAmount()).thenReturn(100.0);
        return t;
    }

    private static long date(int year, int month, int day) {
        Calendar cal = Calendar.getInstance();
        cal.clear();
        cal.set(year, month, day);
        return cal.getTimeInMillis();
    }
}
