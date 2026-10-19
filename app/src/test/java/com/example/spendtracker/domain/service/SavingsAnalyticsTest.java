package com.example.spendtracker.domain.service;

import com.example.spendtracker.data.local.dao.TransactionDao;
import com.example.spendtracker.domain.model.analytics.*;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.TimeZone;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class SavingsAnalyticsTest {
    private AnalyticsSummary summary(double income, double expense, double transfer) {
        return new AnalyticsSummary(income, expense, transfer, income > 0 ? 1 : 0,
                expense > 0 ? 1 : 0, transfer > 0 ? 1 : 0, expense, income, 7);
    }
    @Test public void savingsExcludeTransfersAndComparePercentagePoints() {
        SavingsAnalysis result = new SavingsAnalysis(summary(10000, 6000, 80000), summary(10000, 8000, 0),
                ComparisonWindow.previous(100, 200, AnalyticsGranularity.WEEK));
        assertEquals(40, result.savingsRate(), 0.001);
        assertEquals(60, result.expenseRatio(), 0.001);
        assertEquals(2000, result.surplusChange(), 0.001);
        assertEquals(20, result.rateChangePoints(), 0.001);
    }
    @Test public void zeroIncomeIsUnavailableNotZeroPercentSavings() {
        SavingsAnalysis result = new SavingsAnalysis(summary(0, 500, 0), summary(0, 0, 0),
                ComparisonWindow.previous(100, 200, AnalyticsGranularity.DAY));
        assertFalse(result.hasSavingsRate());
        assertTrue(Double.isNaN(result.savingsRate()));
        assertFalse(result.hasRateComparison());
        assertEquals(-500, result.current.getNetBalance(), 0.001);
    }
    @Test public void overspendingRemainsNegativeAndNoExpensesMeansFullSurplus() {
        SavingsAnalysis deficit = new SavingsAnalysis(summary(100, 150, 0), summary(100, 0, 0),
                ComparisonWindow.previous(100, 200, AnalyticsGranularity.DAY));
        assertEquals(-50, deficit.savingsRate(), 0.001);
        assertEquals(150, deficit.expenseRatio(), 0.001);
        assertEquals(-150, deficit.rateChangePoints(), 0.001);
    }
    private long date(int year, int month, int day) {
        Calendar calendar = Calendar.getInstance(); calendar.clear();
        calendar.set(year, month - 1, day);
        return calendar.getTimeInMillis();
    }
    @Test public void wholeMonthComparisonUsesPreviousCalendarMonthIncludingLeapDay() {
        long start = date(2024, 3, 1), end = date(2024, 4, 1);
        ComparisonWindow previous = ComparisonWindow.previous(start, end, AnalyticsGranularity.MONTH);
        assertEquals(date(2024, 2, 1), previous.start);
        assertEquals(start, previous.end);
    }
    @Test public void calendarMonthComparisonWorksAcrossDaylightSaving() {
        TimeZone original = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"));
            ComparisonWindow previous = ComparisonWindow.previous(date(2024, 4, 1), date(2024, 5, 1), AnalyticsGranularity.MONTH);
            assertEquals(date(2024, 3, 1), previous.start);
            assertEquals(date(2024, 4, 1), previous.end);
        } finally { TimeZone.setDefault(original); }
    }
    @Test public void customRangeUsesAdjacentEqualDuration() {
        ComparisonWindow previous = ComparisonWindow.previous(100, 250, AnalyticsGranularity.MONTH);
        assertEquals(-50, previous.start);
        assertEquals(100, previous.end);
    }
    @Test public void everyGranularityQueriesTheSelectedBuckets() {
        TransactionDao dao = mock(TransactionDao.class);
        TransactionDao.AnalyticsBucket bucket = new TransactionDao.AnalyticsBucket();
        bucket.label = "bucket"; bucket.incomeAmount = 900; bucket.expenseAmount = 300; bucket.transferAmount = 7000;
        List<TransactionDao.AnalyticsBucket> buckets = Collections.singletonList(bucket);
        when(dao.getDailyAnalyticsBucketsSync(100, 200)).thenReturn(buckets);
        when(dao.getWeeklyAnalyticsBucketsSync(100, 200)).thenReturn(buckets);
        when(dao.getMonthlyAnalyticsBucketsSync(100, 200)).thenReturn(buckets);
        when(dao.getAnnualAnalyticsBucketsSync(100, 200)).thenReturn(buckets);
        AnalyticsService service = new AnalyticsService(dao);
        for (AnalyticsGranularity granularity : AnalyticsGranularity.values()) {
            TimeSeriesPoint point = service.getIncomeExpenseTrend(100, 200, granularity).get(0);
            assertEquals(900, point.getValue(), 0.001);
            assertEquals(300, point.getSecondaryValue(), 0.001);
        }
        verify(dao).getDailyAnalyticsBucketsSync(100, 200);
        verify(dao).getWeeklyAnalyticsBucketsSync(100, 200);
        verify(dao).getMonthlyAnalyticsBucketsSync(100, 200);
        verify(dao).getAnnualAnalyticsBucketsSync(100, 200);
        verify(dao, never()).getMonthlyTotalsSync(anyLong(), anyLong(), anyString());
    }
    private TransactionDao.CategorySum category(String name, double total) {
        TransactionDao.CategorySum value = new TransactionDao.CategorySum(); value.category = name; value.total = total; return value;
    }
    @Test public void categoryTrendsIncludeNewStoppedAndChangedSpendingWithoutOverlap() {
        TransactionDao dao = mock(TransactionDao.class);
        when(dao.getCategoryTotalsSync(100, 199)).thenReturn(Arrays.asList(category("Food", 150), category("New", 40)));
        when(dao.getCategoryTotalsSync(0, 99)).thenReturn(Arrays.asList(category("Food", 100), category("Travel", 300)));
        List<CategoryAnalytics> result = new AnalyticsService(dao).getCategoryGrowth(100, 200, AnalyticsGranularity.WEEK);
        assertEquals(3, result.size());
        assertEquals(50, result.get(0).getGrowthPercent(), 0.001);
        assertFalse(result.get(1).hasGrowthData());
        assertEquals("Travel", result.get(2).getCategoryName());
        assertEquals(-100, result.get(2).getGrowthPercent(), 0.001);
        assertEquals(0, result.get(2).getTotalAmount(), 0.001);
        verify(dao).getCategoryTotalsSync(0, 99);
        verify(dao, never()).getCategoryTotalsSync(0, 100);
    }
    @Test public void savingsQueriesUseNonOverlappingBoundaries() {
        TransactionDao dao = mock(TransactionDao.class);
        new AnalyticsService(dao).getSavingsAnalysis(100, 200, AnalyticsGranularity.WEEK);
        verify(dao).getFinancialTotalsSync(100, 199);
        verify(dao).getFinancialTotalsSync(0, 99);
    }
}
