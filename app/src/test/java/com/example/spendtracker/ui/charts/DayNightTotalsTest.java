package com.example.spendtracker.ui.charts;

import com.example.spendtracker.domain.model.Transaction;
import java.util.Arrays;
import java.util.Calendar;
import java.util.TimeZone;
import org.junit.Test;
import static org.junit.Assert.*;

public class DayNightTotalsTest {
    private Transaction transaction(int hour, int minute, double amount) {
        Calendar date = Calendar.getInstance();
        date.clear();
        date.set(2026, Calendar.SEPTEMBER, 28, hour, minute);
        Transaction transaction = new Transaction();
        transaction.setDate(date.getTimeInMillis());
        transaction.setAmount(amount);
        transaction.setType("EXPENSE");
        return transaction;
    }

    @Test public void usesLocalTimeAndIncludesSixAmButExcludesSixPm() {
        TimeZone original = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"));
            DayNightTotals totals = DayNightTotals.from(Arrays.asList(
                    transaction(5, 59, 1), transaction(6, 0, 10),
                    transaction(17, 59, 100), transaction(18, 0, 1000),
                    transaction(0, 0, 10000)), "EXPENSE");
            assertEquals(110, totals.day, 0);
            assertEquals(11001, totals.night, 0);
        } finally { TimeZone.setDefault(original); }
    }

    @Test public void excludesInactiveTransfersAndInvalidAmountsAndRespectsSelectedType() {
        Transaction deleted = transaction(12, 0, 100); deleted.setStatus("DELETED");
        Transaction transfer = transaction(12, 0, 100); transfer.setCategory("Bank Transfer");
        Transaction income = transaction(12, 0, 500); income.setType("INCOME");
        java.util.List<Transaction> rows = Arrays.asList(deleted, transfer, income,
                transaction(12, 0, Double.NaN), transaction(12, 0, Double.POSITIVE_INFINITY),
                transaction(20, 0, -25));
        DayNightTotals expenses = DayNightTotals.from(rows, "EXPENSE");
        assertEquals(0, expenses.day, 0);
        assertEquals(25, expenses.night, 0);
        DayNightTotals incomes = DayNightTotals.from(rows, "INCOME");
        assertEquals(500, incomes.day, 0);
        assertEquals(0, incomes.night, 0);
    }

    @Test public void emptyDataKeepsBothComparisonBucketsAtZero() {
        DayNightTotals totals = DayNightTotals.from(null, "EXPENSE");
        assertEquals(0, totals.day, 0);
        assertEquals(0, totals.night, 0);
    }
}
