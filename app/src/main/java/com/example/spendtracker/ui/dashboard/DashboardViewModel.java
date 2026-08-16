package com.example.spendtracker.ui.dashboard;

import android.content.Context;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;
import com.example.spendtracker.domain.model.Summary;
import com.example.spendtracker.domain.model.Transaction;
import com.example.spendtracker.domain.repository.SecurityRepository;
import com.example.spendtracker.domain.repository.TransactionRepository;
import com.example.prediction.domain.service.PredictionService;
import dagger.hilt.android.lifecycle.HiltViewModel;
import dagger.hilt.android.qualifiers.ApplicationContext;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.inject.Inject;

@HiltViewModel
public class DashboardViewModel extends ViewModel {

    private final TransactionRepository repository;
    private final SecurityRepository securityRepository;
    private final MutableLiveData<DateRange> dateRange = new MutableLiveData<>();
    private final MutableLiveData<FilterType> currentFilter = new MutableLiveData<>(FilterType.DAILY);
    private final PredictionService predictionService;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private long calendarViewMonthStart;
    private final SimpleDateFormat monthYearFormat = new SimpleDateFormat("MMMM yyyy", Locale.getDefault());
    private final SimpleDateFormat dayFormat = new SimpleDateFormat("dd MMMM yyyy", Locale.getDefault());

    public static class DateRange {
        public final long start;
        public final long end;
        public final String label;

        public DateRange(long start, long end, String label) {
            this.start = start;
            this.end = end;
            this.label = label;
        }
    }

    @Inject
    public DashboardViewModel(TransactionRepository repository, SecurityRepository securityRepository, @ApplicationContext Context context) {
        this.repository = repository;
        this.securityRepository = securityRepository;
        this.predictionService = new PredictionService(context);
        calendarViewMonthStart = getStartOfMonth(System.currentTimeMillis());
        setFilter(FilterType.DAILY);
    }

    public enum FilterType { DAILY, MONTHLY, TOTAL, CALENDAR, NOTE }

    public void setFilter(FilterType type) {
        currentFilter.setValue(type);
        if (type == FilterType.DAILY || type == FilterType.MONTHLY || type == FilterType.TOTAL || type == FilterType.CALENDAR) {
            long start = type == FilterType.CALENDAR ? calendarViewMonthStart : getStartOfMonth(System.currentTimeMillis());
            setMonthFilter(start);
        }
    }

    public void moveNext() {
        DateRange current = dateRange.getValue();
        if (current == null) return;
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(current.start);
        
        cal.add(Calendar.MONTH, 1);
        setMonthFilter(cal.getTimeInMillis());
    }

    public void movePrev() {
        DateRange current = dateRange.getValue();
        if (current == null) return;
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(current.start);
        
        cal.add(Calendar.MONTH, -1);
        setMonthFilter(cal.getTimeInMillis());
    }

    public LiveData<Boolean> isPrivacyModeEnabled() {
        return securityRepository.isPrivacyModeEnabled();
    }

    public void setPrivacyModeEnabled(boolean enabled) {
        securityRepository.setPrivacyModeEnabled(enabled);
    }

    public String formatAmount(double amount) {
        if (Boolean.TRUE.equals(isPrivacyModeEnabled().getValue())) return "***";
        return String.format(Locale.getDefault(), "₹ %.2f", amount);
    }

