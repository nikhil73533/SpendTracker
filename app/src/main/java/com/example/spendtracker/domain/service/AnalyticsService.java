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
import com.example.spendtracker.domain.model.analytics.MerchantAnalytics;
import com.example.spendtracker.domain.model.analytics.SpendingConcentration;
import com.example.spendtracker.domain.model.analytics.LargeTransactionSummary;
import com.example.spendtracker.domain.model.analytics.AnomalyTransaction;
import com.example.spendtracker.domain.model.analytics.AnalyticsGranularity;
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

    /** Transaction counts by the UI-selected bucket. Values remain counts, not currency. */
    public List<TimeSeriesPoint> getTransactionFrequency(long start, long end, AnalyticsGranularity granularity) {
        List<TransactionDao.AnalyticsBucket> raw;
        switch (granularity) {
            case DAY:
                raw = transactionDao.getDailyAnalyticsBucketsSync(start, end);
                break;
            case WEEK:
                raw = transactionDao.getWeeklyAnalyticsBucketsSync(start, end);
                break;
            case YEAR:
                raw = transactionDao.getAnnualAnalyticsBucketsSync(start, end);
                break;
            case MONTH:
            default:
                raw = transactionDao.getMonthlyAnalyticsBucketsSync(start, end);
                break;
        }
        List<TimeSeriesPoint> result = new ArrayList<>();
        for (TransactionDao.AnalyticsBucket bucket : raw) {
            // secondary/tertiary retain amount context for tooltips without changing count chart.
            result.add(new TimeSeriesPoint(bucket.label, bucket.transactionCount,
                    bucket.totalAmount, bucket.expenseAmount));
        }
        return result;
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
        List<TransactionDao.AnalyticsBucket> buckets = transactionDao.getExpenseCategoryFrequencySync(start, end);
        List<CategoryAnalytics> result = new ArrayList<>();
        for (TransactionDao.AnalyticsBucket bucket : buckets) {
            result.add(new CategoryAnalytics(bucket.label, bucket.totalAmount, bucket.transactionCount));
        }
        return result;
    }

    public RollingAverage getRollingExpenseAverage(int months) {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.DAY_OF_MONTH, 1);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        long currentMonthStart = calendar.getTimeInMillis();
        calendar.add(Calendar.MONTH, -Math.max(months, 1));
        long windowStart = calendar.getTimeInMillis();
        long now = System.currentTimeMillis();
        double total = transactionDao.getTotalExpenseSync(windowStart, now);
        int count = transactionDao.getExpenseCountSync(windowStart, now);
        double currentMonth = transactionDao.getTotalExpenseSync(currentMonthStart, now);
        int monthsWithData = transactionDao.getMonthlyTotalsSync(windowStart, now, "EXPENSE").size();
        double averageMonthly = total / Math.max(months, 1);
        double averageCount = ((double) count) / Math.max(months, 1);
        double averageTransaction = count == 0 ? 0 : total / count;
        return new RollingAverage(months, averageMonthly, averageCount, averageTransaction,
                currentMonth, monthsWithData >= Math.min(months, 2));
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
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(start);
        calendar.add(Calendar.MONTH, -1);
        long oneMonthStart = calendar.getTimeInMillis();
        calendar.setTimeInMillis(start);
        calendar.add(Calendar.MONTH, -3);
        long threeMonthStart = calendar.getTimeInMillis();
        calendar.setTimeInMillis(start);
        calendar.add(Calendar.MONTH, -6);
        long sixMonthStart = calendar.getTimeInMillis();
        double previousDaily = dailyAverage(oneMonthStart, start);
        double threeMonthDaily = dailyAverage(threeMonthStart, start);
        double sixMonthDaily = dailyAverage(sixMonthStart, start);
        return new ForecastResult(totalExpense, days, Math.max(days, 30), previousDaily, threeMonthDaily, sixMonthDaily);
    }

    private double dailyAverage(long start, long end) {
        int days = Math.max(1, (int) ((end - start) / (1000L * 60 * 60 * 24)));
        return transactionDao.getTotalExpenseSync(start, end) / days;
    }
    
    public MonthlyComparison getMonthOverMonthComparison(long start, long end) {
        double currentTotal = transactionDao.getTotalExpenseSync(start, end);
        long duration = end - start;
        long previousStart = start - duration;
        double previousTotal = transactionDao.getTotalExpenseSync(previousStart, start);
        double currentIncome = transactionDao.getTotalIncomeSync(start, end);
        double previousIncome = transactionDao.getTotalIncomeSync(previousStart, start);
        int currentCount = transactionDao.getExpenseCountSync(start, end);
        int previousCount = transactionDao.getExpenseCountSync(previousStart, start);
        return new MonthlyComparison("Current Period", "Previous Period", currentTotal, previousTotal,
                currentIncome, previousIncome, currentCount, previousCount);
    }

    public List<MerchantAnalytics> getMerchantAnalytics(long start, long end) {
        List<TransactionDao.CategorySum> sums = transactionDao.getTopMerchantsByAmountSync(start, end, 50);
        List<TransactionDao.CategorySum> counts = transactionDao.getTopMerchantsByFrequencySync(start, end, 50);
        Map<String, Integer> countMap = new HashMap<>();
        for (TransactionDao.CategorySum cs : counts) {
            countMap.put(cs.category, (int) cs.total);
        }
        
        List<MerchantAnalytics> result = new ArrayList<>();
        for (TransactionDao.CategorySum s : sums) {
            int count = countMap.getOrDefault(s.category, 1);
            MerchantAnalytics.RecurrenceType type = count > 3 ? MerchantAnalytics.RecurrenceType.FREQUENT : MerchantAnalytics.RecurrenceType.OCCASIONAL;
            result.add(new MerchantAnalytics(s.category, s.total, count, type, null));
        }
        return result;
    }

    public List<MerchantAnalytics> getRecurringMerchants() {
        List<TransactionDao.CategorySum> recurring = transactionDao.getRecurringMerchantCandidatesSync(3);
        List<MerchantAnalytics> result = new ArrayList<>();
        for (TransactionDao.CategorySum r : recurring) {
            result.add(new MerchantAnalytics(r.category, 0, (int) r.total, MerchantAnalytics.RecurrenceType.RECURRING, "Unknown"));
        }
        return result;
    }

    public List<CategoryAnalytics> getCategoryGrowth(long start, long end) {
        long duration = end - start;
        long prevStart = start - duration;
        List<TransactionDao.CategorySum> current = transactionDao.getCategoryTotalsSync(start, end);
        List<TransactionDao.CategorySum> prev = transactionDao.getCategoryTotalsSync(prevStart, start);
        Map<String, Double> prevMap = new HashMap<>();
        for (TransactionDao.CategorySum p : prev) prevMap.put(p.category, p.total);
        
        List<CategoryAnalytics> result = new ArrayList<>();
        for (TransactionDao.CategorySum c : current) {
            double previous = prevMap.getOrDefault(c.category, 0.0);
            double growth = previous > 0 ? ((c.total - previous) / previous) * 100 : 0.0;
            result.add(new CategoryAnalytics(c.category, c.total, 0, growth, previous > 0));
        }
        return result;
    }

    public SpendingConcentration getSpendingConcentration(long start, long end) {
        double total = transactionDao.getTotalExpenseSync(start, end);
        List<TransactionDao.CategorySum> cats = transactionDao.getExpenseCategorySummariesSync(start, end);
        List<SpendingConcentration.Contributor> top = new ArrayList<>();
        double sumTop3 = 0;
        for (int i = 0; i < Math.min(3, cats.size()); i++) {
            TransactionDao.CategorySum c = cats.get(i);
            double pct = total > 0 ? (c.total / total) * 100 : 0;
            top.add(new SpendingConcentration.Contributor(c.category, c.total, pct));
            sumTop3 += c.total;
        }
        double concentration = total > 0 ? (sumTop3 / total) * 100 : 0;
        return new SpendingConcentration(total, top, concentration);
    }

    public LargeTransactionSummary getLargeTransactionAnalysis(long start, long end, double threshold) {
        List<TransactionEntity> active = transactionDao.getActiveNonTransferInRangeSync(start, end);
        List<TransactionEntity> large = new ArrayList<>();
        double totalLarge = 0;
        for (TransactionEntity t : active) {
            if ("EXPENSE".equals(t.type) && t.amount >= threshold) {
                large.add(t);
                totalLarge += t.amount;
            }
        }
        return new LargeTransactionSummary(threshold, large.size(), totalLarge, large);
    }

    public List<AnomalyTransaction> getUnusualTransactions(long start, long end) {
        List<TransactionEntity> txns = transactionDao.getActiveNonTransferInRangeSync(start, end);
        List<TransactionDao.CategorySum> catAverages = transactionDao.getCategoryAveragesSync();
        Map<String, Double> avgMap = new HashMap<>();
        for (TransactionDao.CategorySum c : catAverages) avgMap.put(c.category, c.total);

        List<AnomalyTransaction> anomalies = new ArrayList<>();
        for (TransactionEntity t : txns) {
            if (!"EXPENSE".equals(t.type)) continue;
            double avg = avgMap.getOrDefault(t.category, 0.0);
            if (avg > 0 && t.amount > avg * 3) {
                double multiple = t.amount / avg;
                anomalies.add(new AnomalyTransaction(t.id, t.amount, t.receiverName,
                        t.category, t.date, "Significantly higher than category average", avg, multiple));
            }
        }
        return anomalies;
    }
}
