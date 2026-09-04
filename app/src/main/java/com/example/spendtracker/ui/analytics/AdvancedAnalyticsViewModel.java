package com.example.spendtracker.ui.analytics;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;
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
import com.example.spendtracker.domain.repository.AnalyticsRepository;
import com.example.spendtracker.data.local.entity.TransactionEntity;
import java.util.Calendar;
import java.util.List;

import dagger.hilt.android.lifecycle.HiltViewModel;
import javax.inject.Inject;
import androidx.lifecycle.MediatorLiveData;

/**
 * ViewModel for the Advanced Analytics screen.
 * Manages a date‑range filter (start/end epoch millis) and forwards requests to the
 * {@link AnalyticsRepository}. All heavy work is performed in the repository's background
 * executor; the ViewModel merely exposes LiveData for UI consumption.
 */
@HiltViewModel
public class AdvancedAnalyticsViewModel extends ViewModel {

    private final AnalyticsRepository analyticsRepository;

    // Date range filter – defaults to today (midnight to now)
    private final MutableLiveData<Long> startDate = new MutableLiveData<>();
    private final MutableLiveData<Long> endDate = new MutableLiveData<>();

    // LiveData streams derived from the date range
    private final LiveData<AnalyticsSummary> financialOverview;
    private final LiveData<List<TimeSeriesPoint>> monthlyExpenseTrend;
    private final LiveData<List<CategoryAnalytics>> expenseCategoryAnalytics;
    private final LiveData<List<TransactionEntity>> topTransactions;
    private final LiveData<RollingAverage> rollingExpenseAverage;

    private final LiveData<TransactionVolume> transactionVolume;
    private final LiveData<List<TimeSeriesPoint>> incomeExpenseTrend;
    private final LiveData<BehaviorAnalytics> dayNightAnalytics;
    private final LiveData<BehaviorAnalytics> timeOfDayAnalytics;
    private final LiveData<BehaviorAnalytics> dayOfWeekAnalytics;
    private final LiveData<BehaviorAnalytics> weekdayWeekendAnalytics;
    private final LiveData<ForecastResult> spendingVelocity;
    private final LiveData<MonthlyComparison> monthOverMonthComparison;

    private final LiveData<List<MerchantAnalytics>> merchantAnalytics;
    private final LiveData<List<MerchantAnalytics>> recurringMerchants;
    private final LiveData<List<CategoryAnalytics>> categoryGrowth;
    private final LiveData<SpendingConcentration> spendingConcentration;
    private final LiveData<LargeTransactionSummary> largeTransactionAnalysis;
    private final LiveData<List<AnomalyTransaction>> unusualTransactions;

    private final LiveData<List<FinancialInsight>> financialInsights;
    private final LiveData<ForecastResult> spendingForecast;

    @Inject
    public AdvancedAnalyticsViewModel(AnalyticsRepository analyticsRepository) {
        this.analyticsRepository = analyticsRepository;
        // Initialise default date range – start of current day to now
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        long todayStart = cal.getTimeInMillis();
        long now = System.currentTimeMillis();
        startDate.setValue(todayStart);
        endDate.setValue(now);

        // -----------------------------------------------------------
        // Reactive streams – recompute whenever start or end changes
        // -----------------------------------------------------------
        financialOverview = combineDates((s, e) -> analyticsRepository.getFinancialOverview(s, e));
        monthlyExpenseTrend = combineDates((s, e) -> analyticsRepository.getMonthlyTrend(s, e, "EXPENSE"));
        expenseCategoryAnalytics = combineDates((s, e) -> analyticsRepository.getExpenseCategoryAnalytics(s, e));
        topTransactions = combineDates((s, e) -> analyticsRepository.getTopTransactions(s, e, 5));
        
        transactionVolume = combineDates((s, e) -> analyticsRepository.getTransactionVolume(s, e, "Current Period"));
        incomeExpenseTrend = combineDates((s, e) -> analyticsRepository.getIncomeExpenseTrend(s, e));
        dayNightAnalytics = combineDates((s, e) -> analyticsRepository.getDayNightAnalytics(s, e));
        timeOfDayAnalytics = combineDates((s, e) -> analyticsRepository.getTimeOfDayAnalytics(s, e));
        dayOfWeekAnalytics = combineDates((s, e) -> analyticsRepository.getDayOfWeekAnalytics(s, e));
        weekdayWeekendAnalytics = combineDates((s, e) -> analyticsRepository.getWeekdayWeekendAnalytics(s, e));
        spendingVelocity = combineDates((s, e) -> analyticsRepository.getSpendingVelocity(s, e));
        monthOverMonthComparison = combineDates((s, e) -> analyticsRepository.getMonthOverMonthComparison(s, e));
        
        merchantAnalytics = combineDates((s, e) -> analyticsRepository.getMerchantAnalytics(s, e));
        recurringMerchants = combineDates((s, e) -> analyticsRepository.getRecurringMerchants());
        categoryGrowth = combineDates((s, e) -> analyticsRepository.getCategoryGrowth(s, e));
        spendingConcentration = combineDates((s, e) -> analyticsRepository.getSpendingConcentration(s, e));
        largeTransactionAnalysis = combineDates((s, e) -> analyticsRepository.getLargeTransactionAnalysis(s, e, 5000.0));
        unusualTransactions = combineDates((s, e) -> analyticsRepository.getUnusualTransactions(s, e));

        financialInsights = combineDates((s, e) -> analyticsRepository.generateInsights(s, e));
        spendingForecast = combineDates((s, e) -> analyticsRepository.getSpendingForecast(s, e));
        
        // Rolling average only depends on the end date (or current date) - using a simple map for now
        rollingExpenseAverage = Transformations.switchMap(endDate, e -> analyticsRepository.getRollingExpenseAverage(3));
    }

