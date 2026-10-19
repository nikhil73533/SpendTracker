package com.example.spendtracker.ui.dashboard;

import com.example.spendtracker.domain.model.Transaction;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Pure calendar-day aggregation shared by the glance strip and expanded charts. */
public final class DashboardChartSeries {
    private DashboardChartSeries() { }

    public static Map<Long, Double> activity(List<Transaction> transactions) {
        TreeMap<Long, Double> days = new TreeMap<>();
        if (transactions == null) return days;
        for (Transaction transaction : transactions) {
            if (!"ACTIVE".equals(transaction.getStatus()) || !Double.isFinite(transaction.getAmount())) continue;
            days.merge(startOfDay(transaction.getDate()), Math.abs(transaction.getAmount()), Double::sum);
        }
        if (!days.isEmpty()) {
            Calendar day = Calendar.getInstance();
            day.setTimeInMillis(days.firstKey());
            long last = days.lastKey();
            // Daily is bounded by the active month. Guard malformed imported date spans.
            if ((last - days.firstKey()) / 86_400_000L <= 366) {
                while (day.getTimeInMillis() < last) {
                    days.putIfAbsent(day.getTimeInMillis(), 0d);
                    day.add(Calendar.DAY_OF_MONTH, 1);
                }
            }
        }
        return days;
    }

    public static Map<Long, Double> spendingWeek(List<Transaction> transactions, long now) {
        TreeMap<Long, Double> days = new TreeMap<>();
        Calendar day = Calendar.getInstance();
        day.setTimeInMillis(startOfDay(now));
        day.add(Calendar.DAY_OF_MONTH, -6);
        for (int i = 0; i < 7; i++) {
            days.put(day.getTimeInMillis(), 0d);
            day.add(Calendar.DAY_OF_MONTH, 1);
        }
        for (Transaction transaction : transactions == null ? Collections.<Transaction>emptyList() : transactions) {
            if (!"ACTIVE".equals(transaction.getStatus()) || !"EXPENSE".equals(transaction.getType())
                    || transaction.getCategory().toLowerCase(java.util.Locale.ROOT).contains("transfer")
                    || !Double.isFinite(transaction.getAmount())) continue;
            long key = startOfDay(transaction.getDate());
            if (days.containsKey(key)) days.put(key, days.get(key) + Math.abs(transaction.getAmount()));
        }
        return days;
    }

    private static long startOfDay(long timestamp) {
        Calendar day = Calendar.getInstance();
        day.setTimeInMillis(timestamp);
        day.set(Calendar.HOUR_OF_DAY, 0);
        day.set(Calendar.MINUTE, 0);
        day.set(Calendar.SECOND, 0);
        day.set(Calendar.MILLISECOND, 0);
        return day.getTimeInMillis();
    }
}
