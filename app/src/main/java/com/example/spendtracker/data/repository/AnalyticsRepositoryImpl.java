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
import com.example.spendtracker.data.local.entity.TransactionEntity;
import com.example.spendtracker.domain.repository.AnalyticsRepository;
import com.example.spendtracker.domain.service.AnalyticsService;
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
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Inject
    public AnalyticsRepositoryImpl(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @Override
    public LiveData<AnalyticsSummary> getFinancialOverview(long start, long end) {
        MutableLiveData<AnalyticsSummary> liveData = new MutableLiveData<>();
        executor.execute(() -> liveData.postValue(analyticsService.getFinancialOverview(start, end)));
        return liveData;
    }

    @Override
    public LiveData<List<TransactionEntity>> getTopTransactions(long start, long end, int limit) {
        MutableLiveData<List<TransactionEntity>> liveData = new MutableLiveData<>();
        executor.execute(() -> liveData.postValue(analyticsService.getTopTransactions(start, end, limit)));
        return liveData;
    }

    @Override
    public LiveData<List<TimeSeriesPoint>> getMonthlyTrend(long start, long end, String type) {
        MutableLiveData<List<TimeSeriesPoint>> liveData = new MutableLiveData<>();
        executor.execute(() -> liveData.postValue(analyticsService.getMonthlyTrend(start, end, type)));
        return liveData;
    }

    @Override
    public LiveData<List<CategoryAnalytics>> getExpenseCategoryAnalytics(long start, long end) {
        MutableLiveData<List<CategoryAnalytics>> liveData = new MutableLiveData<>();
        executor.execute(() -> liveData.postValue(analyticsService.getExpenseCategoryAnalytics(start, end)));
        return liveData;
    }

    @Override
    public LiveData<RollingAverage> getRollingExpenseAverage(int months) {
        MutableLiveData<RollingAverage> liveData = new MutableLiveData<>();
        executor.execute(() -> liveData.postValue(analyticsService.getRollingExpenseAverage(months)));
        return liveData;
    }

    @Override
    public LiveData<TransactionVolume> getTransactionVolume(long start, long end, String periodLabel) {
        MutableLiveData<TransactionVolume> liveData = new MutableLiveData<>();
        executor.execute(() -> liveData.postValue(analyticsService.getTransactionVolume(start, end, periodLabel)));
        return liveData;
    }

    @Override
    public LiveData<List<TimeSeriesPoint>> getIncomeExpenseTrend(long start, long end) {
        MutableLiveData<List<TimeSeriesPoint>> liveData = new MutableLiveData<>();
        executor.execute(() -> liveData.postValue(analyticsService.getIncomeExpenseTrend(start, end)));
        return liveData;
    }

    @Override
    public LiveData<BehaviorAnalytics> getDayNightAnalytics(long start, long end) {
        MutableLiveData<BehaviorAnalytics> liveData = new MutableLiveData<>();
        executor.execute(() -> liveData.postValue(analyticsService.getDayNightAnalytics(start, end)));
        return liveData;
    }

    @Override
    public LiveData<BehaviorAnalytics> getTimeOfDayAnalytics(long start, long end) {
        MutableLiveData<BehaviorAnalytics> liveData = new MutableLiveData<>();
        executor.execute(() -> liveData.postValue(analyticsService.getTimeOfDayAnalytics(start, end)));
        return liveData;
    }

    @Override
    public LiveData<BehaviorAnalytics> getDayOfWeekAnalytics(long start, long end) {
        MutableLiveData<BehaviorAnalytics> liveData = new MutableLiveData<>();
        executor.execute(() -> liveData.postValue(analyticsService.getDayOfWeekAnalytics(start, end)));
        return liveData;
    }

    @Override
    public LiveData<BehaviorAnalytics> getWeekdayWeekendAnalytics(long start, long end) {
        MutableLiveData<BehaviorAnalytics> liveData = new MutableLiveData<>();
        executor.execute(() -> liveData.postValue(analyticsService.getWeekdayWeekendAnalytics(start, end)));
        return liveData;
    }

    @Override
    public LiveData<ForecastResult> getSpendingVelocity(long start, long end) {
        MutableLiveData<ForecastResult> liveData = new MutableLiveData<>();
        executor.execute(() -> liveData.postValue(analyticsService.getSpendingVelocity(start, end)));
        return liveData;
    }

    @Override
    public LiveData<MonthlyComparison> getMonthOverMonthComparison(long start, long end) {
        MutableLiveData<MonthlyComparison> liveData = new MutableLiveData<>();
        executor.execute(() -> liveData.postValue(analyticsService.getMonthOverMonthComparison(start, end)));
        return liveData;
    }
}
