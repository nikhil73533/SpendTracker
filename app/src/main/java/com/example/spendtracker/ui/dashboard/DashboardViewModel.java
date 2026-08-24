package com.example.spendtracker.ui.dashboard;

import android.content.Context;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;
import com.example.spendtracker.domain.model.Summary;
import com.example.spendtracker.domain.model.Transaction;
import com.example.spendtracker.domain.repository.SecurityRepository;
import com.example.spendtracker.domain.repository.TransactionRepository;
import com.example.prediction.domain.service.IncrementalPredictionService;
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
    private final MutableLiveData<Integer> selectedTab = new MutableLiveData<>(0);
    private final MutableLiveData<String> searchQuery = new MutableLiveData<>("");
    private final IncrementalPredictionService predictionService;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private long calendarViewMonthStart;
    /** True when the user tapped a specific calendar day; prevents the tab-change from resetting to a monthly filter. */
    private boolean calendarDaySelected = false;
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
        this.predictionService = new IncrementalPredictionService(context);
        calendarViewMonthStart = getStartOfMonth(System.currentTimeMillis());
        setFilter(FilterType.DAILY);
    }

    public enum FilterType { DAILY, MONTHLY, TOTAL, CALENDAR, TRANSACTION_GROUP }

    public void setFilter(FilterType type) {
        currentFilter.setValue(type);
        if (type == FilterType.TRANSACTION_GROUP) return;

        DateRange current = dateRange.getValue();
        long start;
        if (current != null && current.start != 0) {
            start = getStartOfMonth(current.start);
        } else {
            start = calendarViewMonthStart;
        }
        
        if (type == FilterType.DAILY || type == FilterType.MONTHLY || type == FilterType.TOTAL || type == FilterType.CALENDAR) {
            setMonthFilter(start);
        }
    }

    public void selectTab(int index) {
        selectedTab.setValue(index);
    }

    public LiveData<Integer> getSelectedTab() {
        return selectedTab;
    }

    public void setSearchQuery(String query) {
        searchQuery.setValue(query);
    }

    public LiveData<String> getSearchQuery() {
        return searchQuery;
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
        return String.format(Locale.getDefault(), "₹ %.0f", amount);
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
        calendarDaySelected = true;
        long start = getStartOfDay(timestamp);
        dateRange.setValue(new DateRange(start, start + 86399999, label));
    }

    /** @return {@code true} when a specific calendar day was selected and the daily tab should preserve its filter. */
    public boolean isCalendarDaySelected() {
        return calendarDaySelected;
    }

    /** Clears the calendar-day-selected guard so normal tab switching resumes month-based filtering. */
    public void clearCalendarDaySelected() {
        calendarDaySelected = false;
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
                if (!grouped.containsKey(tDay)) grouped.put(tDay, new ArrayList<>());
                grouped.get(tDay).add(t);
            }

            for (Map.Entry<Long, List<Transaction>> entry : grouped.entrySet()) {
                double income = 0, expense = 0;
                for (Transaction t : entry.getValue()) {
                    if ("INCOME".equals(t.getType())) income += t.getAmount();
                    else if ("EXPENSE".equals(t.getType())) expense += t.getAmount();
                    // TRANSFER: not counted in income or expense header
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
                if (!monthGroups.containsKey(monthStart)) monthGroups.put(monthStart, new ArrayList<>());
                monthGroups.get(monthStart).add(t);
            }

            for (Map.Entry<Long, List<Transaction>> entry : monthGroups.entrySet()) {
                double income = 0, expense = 0;
                for (Transaction t : entry.getValue()) {
                    if ("INCOME".equals(t.getType())) income += t.getAmount();
                    else if ("EXPENSE".equals(t.getType())) expense += t.getAmount();
                    // TRANSFER: excluded from month income/expense
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
                else if ("EXPENSE".equals(t.getType())) wExpense += t.getAmount();
                // TRANSFER: not counted
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
        return Transformations.switchMap(dateRange, range ->
            Transformations.map(repository.getTransactionsInRange(range.start, range.end), transactions -> {
                List<CalendarAdapter.CalendarDay> days = new ArrayList<>();
                Calendar cal = Calendar.getInstance();
                cal.setTimeInMillis(range.start);

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
                                else if ("EXPENSE".equals(t.getType())) dExpense += t.getAmount();
                                // TRANSFER: not shown in calendar dots
                            }
                        }
                    }
                    days.add(new CalendarAdapter.CalendarDay(i, dIncome, dExpense, true, start));
                }
                return days;
            })
        );
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
            String updatedType = transaction.getType();
            if ("Transfer".equalsIgnoreCase(newCategory)) {
                updatedType = "TRANSFER";
            } else if ("TRANSFER".equals(transaction.getType())) {
                // If it was a transfer and changed to something else, default to EXPENSE
                // (or you could try to guess based on context, but EXPENSE is safer for most corrections)
                updatedType = "EXPENSE";
            }

            // Create a new Transaction instance to ensure DiffUtil detects the change
            Transaction updated = new Transaction(
                transaction.getId(),
                transaction.getAmount(),
                newCategory,
                transaction.getDescription(),
                updatedType,
                transaction.getDate(),
                transaction.getSource(),
                transaction.getSender(),
                transaction.getUpiId(),
                transaction.getReceiverName(),
                transaction.getBankName(),
                transaction.getSourceType()
            );
            repository.updateTransaction(updated);

            // Incremental learning: user correction teaches the model
            if (!"TRANSFER".equalsIgnoreCase(updatedType)) {
                com.example.prediction.domain.model.PredictionTransaction pt =
                    new com.example.prediction.domain.model.PredictionTransaction(
                        transaction.getReceiverName(),
                        transaction.getUpiId(),
                        transaction.getAmount(),
                        transaction.getType(),
                        transaction.getDate()
                    );
                predictionService.learn(pt, newCategory);
            }
        });
    }

    public void resetModel() {
        executor.execute(() -> predictionService.resetAllData());
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
                        // Create new instance for DiffUtil reliability
                        Transaction updated = new Transaction(
                            t.getId(), t.getAmount(), predicted, t.getDescription(),
                            t.getType(), t.getDate(), t.getSource(), t.getSender(),
                            t.getUpiId(), t.getReceiverName(), t.getBankName(), t.getSourceType()
                        );
                        repository.updateTransaction(updated);
                    }
                }
            }
        });
    }

    public LiveData<TotalPageData> getTotalPageData() {
        return Transformations.switchMap(dateRange, range -> {
            Calendar cal = Calendar.getInstance();
            cal.setTimeInMillis(range.start);
            cal.add(Calendar.MONTH, -1);
            long lastStart = getStartOfMonth(cal.getTimeInMillis());
            cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH));
            long lastEnd = getStartOfDay(cal.getTimeInMillis()) + 86399999;

            LiveData<Summary> currentSummaryLive = repository.getSummary(range.start, range.end);
            LiveData<Summary> lastSummaryLive    = repository.getSummary(lastStart, lastEnd);
            LiveData<Double>  cardExpLive        = repository.getTotalCardExpense(range.start, range.end);
            LiveData<Double>  acctExpLive        = repository.getTotalAccountExpense(range.start, range.end);
            LiveData<Double>  transferLive       = repository.getTotalTransfer(range.start, range.end);

            MediatorLiveData<TotalPageData> mediator = new MediatorLiveData<>();

            Runnable update = () -> {
                Summary current = currentSummaryLive.getValue();
                Summary last    = lastSummaryLive.getValue();
                if (current != null) {
                    double currentExp = current.getTotalExpense();
                    double lastExp    = (last != null) ? last.getTotalExpense() : 0;
                    int percent = 0;
                    if (lastExp > 0) percent = (int) (((currentExp - lastExp) / lastExp) * 100);

                    double cardExp  = cardExpLive.getValue()  != null ? cardExpLive.getValue()  : 0;
                    double acctExp  = acctExpLive.getValue()  != null ? acctExpLive.getValue()  : 0;
                    double transfer = transferLive.getValue() != null ? transferLive.getValue() : 0;
                    double income   = current.getTotalIncome();

                    mediator.setValue(new TotalPageData(percent, acctExp, cardExp, transfer, income));
                }
            };

            mediator.addSource(currentSummaryLive, v -> update.run());
            mediator.addSource(lastSummaryLive,    v -> update.run());
            mediator.addSource(cardExpLive,        v -> update.run());
            mediator.addSource(acctExpLive,        v -> update.run());
            mediator.addSource(transferLive,       v -> update.run());

            return mediator;
        });
    }

    public LiveData<List<com.example.spendtracker.data.local.dao.TransactionDao.CategorySum>> getBankTotals() {
        return Transformations.switchMap(dateRange, range ->
            repository.getBankTotals(range.start, range.end, "EXPENSE")
        );
    }

    /** Updates only the transaction type and persists to DB. */
    public void updateTransactionType(Transaction transaction, String newType) {
        executor.execute(() -> {
            String newCategory = "TRANSFER".equals(newType) ? "Transfer" : transaction.getCategory();
            Transaction updated = new Transaction(
                transaction.getId(), transaction.getAmount(), newCategory,
                transaction.getDescription(), newType, transaction.getDate(),
                transaction.getSource(), transaction.getSender(), transaction.getUpiId(),
                transaction.getReceiverName(), transaction.getBankName(), transaction.getSourceType()
            );
            repository.updateTransaction(updated);
        });
    }

    public static class TotalPageData {
        public final int comparedPercent;
        public final double accountExpenses;
        public final double cardExpenses;
        public final double transfers;
        public final double income;

        public TotalPageData(int comparedPercent, double accountExpenses, double cardExpenses, double transfers, double income) {
            this.comparedPercent = comparedPercent;
            this.accountExpenses = accountExpenses;
            this.cardExpenses = cardExpenses;
            this.transfers = transfers;
            this.income = income;
        }
    }
}