    private void setMonthFilter(long timestamp) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(timestamp);
        cal.set(Calendar.DAY_OF_MONTH, 1);
        long start = getStartOfDay(cal.getTimeInMillis());
        cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH));
        long end = getStartOfDay(cal.getTimeInMillis()) + 86399999;
        dateRange.setValue(new DateRange(start, end, monthYearFormat.format(new Date(start))));
    }

    public void setCalendarFilter(long timestamp, String label) {
        long start = getStartOfDay(timestamp);
        dateRange.setValue(new DateRange(start, start + 86399999, label));
    }

    public LiveData<DateRange> getDateRange() { return dateRange; }

    public LiveData<List<Transaction>> getTransactions() {
        return Transformations.switchMap(dateRange, range -> {
            if (range.start == 0 && range.end == Long.MAX_VALUE) {
                return repository.getTransactions();
            } else {
                return repository.getTransactionsInRange(range.start, range.end);
            }
        });
    }

    public LiveData<List<GroupedTransactionAdapter.ListItem>> getGroupedTransactions() {
        return Transformations.map(getTransactions(), transactions -> {
            List<GroupedTransactionAdapter.ListItem> items = new ArrayList<>();
            if (transactions == null || transactions.isEmpty()) return items;

            Map<Long, List<Transaction>> grouped = new LinkedHashMap<>();
            for (Transaction t : transactions) {
                long tDay = getStartOfDay(t.getDate());
                if (!grouped.containsKey(tDay)) {
                    grouped.put(tDay, new ArrayList<>());
                }
                grouped.get(tDay).add(t);
            }

            for (Map.Entry<Long, List<Transaction>> entry : grouped.entrySet()) {
                double income = 0;
                double expense = 0;
                for (Transaction t : entry.getValue()) {
                    if ("INCOME".equals(t.getType())) income += t.getAmount();
                    else expense += t.getAmount();
                }
                items.add(new GroupedTransactionAdapter.HeaderItem(entry.getKey(), income, expense));
                for (Transaction t : entry.getValue()) {
                    items.add(new GroupedTransactionAdapter.TransactionItem(t));
                }
            }
            return items;
        });
    }

    public LiveData<List<MonthlySummaryAdapter.MonthSummary>> getMonthlySummaries() {
        return Transformations.map(repository.getTransactions(), transactions -> {
            List<MonthlySummaryAdapter.MonthSummary> summaries = new ArrayList<>();
            if (transactions == null || transactions.isEmpty()) return summaries;

            Map<Long, List<Transaction>> monthGroups = new LinkedHashMap<>();
            for (Transaction t : transactions) {
                long monthStart = getStartOfMonth(t.getDate());
                if (!monthGroups.containsKey(monthStart)) {
                    monthGroups.put(monthStart, new ArrayList<>());
                }
                monthGroups.get(monthStart).add(t);
            }

            for (Map.Entry<Long, List<Transaction>> entry : monthGroups.entrySet()) {
                double income = 0;
                double expense = 0;
                for (Transaction t : entry.getValue()) {
                    if ("INCOME".equals(t.getType())) income += t.getAmount();
                    else expense += t.getAmount();
                }
                List<MonthlySummaryAdapter.WeeklySummary> weeks = calculateWeeklySummaries(entry.getValue());
                summaries.add(new MonthlySummaryAdapter.MonthSummary(entry.getKey(), income, expense, weeks));
            }
            return summaries;
        });
    }

    private List<MonthlySummaryAdapter.WeeklySummary> calculateWeeklySummaries(List<Transaction> monthTransactions) {
        List<MonthlySummaryAdapter.WeeklySummary> weeks = new ArrayList<>();
        if (monthTransactions == null || monthTransactions.isEmpty()) return weeks;

        monthTransactions.sort((a, b) -> Long.compare(b.getDate(), a.getDate()));

        Calendar cal = Calendar.getInstance();
        Map<Integer, List<Transaction>> weekGroups = new LinkedHashMap<>();
        for (Transaction t : monthTransactions) {
            cal.setTimeInMillis(t.getDate());
            int week = cal.get(Calendar.WEEK_OF_MONTH);
            if (!weekGroups.containsKey(week)) weekGroups.put(week, new ArrayList<>());
            weekGroups.get(week).add(t);
        }

        for (Map.Entry<Integer, List<Transaction>> entry : weekGroups.entrySet()) {
            double wIncome = 0, wExpense = 0;
            long min = Long.MAX_VALUE, max = 0;
            for (Transaction t : entry.getValue()) {
                if ("INCOME".equals(t.getType())) wIncome += t.getAmount();
                else wExpense += t.getAmount();
                min = Math.min(min, t.getDate());
                max = Math.max(max, t.getDate());
            }
            SimpleDateFormat sdf = new SimpleDateFormat("dd MMM", Locale.getDefault());
            String range = sdf.format(new Date(min)) + " - " + sdf.format(new Date(max));
            weeks.add(new MonthlySummaryAdapter.WeeklySummary(range, wIncome, wExpense));
        }
        return weeks;
    }

    private long getStartOfMonth(long timestamp) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(timestamp);
        cal.set(Calendar.DAY_OF_MONTH, 1);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }

    public LiveData<List<CalendarAdapter.CalendarDay>> getCalendarDays() {
        return Transformations.map(repository.getTransactionsInRange(dateRange.getValue().start, dateRange.getValue().end), transactions -> {
            List<CalendarAdapter.CalendarDay> days = new ArrayList<>();
            Calendar cal = Calendar.getInstance();
            cal.setTimeInMillis(dateRange.getValue().start);
            
            cal.set(Calendar.DAY_OF_MONTH, 1);
            int firstDayOfWeek = cal.get(Calendar.DAY_OF_WEEK);
            for (int i = 1; i < firstDayOfWeek; i++) {
                days.add(new CalendarAdapter.CalendarDay(0, 0, 0, false, 0));
            }

            int daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH);
            for (int i = 1; i <= daysInMonth; i++) {
                cal.set(Calendar.DAY_OF_MONTH, i);
                long start = getStartOfDay(cal.getTimeInMillis());
                long end = start + 86399999;
                double dIncome = 0, dExpense = 0;
                if (transactions != null) {
                    for (Transaction t : transactions) {
                        if (t.getDate() >= start && t.getDate() <= end) {
                            if ("INCOME".equals(t.getType())) dIncome += t.getAmount();
                            else dExpense += t.getAmount();
                        }
                    }
                }
                days.add(new CalendarAdapter.CalendarDay(i, dIncome, dExpense, true, start));
            }
            return days;
        });
    }

    private long getStartOfDay(long timestamp) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(timestamp);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }

    public LiveData<Summary> getSummary() {
        return Transformations.switchMap(dateRange, range -> 
            repository.getSummary(range.start, range.end)
        );
    }

    public LiveData<List<String>> getCategories() {
        return repository.getCategories();
    }

    public void updateTransactionCategory(Transaction transaction, String newCategory) {
        executor.execute(() -> {
            transaction.setCategory(newCategory);
            repository.updateTransaction(transaction);
        });
    }

    public void resetModel() {
        executor.execute(() -> {
            predictionService.resetModel();
            triggerRefinementPass();
        });
    }

    public void triggerRefinementPass() {
        executor.execute(() -> {
            List<Transaction> transactions = repository.getTransactionsSync();
            if (transactions == null || transactions.isEmpty()) return;

            for (Transaction t : transactions) {
                if ("PENDING".equals(t.getCategory()) || t.getCategory().isEmpty()) {
                    com.example.prediction.domain.model.PredictionTransaction pt = 
                        new com.example.prediction.domain.model.PredictionTransaction(
                            t.getReceiverName(), t.getUpiId(), t.getAmount(), t.getType(), t.getDate());
                    String predicted = predictionService.predict(pt).getCategory();
                    if (!predicted.equals(t.getCategory())) {
                        t.setCategory(predicted);
                        repository.updateTransaction(t);
                    }
                }
            }
        });
    }

    public LiveData<TotalPageData> getTotalPageData() {
        return Transformations.map(getSummary(), summary -> {
            return new TotalPageData(0, summary.getTotalExpense(), 0, summary.getTotalIncome());
        });
    }

    public static class TotalPageData {
        public final int comparedPercent;
        public final double accountExpenses;
        public final double cardExpenses;
        public final double transfers;

        public TotalPageData(int comparedPercent, double accountExpenses, double cardExpenses, double transfers) {
            this.comparedPercent = comparedPercent;
            this.accountExpenses = accountExpenses;
            this.cardExpenses = cardExpenses;
            this.transfers = transfers;
        }
    }
}
