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
import com.example.spendtracker.domain.model.analytics.AnalyticsGranularity;
import com.example.spendtracker.domain.repository.AnalyticsRepository;
import com.example.spendtracker.domain.repository.SecurityRepository;
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
    private final SecurityRepository securityRepository;
    private final LiveData<com.example.spendtracker.domain.model.PredictionInsights> predictionInsights;

    public LiveData<com.example.spendtracker.domain.model.PredictionInsights> getPredictionInsights() { return predictionInsights; }

    // Date range filter – defaults to today (midnight to now)
    private static final class DateRange {
        final long start, end;
        DateRange(long start, long end) { this.start = start; this.end = end; }
    }
    private final MutableLiveData<DateRange> dateRange = new MutableLiveData<>();
    private final MutableLiveData<AnalyticsGranularity> granularity = new MutableLiveData<>();

    // LiveData streams derived from the date range
    private final LiveData<AnalyticsSummary> financialOverview;
    private final LiveData<com.example.spendtracker.domain.model.analytics.SavingsAnalysis> savingsAnalysis;
    private final LiveData<List<TimeSeriesPoint>> monthlyExpenseTrend;
    private final LiveData<List<TimeSeriesPoint>> transactionFrequency;
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
    public AdvancedAnalyticsViewModel(AnalyticsRepository analyticsRepository,
                                      com.example.spendtracker.domain.repository.TransactionRepository transactions,
                                      SecurityRepository securityRepository) {
        predictionInsights = Transformations.map(transactions.getTransactions(), com.example.spendtracker.domain.model.PredictionInsights::from);
        this.analyticsRepository = analyticsRepository;
        this.securityRepository = securityRepository;
        // Initialise default date range – start of the current month to now.
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.DAY_OF_MONTH, 1);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        long monthStart = cal.getTimeInMillis();
        long now = System.currentTimeMillis();
        dateRange.setValue(new DateRange(monthStart, now));
        granularity.setValue(AnalyticsGranularity.MONTH);

        // -----------------------------------------------------------
        // Reactive streams – recompute whenever start or end changes
        // -----------------------------------------------------------
        financialOverview = combineDates((s, e) -> analyticsRepository.getFinancialOverview(s, e - 1));
        savingsAnalysis = combineDatesAndGranularity(analyticsRepository::getSavingsAnalysis);
        monthlyExpenseTrend = combineDates((s, e) -> analyticsRepository.getMonthlyTrend(s, e - 1, "EXPENSE"));
        transactionFrequency = combineDatesAndGranularity((s, e, g) -> analyticsRepository.getTransactionFrequency(s, e, g));
        expenseCategoryAnalytics = combineDates((s, e) -> analyticsRepository.getExpenseCategoryAnalytics(s, e));
        topTransactions = combineDates((s, e) -> analyticsRepository.getTopTransactions(s, e - 1, 5));
        
        transactionVolume = combineDates((s, e) -> analyticsRepository.getTransactionVolume(s, e - 1, "Current Period"));
        incomeExpenseTrend = combineDatesAndGranularity(analyticsRepository::getIncomeExpenseTrend);
        dayNightAnalytics = combineDates((s, e) -> analyticsRepository.getDayNightAnalytics(s, e - 1));
        timeOfDayAnalytics = combineDates((s, e) -> analyticsRepository.getTimeOfDayAnalytics(s, e - 1));
        dayOfWeekAnalytics = combineDates((s, e) -> analyticsRepository.getDayOfWeekAnalytics(s, e - 1));
        weekdayWeekendAnalytics = combineDates((s, e) -> analyticsRepository.getWeekdayWeekendAnalytics(s, e - 1));
        spendingVelocity = combineDates((s, e) -> analyticsRepository.getSpendingVelocity(s, e - 1));
        monthOverMonthComparison = combineDates((s, e) -> analyticsRepository.getMonthOverMonthComparison(s, e - 1));
        
        merchantAnalytics = combineDates((s, e) -> analyticsRepository.getMerchantAnalytics(s, e - 1));
        recurringMerchants = combineDates((s, e) -> analyticsRepository.getRecurringMerchants());
        categoryGrowth = combineDatesAndGranularity(analyticsRepository::getCategoryGrowth);
        spendingConcentration = combineDates((s, e) -> analyticsRepository.getSpendingConcentration(s, e - 1));
        largeTransactionAnalysis = combineDates((s, e) -> analyticsRepository.getLargeTransactionAnalysis(s, e - 1, 5000.0));
        unusualTransactions = combineDates((s, e) -> analyticsRepository.getUnusualTransactions(s, e - 1));

        financialInsights = combineDates((s, e) -> analyticsRepository.generateInsights(s, e - 1));
        spendingForecast = combineDates((s, e) -> analyticsRepository.getSpendingForecast(s, e - 1));
        // Rolling average only depends on the end date (or current date) - using a simple map for now
        rollingExpenseAverage = Transformations.switchMap(dateRange, e -> analyticsRepository.getRollingExpenseAverage(3));
    }

    /** Helper functional interface for repository calls that need a start/end range. */
    private interface DateRangeRepo<T> {
        LiveData<T> call(long start, long end);
    }

    private interface GranularDateRangeRepo<T> {
        LiveData<T> call(long start, long end, AnalyticsGranularity granularity);
    }

    /**
     * Combines startDate and endDate LiveData into a single LiveData stream using MediatorLiveData.
     */
    private <T> LiveData<T> combineDates(DateRangeRepo<T> repoCall) {
        return Transformations.switchMap(dateRange, range -> repoCall.call(range.start, range.end));
    }

    private <T> LiveData<T> combineDatesAndGranularity(GranularDateRangeRepo<T> repoCall) {
        return Transformations.switchMap(dateRange, range -> Transformations.switchMap(granularity,
                value -> repoCall.call(range.start, range.end, value)));
    }

    // ---------------------------------------------------
    // Public accessors for the UI
    // ---------------------------------------------------
    public LiveData<AnalyticsSummary> getFinancialOverview() { return financialOverview; }
    public LiveData<com.example.spendtracker.domain.model.analytics.SavingsAnalysis> getSavingsAnalysis() { return savingsAnalysis; }
    public LiveData<AnalyticsGranularity> getGranularity() { return granularity; }
    public LiveData<List<TimeSeriesPoint>> getMonthlyExpenseTrend() { return monthlyExpenseTrend; }
    public LiveData<List<TimeSeriesPoint>> getTransactionFrequency() { return transactionFrequency; }
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
    public LiveData<Boolean> isPrivacyModeEnabled() { return securityRepository.isPrivacyModeEnabled(); }
    public void setPrivacyModeEnabled(boolean enabled) { securityRepository.setPrivacyModeEnabled(enabled); }
    // ---------------------------------------------------
    // Date range setters (called by UI when user selects a new period)
    // ---------------------------------------------------
    public long getStartDate() { return dateRange.getValue().start; }
    public long getEndDate() { return dateRange.getValue().end; }
    public LiveData<?> getDateRange() { return dateRange; }
    public void setDateRange(long start, long end) {
        if (end <= start) return;
        DateRange current = dateRange.getValue();
        if (current.start != start || current.end != end) dateRange.setValue(new DateRange(start, end));
    }
    public void setStartDate(long value) { setDateRange(value, getEndDate()); }
    public void setEndDate(long value) { setDateRange(getStartDate(), value); }
    /**
     * The chips are analytics windows, not merely a chart display preference.  Keeping the
     * range in the same state stream makes every card and chart refresh from the same window.
     */
    public void setGranularity(AnalyticsGranularity value) {
        if (value == null) return;
        granularity.setValue(value);
        Calendar calendar = Calendar.getInstance();
        DateRange current = dateRange.getValue();
        long anchor = current == null ? System.currentTimeMillis()
                : Math.min(System.currentTimeMillis(), Math.max(current.start, current.end - 1));
        calendar.setTimeInMillis(anchor);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        long start;
        switch (value) {
            case DAY:
                start = calendar.getTimeInMillis();
                calendar.add(Calendar.DAY_OF_MONTH, 1);
                break;
            case WEEK:
                calendar.add(Calendar.DAY_OF_MONTH, -6);
                start = calendar.getTimeInMillis();
                calendar.add(Calendar.DAY_OF_MONTH, 7);
                break;
            case YEAR:
                calendar.set(Calendar.DAY_OF_YEAR, 1);
                start = calendar.getTimeInMillis();
                calendar.add(Calendar.YEAR, 1);
                break;
            case MONTH:
            default:
                calendar.set(Calendar.DAY_OF_MONTH, 1);
                start = calendar.getTimeInMillis();
                calendar.add(Calendar.MONTH, 1);
                break;
        }
        setDateRange(start, calendar.getTimeInMillis());
    }
}
  
