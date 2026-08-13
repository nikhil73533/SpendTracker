package com.example.spendtracker.ui.dashboard;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;
import com.example.spendtracker.domain.model.Summary;
import com.example.spendtracker.domain.model.Transaction;
import com.example.spendtracker.domain.repository.TransactionRepository;
import com.example.prediction.domain.service.PredictionService;
import com.example.prediction.domain.model.PredictionTransaction;
import android.content.Context;
import dagger.hilt.android.lifecycle.HiltViewModel;
import dagger.hilt.android.qualifiers.ApplicationContext;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.text.SimpleDateFormat;
import javax.inject.Inject;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import com.example.prediction.domain.service.KNNPredictor;

@HiltViewModel
public class DashboardViewModel extends ViewModel {
    private final TransactionRepository repository;
    private final MutableLiveData<DateRange> dateRange = new MutableLiveData<>();
    private final MutableLiveData<FilterType> currentFilter = new MutableLiveData<>(FilterType.MONTHLY);
    private final PredictionService predictionService;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private long calendarViewMonthStart = 0;

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
    public DashboardViewModel(TransactionRepository repository, @ApplicationContext Context context) {
        this.repository = repository;
        this.predictionService = new PredictionService(context);
        calendarViewMonthStart = getStartOfMonth(System.currentTimeMillis());
        setFilter(FilterType.DAILY);
    }

    public enum FilterType { DAILY, MONTHLY, TOTAL, CALENDAR, NOTE }