    /** Helper functional interface for repository calls that need a start/end range. */
    private interface DateRangeRepo<T> {
        LiveData<T> call(long start, long end);
    }

    /**
     * Combines startDate and endDate LiveData into a single LiveData stream using MediatorLiveData.
     */
    private <T> LiveData<T> combineDates(DateRangeRepo<T> repoCall) {
        MediatorLiveData<T> result = new MediatorLiveData<>();
        
        // This inner LiveData holds the actual stream returned by the repository
        final MutableLiveData<LiveData<T>> currentSource = new MutableLiveData<>();
        
        Runnable updateSource = () -> {
            Long s = startDate.getValue();
            Long e = endDate.getValue();
            if (s != null && e != null) {
                LiveData<T> oldSource = currentSource.getValue();
                if (oldSource != null) {
                    result.removeSource(oldSource);
                }
                
                LiveData<T> newSource = repoCall.call(s, e);
                currentSource.setValue(newSource);
                
                result.addSource(newSource, result::setValue);
            }
        };

        result.addSource(startDate, val -> updateSource.run());
        result.addSource(endDate, val -> updateSource.run());
        
        return result;
    }

    // ---------------------------------------------------
    // Public accessors for the UI
    // ---------------------------------------------------
    public LiveData<AnalyticsSummary> getFinancialOverview() { return financialOverview; }
    public LiveData<List<TimeSeriesPoint>> getMonthlyExpenseTrend() { return monthlyExpenseTrend; }
    public LiveData<List<CategoryAnalytics>> getExpenseCategoryAnalytics() { return expenseCategoryAnalytics; }
    public LiveData<List<TransactionEntity>> getTopTransactions() { return topTransactions; }
    public LiveData<RollingAverage> getRollingExpenseAverage() { return rollingExpenseAverage; }
    
    public LiveData<TransactionVolume> getTransactionVolume() { return transactionVolume; }
    public LiveData<List<TimeSeriesPoint>> getIncomeExpenseTrend() { return incomeExpenseTrend; }
    public LiveData<BehaviorAnalytics> getDayNightAnalytics() { return dayNightAnalytics; }
    public LiveData<BehaviorAnalytics> getTimeOfDayAnalytics() { return timeOfDayAnalytics; }
    public LiveData<BehaviorAnalytics> getDayOfWeekAnalytics() { return dayOfWeekAnalytics; }
    public LiveData<BehaviorAnalytics> getWeekdayWeekendAnalytics() { return weekdayWeekendAnalytics; }
    public LiveData<ForecastResult> getSpendingVelocity() { return spendingVelocity; }
    public LiveData<MonthlyComparison> getMonthOverMonthComparison() { return monthOverMonthComparison; }

    public LiveData<List<MerchantAnalytics>> getMerchantAnalytics() { return merchantAnalytics; }
    public LiveData<List<MerchantAnalytics>> getRecurringMerchants() { return recurringMerchants; }
    public LiveData<List<CategoryAnalytics>> getCategoryGrowth() { return categoryGrowth; }
    public LiveData<SpendingConcentration> getSpendingConcentration() { return spendingConcentration; }
    public LiveData<LargeTransactionSummary> getLargeTransactionAnalysis() { return largeTransactionAnalysis; }
    public LiveData<List<AnomalyTransaction>> getUnusualTransactions() { return unusualTransactions; }

    public LiveData<List<FinancialInsight>> getFinancialInsights() { return financialInsights; }
    public LiveData<ForecastResult> getSpendingForecast() { return spendingForecast; }

    // ---------------------------------------------------
    // Date range setters (called by UI when user selects a new period)
    // ---------------------------------------------------
    public void setStartDate(long epochMillis) { startDate.setValue(epochMillis); }
    public void setEndDate(long epochMillis) { endDate.setValue(epochMillis); }
}
