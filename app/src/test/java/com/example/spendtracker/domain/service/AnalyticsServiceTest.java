package com.example.spendtracker.domain.service;

import com.example.spendtracker.data.local.dao.TransactionDao;
import com.example.spendtracker.domain.model.analytics.AnalyticsGranularity;
import com.example.spendtracker.domain.model.analytics.CategoryAnalytics;
import com.example.spendtracker.domain.model.analytics.TimeSeriesPoint;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class AnalyticsServiceTest {
    @Test
    public void categoryFrequencyUsesCountsRatherThanAmountOrdering() {
        TransactionDao dao = mock(TransactionDao.class);
        TransactionDao.AnalyticsBucket health = new TransactionDao.AnalyticsBucket();
        health.label = "Health";
        health.transactionCount = 7;
        health.totalAmount = 650.0;
        when(dao.getExpenseCategoryFrequencySync(100L, 200L)).thenReturn(Arrays.asList(health));

        List<CategoryAnalytics> result = new AnalyticsService(dao).getExpenseCategoryAnalytics(100L, 200L);

        assertEquals(1, result.size());
        assertEquals("Health", result.get(0).getCategoryName());
        assertEquals(7, result.get(0).getTransactionCount());
        assertEquals(650.0, result.get(0).getTotalAmount(), 0.001);
    }

    @Test
    public void transactionVolumeUsesSelectedGranularityAndReturnsCounts() {
        TransactionDao dao = mock(TransactionDao.class);
        TransactionDao.AnalyticsBucket day = new TransactionDao.AnalyticsBucket();
        day.label = "2024-01-05";
        day.transactionCount = 4;
        day.totalAmount = 1200.0;
        day.expenseAmount = 800.0;
        when(dao.getDailyAnalyticsBucketsSync(100L, 200L)).thenReturn(Arrays.asList(day));

        List<TimeSeriesPoint> result = new AnalyticsService(dao)
                .getTransactionFrequency(100L, 200L, AnalyticsGranularity.DAY);

        assertEquals(4.0, result.get(0).getValue(), 0.001);
        assertEquals(1200.0, result.get(0).getSecondaryValue(), 0.001);
    }
}
