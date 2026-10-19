package com.example.spendtracker.ui.dashboard;

import com.example.spendtracker.domain.model.Transaction;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class DashboardChartSeriesTest {
    private Transaction transaction(String type, double amount, int year, int month, int date) {
        Calendar day = Calendar.getInstance();
        day.clear();
        day.set(year, month - 1, date, 12, 0);
        Transaction transaction = new Transaction();
        transaction.setType(type);
        transaction.setAmount(amount);
        transaction.setDate(day.getTimeInMillis());
        return transaction;
    }

    @Test public void activitySortsDatesFillsGapsAndIncludesAllMovement() {
        Map<Long, Double> actual = DashboardChartSeries.activity(Arrays.asList(
                transaction("EXPENSE", -20, 2026, 9, 25),
                transaction("INCOME", 100, 2026, 9, 23),
                transaction("TRANSFER", 40, 2026, 9, 25)));
        assertEquals(Arrays.asList(100d, 0d, 60d), new ArrayList<>(actual.values()));
    }

    @Test public void spendingExcludesIncomeTransfersOutsideWeekAndFutureDays() {
        long now = transaction("EXPENSE", 0, 2026, 9, 25).getDate();
        Map<Long, Double> actual = DashboardChartSeries.spendingWeek(Arrays.asList(
                transaction("EXPENSE", 10, 2026, 9, 19),
                transaction("EXPENSE", 20, 2026, 9, 25),
                transaction("EXPENSE", 100, 2026, 9, 18),
                transaction("EXPENSE", 200, 2026, 9, 26),
                transaction("INCOME", 300, 2026, 9, 25),
                transaction("TRANSFER", 400, 2026, 9, 25)), now);
        assertEquals(Arrays.asList(10d, 0d, 0d, 0d, 0d, 0d, 20d), new ArrayList<>(actual.values()));
    }

    @Test public void spendingUsesCalendarDaysAcrossDaylightSaving() {
        TimeZone original = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"));
            long now = transaction("EXPENSE", 0, 2026, 3, 10).getDate();
            Map<Long, Double> actual = DashboardChartSeries.spendingWeek(Arrays.asList(
                    transaction("EXPENSE", 5, 2026, 3, 7),
                    transaction("EXPENSE", 9, 2026, 3, 8)), now);
            assertEquals(Arrays.asList(0d, 0d, 0d, 5d, 9d, 0d, 0d), new ArrayList<>(actual.values()));
        } finally { TimeZone.setDefault(original); }
    }

    @Test public void emptyAndInvalidAmountsDoNotProduceInvalidChartValues() {
        assertTrue(DashboardChartSeries.activity(null).isEmpty());
        assertTrue(DashboardChartSeries.activity(Collections.singletonList(
                transaction("EXPENSE", Double.NaN, 2026, 9, 25))).isEmpty());
        assertEquals(7, DashboardChartSeries.spendingWeek(null, System.currentTimeMillis()).size());
    }
}
