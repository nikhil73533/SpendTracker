package com.example.spendtracker.domain.repository;

import androidx.lifecycle.LiveData;
import com.example.spendtracker.domain.model.analytics.AnalyticsSummary;
import com.example.spendtracker.domain.model.analytics.CategoryAnalytics;
import com.example.spendtracker.domain.model.analytics.RollingAverage;
import com.example.spendtracker.domain.model.analytics.TimeSeriesPoint;
import com.example.spendtracker.domain.model.analytics.BehaviorAnalytics;
import com.example.spendtracker.domain.model.analytics.ForecastResult;
import com.example.spendtracker.domain.model.analytics.MonthlyComparison;
import com.example.spendtracker.domain.model.analytics.TransactionVolume;
import com.example.spendtracker.data.local.entity.TransactionEntity;
import java.util.List;

/**
 * Repository exposing analytics results as LiveData for UI consumption.
 */
public interface AnalyticsRepository {
    LiveData<AnalyticsSummary> getFinancialOverview(long start, long end);
    LiveData<List<TransactionEntity>> getTopTransactions(long start, long end, int limit);
    LiveData<List<TimeSeriesPoint>> getMonthlyTrend(long start, long end, String type);
    LiveData<List<CategoryAnalytics>> getExpenseCategoryAnalytics(long start, long end);
    LiveData<RollingAverage> getRollingExpenseAverage(int months);
    LiveData<TransactionVolume> getTransactionVolume(long start, long end, String periodLabel);
    LiveData<List<TimeSeriesPoint>> getIncomeExpenseTrend(long start, long end);
    LiveData<BehaviorAnalytics> getDayNightAnalytics(long start, long end);
    LiveData<BehaviorAnalytics> getTimeOfDayAnalytics(long start, long end);
    LiveData<BehaviorAnalytics> getDayOfWeekAnalytics(long start, long end);
    LiveData<BehaviorAnalytics> getWeekdayWeekendAnalytics(long start, long end);
    LiveData<ForecastResult> getSpendingVelocity(long start, long end);
    LiveData<MonthlyComparison> getMonthOverMonthComparison(long start, long end);
}
