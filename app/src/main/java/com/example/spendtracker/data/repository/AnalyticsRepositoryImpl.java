package com.example.spendtracker.data.repository;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.example.spendtracker.domain.model.analytics.AnalyticsSummary;
import com.example.spendtracker.domain.model.analytics.CategoryAnalytics;
import com.example.spendtracker.domain.model.analytics.RollingAverage;
import com.example.spendtracker.domain.model.analytics.TimeSeriesPoint;
import com.example.spendtracker.domain.model.analytics.BehaviorAnalytics;
import com.example.spendtracker.domain.model.analytics.ForecastResult;
import com.example.spendtracker.domain.model.analytics.MonthlyComparison;
import com.example.spendtracker.domain.model.analytics.TransactionVolume;
import com.example.spendtracker.domain.model.analytics.MerchantAnalytics;
import com.example.spendtracker.domain.model.analytics.SpendingConcentration;
import com.example.spendtracker.domain.model.analytics.LargeTransactionSummary;
import com.example.spendtracker.domain.model.analytics.AnomalyTransaction;
import com.example.spendtracker.domain.model.analytics.FinancialInsight;
import com.example.spendtracker.domain.model.analytics.AnalyticsGranularity;
import com.example.spendtracker.data.local.entity.TransactionEntity;
import com.example.spendtracker.domain.repository.AnalyticsRepository;
import com.example.spendtracker.domain.service.AnalyticsService;
import com.example.spendtracker.domain.service.InsightEngine;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Implementation of {@link AnalyticsRepository}. All heavy DAO work is delegated to
 * {@link AnalyticsService} which runs on a background thread via this repository's executor.
 */
@Singleton
public class AnalyticsRepositoryImpl implements AnalyticsRepository {

    private final AnalyticsService analyticsService;
    private final InsightEngine insightEngine;
    private final com.example.spendtracker.data.local.database.SpendTrackerDatabase database;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Inject
    public AnalyticsRepositoryImpl(AnalyticsService analyticsService, InsightEngine insightEngine,
            @com.example.spendtracker.di.MainDatabase com.example.spendtracker.data.local.database.SpendTrackerDatabase database) {
        this.database = database;
        this.analyticsService = analyticsService;
        this.insightEngine = insightEngine;
    }

    @Override
    public LiveData<AnalyticsSummary> getFinancialOverview(long start, long end) {
        return observe(() -> analyticsService.getFinancialOverview(start, end));
    }

    @Override
    public LiveData<List<TransactionEntity>> getTopTransactions(long start, long end, int limit) {
        return observe(() -> analyticsService.getTopTransactions(start, end, limit));
    }

    @Override
    public LiveData<List<TimeSeriesPoint>> getMonthlyTrend(long start, long end, String type) {
        return observe(() -> analyticsService.getMonthlyTrend(start, end, type));
    }

    @Override
    public LiveData<List<TimeSeriesPoint>> getTransactionFrequency(long start, long end, AnalyticsGranularity granularity) {
        return observe(() -> analyticsService.getTransactionFrequency(start, end, granularity));
    }

    @Override
    public LiveData<List<CategoryAnalytics>> getExpenseCategoryAnalytics(long start, long end) {
        return observe(() -> analyticsService.getExpenseCategoryAnalytics(start, end));
    }

    @Override
    public LiveData<RollingAverage> getRollingExpenseAverage(int months) {
        return observe(() -> analyticsService.getRollingExpenseAverage(months));
    }

    @Override
    public LiveData<TransactionVolume> getTransactionVolume(long start, long end, String periodLabel) {
        return observe(() -> analyticsService.getTransactionVolume(start, end, periodLabel));
    }

    @Override
    public LiveData<List<TimeSeriesPoint>> getIncomeExpenseTrend(long start, long end) {
        return observe(() -> analyticsService.getIncomeExpenseTrend(start, end));
    }

    @Override
    public LiveData<BehaviorAnalytics> getDayNightAnalytics(long start, long end) {
        return observe(() -> analyticsService.getDayNightAnalytics(start, end));
    }

    @Override
    public LiveData<BehaviorAnalytics> getTimeOfDayAnalytics(long start, long end) {
        return observe(() -> analyticsService.getTimeOfDayAnalytics(start, end));
    }

    @Override
    public LiveData<BehaviorAnalytics> getDayOfWeekAnalytics(long start, long end) {
        return observe(() -> analyticsService.getDayOfWeekAnalytics(start, end));
    }

    @Override
    public LiveData<BehaviorAnalytics> getWeekdayWeekendAnalytics(long start, long end) {
        return observe(() -> analyticsService.getWeekdayWeekendAnalytics(start, end));
    }

    @Override
    public LiveData<ForecastResult> getSpendingVelocity(long start, long end) {
        return observe(() -> analyticsService.getSpendingVelocity(start, end));
    }

    @Override
    public LiveData<MonthlyComparison> getMonthOverMonthComparison(long start, long end) {
        return observe(() -> analyticsService.getMonthOverMonthComparison(start, end));
    }

    @Override
    public LiveData<List<MerchantAnalytics>> getMerchantAnalytics(long start, long end) {
        return observe(() -> analyticsService.getMerchantAnalytics(start, end));
    }

    @Override
    public LiveData<List<MerchantAnalytics>> getRecurringMerchants() {
        return observe(() -> analyticsService.getRecurringMerchants());
    }

    @Override
    public LiveData<List<CategoryAnalytics>> getCategoryGrowth(long start, long end) {
        return observe(() -> analyticsService.getCategoryGrowth(start, end));
    }

    @Override
    public LiveData<SpendingConcentration> getSpendingConcentration(long start, long end) {
        return observe(() -> analyticsService.getSpendingConcentration(start, end));
    }

    @Override
    public LiveData<LargeTransactionSummary> getLargeTransactionAnalysis(long start, long end, double threshold) {
        return observe(() -> analyticsService.getLargeTransactionAnalysis(start, end, threshold));
    }

    @Override
    public LiveData<List<AnomalyTransaction>> getUnusualTransactions(long start, long end) {
        return observe(() -> analyticsService.getUnusualTransactions(start, end));
    }

    @Override
    public LiveData<List<FinancialInsight>> generateInsights(long start, long end) {
        return observe(() -> insightEngine.generateInsights(start, end));
    }

    @Override
    public LiveData<ForecastResult> getSpendingForecast(long start, long end) {
        return observe(() -> insightEngine.getSpendingForecast(start, end));
    }
    private <T> LiveData<T> observe(java.util.concurrent.Callable<T> query) {
        return new LiveData<T>() {
            private final android.os.Handler main = new android.os.Handler(android.os.Looper.getMainLooper());
            private final java.util.concurrent.atomic.AtomicInteger generation = new java.util.concurrent.atomic.AtomicInteger();
            private final androidx.room.InvalidationTracker.Observer observer = new androidx.room.InvalidationTracker.Observer("transactions") {
                @Override public void onInvalidated(java.util.Set<String> tables) { refresh(); }
            };
            private void refresh() {
                int request = generation.incrementAndGet();
                executor.execute(() -> {
                    if (request != generation.get()) return;
                    try {
                        T value = query.call();
                        main.post(() -> { if (hasActiveObservers() && request == generation.get()) setValue(value); });
                    } catch (Exception error) {
                        android.util.Log.e("Analytics", "Unable to load analytics", error);
                    }
                });
            }
            @Override protected void onActive() { database.getInvalidationTracker().addObserver(observer); refresh(); }
            @Override protected void onInactive() { generation.incrementAndGet(); database.getInvalidationTracker().removeObserver(observer); }
        };
    }

}