    public void setFilter(FilterType type) {
        currentFilter.setValue(type);
        if (type == FilterType.CALENDAR || type == FilterType.MONTHLY) {
            if (calendarViewMonthStart == 0) calendarViewMonthStart = getStartOfMonth(System.currentTimeMillis());
            setMonthFilter(calendarViewMonthStart);
            return;
        }

        Calendar cal = Calendar.getInstance();
        switch (type) {
            case DAILY:
                cal.set(Calendar.DAY_OF_MONTH, 1);
                cal.set(Calendar.HOUR_OF_DAY, 0);
                cal.set(Calendar.MINUTE, 0);
                cal.set(Calendar.SECOND, 0);
                long start = cal.getTimeInMillis();
                cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH));
                cal.set(Calendar.HOUR_OF_DAY, 23);
                cal.set(Calendar.MINUTE, 59);
                cal.set(Calendar.SECOND, 59);
                dateRange.setValue(new DateRange(start, cal.getTimeInMillis(), "Current Month"));
                break;
            case TOTAL:
                dateRange.setValue(new DateRange(0, Long.MAX_VALUE, "All Time"));
                break;
        }
    }

    public void moveNext() {
        FilterType filter = currentFilter.getValue();
        if (filter == FilterType.TOTAL) return;

        Calendar cal = Calendar.getInstance();
        DateRange current = dateRange.getValue();
        if (current != null) cal.setTimeInMillis(current.start);

        if (filter == FilterType.DAILY) {
            cal.add(Calendar.DAY_OF_MONTH, 1);
            setCalendarFilter(cal.getTimeInMillis(), "Selected Date");
        } else if (filter == FilterType.MONTHLY || filter == FilterType.CALENDAR) {
            cal.add(Calendar.MONTH, 1);
            calendarViewMonthStart = cal.getTimeInMillis();
            setMonthFilter(calendarViewMonthStart);
        }
    }

    public void movePrev() {
        FilterType filter = currentFilter.getValue();
        if (filter == FilterType.TOTAL) return;

        Calendar cal = Calendar.getInstance();
        DateRange current = dateRange.getValue();
        if (current != null) cal.setTimeInMillis(current.start);

        if (filter == FilterType.DAILY) {
            cal.add(Calendar.DAY_OF_MONTH, -1);
            setCalendarFilter(cal.getTimeInMillis(), "Selected Date");
        } else if (filter == FilterType.MONTHLY || filter == FilterType.CALENDAR) {
            cal.add(Calendar.MONTH, -1);
            calendarViewMonthStart = cal.getTimeInMillis();
            setMonthFilter(calendarViewMonthStart);
        }
    }

    private void setMonthFilter(long timestamp) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(timestamp);
        cal.set(Calendar.DAY_OF_MONTH, 1);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        long start = cal.getTimeInMillis();
        cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH));
        cal.set(Calendar.HOUR_OF_DAY, 23);
        cal.set(Calendar.MINUTE, 59);
        cal.set(Calendar.SECOND, 59);
        
        String label = new SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(new Date(start));
        dateRange.setValue(new DateRange(start, cal.getTimeInMillis(), label));
    }

    public void setCalendarFilter(long timestamp, String label) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(timestamp);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        long start = cal.getTimeInMillis();
        cal.set(Calendar.HOUR_OF_DAY, 23);
        cal.set(Calendar.MINUTE, 59);
        cal.set(Calendar.SECOND, 59);
        
        String displayLabel = new SimpleDateFormat("d MMMM yyyy", Locale.getDefault()).format(new Date(start));
        dateRange.setValue(new DateRange(start, cal.getTimeInMillis(), displayLabel));
    }

    public LiveData<DateRange> getDateRange() {
        return dateRange;
    }

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

            long currentDay = -1;
            List<Transaction> dayTransactions = new ArrayList<>();
            double dayIncome = 0;
            double dayExpense = 0;

            for (Transaction t : transactions) {
                long tDay = getStartOfDay(t.getDate());
                if (tDay != currentDay) {
                    if (currentDay != -1) {
                        items.add(0, new GroupedTransactionAdapter.HeaderItem(currentDay, dayIncome, dayExpense)); // This is tricky with sorting
                    }
                    // Since transactions are sorted DESC by date, we can just add headers as we go
                }
            }
            // Re-thinking: Transactions are sorted DESC.
            // Group by date.
            
            Map<Long, List<Transaction>> groups = new LinkedHashMap<>();
            for (Transaction t : transactions) {
                long day = getStartOfDay(t.getDate());
                if (!groups.containsKey(day)) {
                    groups.put(day, new ArrayList<>());
                }
                List<Transaction> group = groups.get(day);
                if (group != null) {
                    group.add(t);
                }
            }

            for (Map.Entry<Long, List<Transaction>> entry : groups.entrySet()) {
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
            android.util.Log.d("DashboardVM", "getMonthlySummaries: " + (transactions != null ? transactions.size() : 0) + " transactions");
            List<MonthlySummaryAdapter.MonthSummary> summaries = new ArrayList<>();
            if (transactions == null || transactions.isEmpty()) return summaries;

            // Group by month
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

                // Group by week within month
                List<MonthlySummaryAdapter.WeeklySummary> weeks = calculateWeeklySummaries(entry.getValue());
                summaries.add(new MonthlySummaryAdapter.MonthSummary(entry.getKey(), income, expense, weeks));
            }
            // Sort by latest month
            summaries.sort((a, b) -> Long.compare(b.monthTimestamp, a.monthTimestamp));
            return summaries;
        });
    }

    private List<MonthlySummaryAdapter.WeeklySummary> calculateWeeklySummaries(List<Transaction> monthTransactions) {
        List<MonthlySummaryAdapter.WeeklySummary> weeklySummaries = new ArrayList<>();
        if (monthTransactions == null || monthTransactions.isEmpty()) return weeklySummaries;

        // Sort by date DESC
        monthTransactions.sort((a, b) -> Long.compare(b.getDate(), a.getDate()));

        Map<String, double[]> weekMap = new LinkedHashMap<>(); // Key: "start ~ end", Value: [income, expense]
        SimpleDateFormat sdf = new SimpleDateFormat("dd MMM", Locale.getDefault());

        for (Transaction t : monthTransactions) {
            Calendar cal = Calendar.getInstance();
            cal.setTimeInMillis(t.getDate());
            
            // Determine week boundaries (Monday to Sunday)
            cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
            long weekStart = cal.getTimeInMillis();
            cal.add(Calendar.DAY_OF_WEEK, 6);
            long weekEnd = cal.getTimeInMillis();
            
            String key = sdf.format(new Date(weekStart)) + " – " + sdf.format(new Date(weekEnd));
            if (!weekMap.containsKey(key)) {
                weekMap.put(key, new double[2]);
            }
            double[] totals = weekMap.get(key);
            if ("INCOME".equals(t.getType())) totals[0] += t.getAmount();
            else totals[1] += t.getAmount();
        }

        for (Map.Entry<String, double[]> entry : weekMap.entrySet()) {
            weeklySummaries.add(new MonthlySummaryAdapter.WeeklySummary(entry.getKey(), entry.getValue()[0], entry.getValue()[1]));
        }
        return weeklySummaries;
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
        return Transformations.switchMap(dateRange, range -> {
            if (range == null || range.start == 0) return new MutableLiveData<>(new ArrayList<>());
            
            Calendar cal = Calendar.getInstance();
            cal.setTimeInMillis(range.start);
            cal.set(Calendar.DAY_OF_MONTH, 1);
            int month = cal.get(Calendar.MONTH);
            
            // First day of grid
            int firstDayOfWeek = cal.get(Calendar.DAY_OF_WEEK) - 1; 
            cal.add(Calendar.DAY_OF_MONTH, -firstDayOfWeek);
            long gridStart = getStartOfDay(cal.getTimeInMillis());
            
            Calendar endCal = Calendar.getInstance();
            endCal.setTimeInMillis(gridStart);
            endCal.add(Calendar.DAY_OF_MONTH, 41);
            long gridEnd = getStartOfDay(endCal.getTimeInMillis()) + (24 * 60 * 60 * 1000) - 1;

            return Transformations.map(repository.getTransactionsInRange(gridStart, gridEnd), transactions -> {
                android.util.Log.d("DashboardVM", "getCalendarDays: " + (transactions != null ? transactions.size() : 0) + " transactions for range");
                List<CalendarAdapter.CalendarDay> days = new ArrayList<>();
                Calendar gridCal = Calendar.getInstance();
                gridCal.setTimeInMillis(gridStart);

                for (int i = 0; i < 42; i++) {
                    long dayStart = getStartOfDay(gridCal.getTimeInMillis());
                    long dayEnd = dayStart + (24 * 60 * 60 * 1000) - 1;

                    double income = 0;
                    double expense = 0;
                    if (transactions != null) {
                        for (Transaction t : transactions) {
                            if (t.getDate() >= dayStart && t.getDate() <= dayEnd) {
                                if ("INCOME".equals(t.getType())) income += t.getAmount();
                                else expense += t.getAmount();
                            }
                        }
                    }

                    days.add(new CalendarAdapter.CalendarDay(
                        gridCal.get(Calendar.DAY_OF_MONTH),
                        income,
                        expense,
                        gridCal.get(Calendar.MONTH) == month,
                        dayStart
                    ));
                    gridCal.add(Calendar.DAY_OF_MONTH, 1);
                }
                return days;
            });
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
        Transaction updated = new Transaction(
            transaction.getId(),
            transaction.getAmount(),
            newCategory,
            transaction.getDescription(),
            transaction.getType(),
            transaction.getDate(),
            transaction.getSource(),
            transaction.getSender(),
            transaction.getUpiId(),
            transaction.getReceiverName(),
            transaction.getBankName(),
            transaction.getSourceType()
        );
        repository.updateTransaction(updated);

        // Learning Integration
        PredictionTransaction pt = new PredictionTransaction(
            transaction.getReceiverName(),
            transaction.getUpiId(),
            transaction.getAmount(),
            transaction.getType(),
            transaction.getDate()
        );
        predictionService.learn(pt, newCategory);

        // Trigger Refinement Pass
        triggerRefinementPass();
    }

    public void resetModel() {
        executor.execute(() -> {
            predictionService.resetModel();
        });
    }

    private void triggerRefinementPass() {
        executor.execute(() -> {
            List<Transaction> transactions = repository.getTransactionsSync();
            if (transactions == null) return;

            List<Transaction> toRefine = new ArrayList<>();
            List<PredictionTransaction> pts = new ArrayList<>();

            for (Transaction t : transactions) {
                if ("Uncategorized".equals(t.getCategory()) || "Other".equals(t.getCategory())) {
                    toRefine.add(t);
                    pts.add(new PredictionTransaction(
                        t.getReceiverName(),
                        t.getUpiId(),
                        t.getAmount(),
                        t.getType(),
                        t.getDate()
                    ));
                }
            }

            if (toRefine.isEmpty()) return;

            List<com.example.prediction.domain.service.KNNPredictor.PredictionResult> results = predictionService.batchPredict(pts);
            for (int i = 0; i < toRefine.size(); i++) {
                com.example.prediction.domain.service.KNNPredictor.PredictionResult res = results.get(i);
                // Lower threshold to 5% (0.05) for more proactive updates
                if (res != null && res.getConfidence() > 0.05) {
                    Transaction old = toRefine.get(i);
                    Transaction updated = new Transaction(
                        old.getId(),
                        old.getAmount(),
                        res.getCategory(),
                        old.getDescription(),
                        old.getType(),
                        old.getDate(),
                        old.getSource(),
                        old.getSender(),
                        old.getUpiId(),
                        old.getReceiverName(),
                        old.getBankName(),
                        old.getSourceType()
                    );
                    repository.updateTransaction(updated);
                }
            }
        });
    }

    public LiveData<TotalPageData> getTotalPageData() {
        return Transformations.switchMap(dateRange, range -> {
            // We need this month and last month
            Calendar cal = Calendar.getInstance();
            cal.setTimeInMillis(range.start);
            long thisMonthStart = getStartOfMonth(cal.getTimeInMillis());
            cal.add(Calendar.MONTH, -1);
            long lastMonthStart = getStartOfMonth(cal.getTimeInMillis());
            cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH));
            long lastMonthEnd = cal.getTimeInMillis() + (24*60*60*1000) - 1;

            return Transformations.switchMap(repository.getTransactionsInRange(lastMonthStart, Long.MAX_VALUE), allTransactions -> {
                MutableLiveData<TotalPageData> data = new MutableLiveData<>();
                
                double thisMonthExpenses = 0;
                double lastMonthExpenses = 0;
                double accountExpenses = 0;
                double cardExpenses = 0;
                double transfers = 0;

                long thisMonthEnd = range.end;

                for (Transaction t : allTransactions) {
                    if (t.getDate() >= thisMonthStart && t.getDate() <= thisMonthEnd) {
                        if ("EXPENSE".equals(t.getType())) {
                            thisMonthExpenses += t.getAmount();
                            if ("Account".equalsIgnoreCase(t.getSourceType())) {
                                accountExpenses += t.getAmount();
                            } else if ("Credit Card".equalsIgnoreCase(t.getSourceType())) {
                                cardExpenses += t.getAmount();
                            }
                        }
                        if ("Transfer".equalsIgnoreCase(t.getCategory())) {
                            transfers += t.getAmount();
                        }
                    } else if (t.getDate() >= lastMonthStart && t.getDate() <= lastMonthEnd) {
                        if ("EXPENSE".equals(t.getType())) {
                            lastMonthExpenses += t.getAmount();
                        }
                    }
                }

                int comparedPercent = 0;
                if (lastMonthExpenses > 0) {
                    comparedPercent = (int) ((thisMonthExpenses / lastMonthExpenses) * 100);
                }

                data.setValue(new TotalPageData(comparedPercent, accountExpenses, cardExpenses, transfers));
                return data;
            });
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
