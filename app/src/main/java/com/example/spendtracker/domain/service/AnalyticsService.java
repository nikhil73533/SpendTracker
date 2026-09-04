package com.example.spendtracker.domain.service;

import com.example.spendtracker.data.local.dao.TransactionDao;
import com.example.spendtracker.di.MainDatabase;
import com.example.spendtracker.domain.model.analytics.AnalyticsSummary;
import com.example.spendtracker.domain.model.analytics.CategoryAnalytics;
import com.example.spendtracker.domain.model.analytics.RollingAverage;
import com.example.spendtracker.domain.model.analytics.TimeSeriesPoint;
import com.example.spendtracker.domain.model.analytics.BehaviorAnalytics;
import com.example.spendtracker.domain.model.analytics.ForecastResult;
import com.example.spendtracker.domain.model.analytics.MonthlyComparison;
import com.example.spendtracker.domain.model.analytics.TransactionVolume;
import com.example.spendtracker.data.local.entity.TransactionEntity;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class AnalyticsService {

    private final TransactionDao transactionDao;

    @Inject
    public AnalyticsService(@MainDatabase TransactionDao transactionDao) {
        this.transactionDao = transactionDao;
    }

    public AnalyticsSummary getFinancialOverview(long start, long end) {
        double totalIncome = transactionDao.getTotalIncomeSync(start, end);
        double totalExpense = transactionDao.getTotalExpenseSync(start, end);
        double totalTransfer = transactionDao.getTotalTransferAmountSync(start, end);
        int incomeCount = transactionDao.getIncomeCountSync(start, end);
        int expenseCount = transactionDao.getExpenseCountSync(start, end);
        int transferCount = transactionDao.getTransferCountSync(start, end);
        Double avgIncome = transactionDao.getAverageIncomeSync(start, end);
        Double avgExpense = transactionDao.getAverageExpenseSync(start, end);
        double avgInc = avgIncome != null ? avgIncome : 0.0;
        double avgExp = avgExpense != null ? avgExpense : 0.0;
        return new AnalyticsSummary(totalIncome, totalExpense, totalTransfer,
                incomeCount, expenseCount, transferCount, avgExp, avgInc);
    }

    public TransactionVolume getTransactionVolume(long start, long end, String periodLabel) {
        int totalCount = transactionDao.getTotalCountSync(start, end);
        int expenseCount = transactionDao.getExpenseCountSync(start, end);
        int incomeCount = transactionDao.getIncomeCountSync(start, end);
        int transferCount = transactionDao.getTransferCountSync(start, end);
        double totalExpense = transactionDao.getTotalExpenseSync(start, end);
        double totalIncome = transactionDao.getTotalIncomeSync(start, end);
        double totalTransfer = transactionDao.getTotalTransferAmountSync(start, end);
        return new TransactionVolume(periodLabel, totalCount, expenseCount, incomeCount, transferCount, totalExpense, totalIncome, totalTransfer);
    }

    public List<TransactionEntity> getTopTransactions(long start, long end, int limit) {
        return transactionDao.getTopTransactionsSync(start, end, limit);
    }

    public List<TimeSeriesPoint> getMonthlyTrend(long start, long end, String type) {
        List<TransactionDao.CategorySum> raw = transactionDao.getMonthlyTotalsSync(start, end, type);
        List<TimeSeriesPoint> points = new ArrayList<>();
        for (TransactionDao.CategorySum cs : raw) {
            points.add(new TimeSeriesPoint(cs.category, cs.total));
        }
        return points;
    }

    public List<TimeSeriesPoint> getIncomeExpenseTrend(long start, long end) {
        List<TransactionDao.CategorySum> incomeRaw = transactionDao.getMonthlyTotalsSync(start, end, "INCOME");
        List<TransactionDao.CategorySum> expenseRaw = transactionDao.getMonthlyTotalsSync(start, end, "EXPENSE");
        
        Map<String, double[]> combined = new HashMap<>();
        for (TransactionDao.CategorySum cs : incomeRaw) {
            combined.put(cs.category, new double[]{cs.total, 0.0});
        }
        for (TransactionDao.CategorySum cs : expenseRaw) {
            if (combined.containsKey(cs.category)) {
                combined.get(cs.category)[1] = cs.total;
            } else {
                combined.put(cs.category, new double[]{0.0, cs.total});
            }
        }
        
        List<TimeSeriesPoint> points = new ArrayList<>();
        List<String> sortedKeys = new ArrayList<>(combined.keySet());
        sortedKeys.sort(String::compareTo);
        
        for (String key : sortedKeys) {
            double[] vals = combined.get(key);
            points.add(new TimeSeriesPoint(key, vals[0], vals[1], vals[0] - vals[1]));
        }
        return points;
    }

    public List<CategoryAnalytics> getExpenseCategoryAnalytics(long start, long end) {
        List<TransactionDao.CategorySum> sums = transactionDao.getExpenseCategorySummariesSync(start, end);
        List<CategoryAnalytics> result = new ArrayList<>();
        for (TransactionDao.CategorySum cs : sums) {
            result.add(new CategoryAnalytics(cs.category, cs.total, 0));
        }
        return result;
    }

    public RollingAverage getRollingExpenseAverage(int months) {
        return new RollingAverage(months, 0.0, 0.0, 0.0, 0.0, false); // Simplified placeholder
    }

    public BehaviorAnalytics getDayNightAnalytics(long start, long end) {
        List<TransactionEntity> txns = transactionDao.getActiveNonTransferInRangeSync(start, end);
        int dayCount = 0, nightCount = 0;
        double dayTotal = 0, nightTotal = 0;
        Calendar cal = Calendar.getInstance();

        for (TransactionEntity t : txns) {
            if (!"EXPENSE".equals(t.type)) continue;
            cal.setTimeInMillis(t.date);
            int hour = cal.get(Calendar.HOUR_OF_DAY);
            if (hour >= 6 && hour < 18) {
                dayCount++;
                dayTotal += t.amount;
            } else {
                nightCount++;
                nightTotal += t.amount;
            }
        }

        List<BehaviorAnalytics.Segment> segments = new ArrayList<>();
        segments.add(new BehaviorAnalytics.Segment("Day (6AM-6PM)", dayCount, dayTotal));
        segments.add(new BehaviorAnalytics.Segment("Night (6PM-6AM)", nightCount, nightTotal));
        
        String insight = (nightTotal > dayTotal) ? "You spend more at night." : "Most of your spending happens during the day.";
        return new BehaviorAnalytics("DAY_NIGHT", segments, insight);
    }

    public BehaviorAnalytics getTimeOfDayAnalytics(long start, long end) {
        List<TransactionEntity> txns = transactionDao.getActiveNonTransferInRangeSync(start, end);
        double[] totals = new double[4];
        int[] counts = new int[4];
        Calendar cal = Calendar.getInstance();

        for (TransactionEntity t : txns) {
            if (!"EXPENSE".equals(t.type)) continue;
            cal.setTimeInMillis(t.date);
            int hour = cal.get(Calendar.HOUR_OF_DAY);
            int idx = (hour >= 6 && hour < 12) ? 0 : (hour >= 12 && hour < 17) ? 1 : (hour >= 17 && hour < 21) ? 2 : 3;
            totals[idx] += t.amount;
            counts[idx]++;
        }

        List<BehaviorAnalytics.Segment> segments = new ArrayList<>();
        segments.add(new BehaviorAnalytics.Segment("Morning (6-12)", counts[0], totals[0]));
        segments.add(new BehaviorAnalytics.Segment("Afternoon (12-17)", counts[1], totals[1]));
        segments.add(new BehaviorAnalytics.Segment("Evening (17-21)", counts[2], totals[2]));
        segments.add(new BehaviorAnalytics.Segment("Night (21-6)", counts[3], totals[3]));

        return new BehaviorAnalytics("TIME_OF_DAY", segments, "Time of day spending analysis.");
    }

    public BehaviorAnalytics getDayOfWeekAnalytics(long start, long end) {
        List<TransactionDao.CategorySum> sums = transactionDao.getDayOfWeekTotalsSync(start, end);
        List<TransactionDao.CategorySum> counts = transactionDao.getDayOfWeekCountsSync(start, end);
        
        Map<String, Integer> countMap = new HashMap<>();
        for (TransactionDao.CategorySum c : counts) {
            countMap.put(c.category, (int) c.total);
        }

        String[] days = {"Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"};
        List<BehaviorAnalytics.Segment> segments = new ArrayList<>();

        for (TransactionDao.CategorySum s : sums) {
            int dayIdx = Integer.parseInt(s.category);
            int count = countMap.getOrDefault(s.category, 0);
            segments.add(new BehaviorAnalytics.Segment(days[dayIdx], count, s.total));
        }

        return new BehaviorAnalytics("DAY_OF_WEEK", segments, "Spending by day of the week.");
    }
    
    public BehaviorAnalytics getWeekdayWeekendAnalytics(long start, long end) {
        List<TransactionDao.CategorySum> sums = transactionDao.getDayOfWeekTotalsSync(start, end);
        List<TransactionDao.CategorySum> counts = transactionDao.getDayOfWeekCountsSync(start, end);
        
        double weekdayTotal = 0, weekendTotal = 0;
        int weekdayCount = 0, weekendCount = 0;
        
        Map<String, Integer> countMap = new HashMap<>();
        for (TransactionDao.CategorySum c : counts) countMap.put(c.category, (int) c.total);

        for (TransactionDao.CategorySum s : sums) {
            int dayIdx = Integer.parseInt(s.category);
            int count = countMap.getOrDefault(s.category, 0);
            if (dayIdx == 0 || dayIdx == 6) {
                weekendTotal += s.total;
                weekendCount += count;
            } else {
                weekdayTotal += s.total;
                weekdayCount += count;
            }
        }
        
        List<BehaviorAnalytics.Segment> segments = new ArrayList<>();
        segments.add(new BehaviorAnalytics.Segment("Weekday", weekdayCount, weekdayTotal));
        segments.add(new BehaviorAnalytics.Segment("Weekend", weekendCount, weekendTotal));
        return new BehaviorAnalytics("WEEKDAY_WEEKEND", segments, "Weekday vs Weekend spending.");
    }
    
    public ForecastResult getSpendingVelocity(long start, long end) {
        double totalExpense = transactionDao.getTotalExpenseSync(start, end);
        long durationMs = end - start;
        int days = (int) (durationMs / (1000 * 60 * 60 * 24));
        if (days <= 0) days = 1;
        return new ForecastResult(totalExpense, days, 30, 0.0, 0.0, 0.0);
    }
    
    public MonthlyComparison getMonthOverMonthComparison(long start, long end) {
        double currentTotal = transactionDao.getTotalExpenseSync(start, end);
        long duration = end - start;
        long previousStart = start - duration;
        double previousTotal = transactionDao.getTotalExpenseSync(previousStart, start);
        
        return new MonthlyComparison("Current Month", "Previous Month", currentTotal, previousTotal, 0.0, 0.0, 0, 0);
    }
}
