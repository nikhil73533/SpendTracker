package com.example.spendtracker.ui.charts;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;
import com.example.spendtracker.domain.model.Summary;
import com.example.spendtracker.domain.repository.SecurityRepository;
import com.example.spendtracker.domain.repository.TransactionRepository;
import java.util.Calendar;
import java.util.List;
import dagger.hilt.android.lifecycle.HiltViewModel;
import javax.inject.Inject;

@HiltViewModel
public class ChartsViewModel extends ViewModel {
    private final TransactionRepository repository;
    private final SecurityRepository securityRepository;

    public enum Granularity { DAILY, WEEKLY, MONTHLY, ANNUALLY }

    private final MutableLiveData<Long> currentMonthStart = new MutableLiveData<>();
    private final MutableLiveData<Granularity> granularity = new MutableLiveData<>(Granularity.MONTHLY);
    private final MutableLiveData<String> transactionType = new MutableLiveData<>("EXPENSE");

    @Inject
    public ChartsViewModel(TransactionRepository repository, SecurityRepository securityRepository) {
        this.repository = repository;
        this.securityRepository = securityRepository;
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.DAY_OF_MONTH, 1);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        currentMonthStart.setValue(cal.getTimeInMillis());
    }

    public LiveData<Long> getCurrentMonthStart() { return currentMonthStart; }
    public LiveData<Granularity> getGranularity() { return granularity; }
    public LiveData<String> getTransactionType() { return transactionType; }

    public void setGranularity(Granularity g) { granularity.setValue(g); }
    public void setTransactionType(String type) { transactionType.setValue(type); }

    public LiveData<Boolean> isPrivacyModeEnabled() { return securityRepository.isPrivacyModeEnabled(); }
    public String formatAmount(double amount) { return securityRepository.maskAmount(amount); }

    public void moveNext() {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(currentMonthStart.getValue());
        Granularity g = granularity.getValue();
        if (g == Granularity.ANNUALLY) {
            cal.add(Calendar.YEAR, 1);
        } else if (g == Granularity.WEEKLY) {
            cal.add(Calendar.WEEK_OF_YEAR, 1);
        } else {
            cal.add(Calendar.MONTH, 1);
        }
        currentMonthStart.setValue(cal.getTimeInMillis());
    }

    public void movePrev() {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(currentMonthStart.getValue());
        Granularity g = granularity.getValue();
        if (g == Granularity.ANNUALLY) {
            cal.add(Calendar.YEAR, -1);
        } else if (g == Granularity.WEEKLY) {
            cal.add(Calendar.WEEK_OF_YEAR, -1);
        } else {
            cal.add(Calendar.MONTH, -1);
        }
        currentMonthStart.setValue(cal.getTimeInMillis());
    }

    public LiveData<Summary> getChartData() {
        return Transformations.switchMap(currentMonthStart, start -> 
            Transformations.switchMap(granularity, g -> {
                Calendar cal = Calendar.getInstance();
                cal.setTimeInMillis(start);
                
                long end;
                if (g == Granularity.ANNUALLY) {
                    cal.set(Calendar.MONTH, 11);
                    cal.set(Calendar.DAY_OF_MONTH, 31);
                } else if (g == Granularity.WEEKLY) {
                    cal.add(Calendar.DAY_OF_YEAR, 6);
                } else {
                    cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH));
                }
                
                cal.set(Calendar.HOUR_OF_DAY, 23);
                cal.set(Calendar.MINUTE, 59);
                cal.set(Calendar.SECOND, 59);
                end = cal.getTimeInMillis();
                
                return repository.getSummary(start, end);
            })
        );
    }

    public LiveData<List<com.example.spendtracker.data.local.dao.TransactionDao.CategorySum>> getWeekdayWeekendTotals(String type) {
        return Transformations.switchMap(currentMonthStart, start ->
            Transformations.switchMap(granularity, g -> {
                long end = calculateEndTime(start, g);
                return repository.getWeekdayWeekendTotals(start, end, type);
            })
        );
    }

    public LiveData<List<com.example.spendtracker.data.local.dao.TransactionDao.CategorySum>> getBankTotals(String type) {
        return Transformations.switchMap(currentMonthStart, start ->
            Transformations.switchMap(granularity, g -> {
                long end = calculateEndTime(start, g);
                return repository.getBankTotals(start, end, type);
            })
        );
    }

    public LiveData<List<com.example.spendtracker.data.local.dao.TransactionDao.CategorySum>> getSourceTypeTotals(String type) {
        return Transformations.switchMap(currentMonthStart, start ->
            Transformations.switchMap(granularity, g -> {
                long end = calculateEndTime(start, g);
                return repository.getSourceTypeTotals(start, end, type);
            })
        );
    }

    private long calculateEndTime(long start, Granularity g) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(start);
        if (g == Granularity.ANNUALLY) {
            cal.set(Calendar.MONTH, 11);
            cal.set(Calendar.DAY_OF_MONTH, 31);
        } else if (g == Granularity.WEEKLY) {
            cal.add(Calendar.DAY_OF_YEAR, 6);
        } else {
            cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH));
        }
        cal.set(Calendar.HOUR_OF_DAY, 23);
        cal.set(Calendar.MINUTE, 59);
        cal.set(Calendar.SECOND, 59);
        return cal.getTimeInMillis();
    }

    public LiveData<List<com.example.spendtracker.domain.model.DailyTrend>> getDailyTrends(String type) {
        return Transformations.switchMap(currentMonthStart, start -> 
            Transformations.switchMap(granularity, g -> {
                Calendar cal = Calendar.getInstance();
                cal.setTimeInMillis(start);
                
                // Requirement: Previous 2 months + Current month (or weeks/years)
                if (g == Granularity.ANNUALLY) {
                    cal.add(Calendar.YEAR, -2);
                    long rangeStart = cal.getTimeInMillis();
                    cal.setTimeInMillis(start);
                    cal.set(Calendar.MONTH, 11);
                    cal.set(Calendar.DAY_OF_MONTH, 31);
                    cal.set(Calendar.HOUR_OF_DAY, 23);
                    cal.set(Calendar.MINUTE, 59);
                    cal.set(Calendar.SECOND, 59);
                    return repository.getAnnuallyTotals(rangeStart, cal.getTimeInMillis(), type);
                } else if (g == Granularity.MONTHLY) {
                    cal.add(Calendar.MONTH, -2);
                    long rangeStart = cal.getTimeInMillis();
                    cal.setTimeInMillis(start);
                    cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH));
                    cal.set(Calendar.HOUR_OF_DAY, 23);
                    cal.set(Calendar.MINUTE, 59);
                    cal.set(Calendar.SECOND, 59);
                    return repository.getMonthlyTotals(rangeStart, cal.getTimeInMillis(), type);
                } else if (g == Granularity.WEEKLY) {
                    cal.add(Calendar.WEEK_OF_YEAR, -2);
                    long rangeStart = cal.getTimeInMillis();
                    cal.setTimeInMillis(start);
                    cal.add(Calendar.DAY_OF_YEAR, 6);
                    cal.set(Calendar.HOUR_OF_DAY, 23);
                    cal.set(Calendar.MINUTE, 59);
                    cal.set(Calendar.SECOND, 59);
                    return repository.getWeeklyTotals(rangeStart, cal.getTimeInMillis(), type);
                } else {
                    cal.add(Calendar.DAY_OF_MONTH, -30);
                    return repository.getDailyTotals(cal.getTimeInMillis(), System.currentTimeMillis(), type);
                }
            })
        );
    }
}
