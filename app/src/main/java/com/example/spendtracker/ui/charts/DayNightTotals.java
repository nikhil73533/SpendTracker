package com.example.spendtracker.ui.charts;

import com.example.spendtracker.domain.model.Transaction;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/** Local-time comparison, matching the 6 am / 6 pm boundaries in analytics. */
final class DayNightTotals {
    final double day;
    final double night;

    private DayNightTotals(double day, double night) {
        this.day = day;
        this.night = night;
    }

    static DayNightTotals from(List<Transaction> transactions, String type) {
        double day = 0, night = 0;
        Calendar local = Calendar.getInstance();
        if (transactions != null) for (Transaction transaction : transactions) {
            String category = transaction.getCategory();
            if (!"ACTIVE".equals(transaction.getStatus()) || !type.equals(transaction.getType())
                    || "TRANSFER".equals(transaction.getType())
                    || (category != null && category.toLowerCase(Locale.ROOT).contains("transfer"))
                    || !Double.isFinite(transaction.getAmount())) continue;
            local.setTimeInMillis(transaction.getDate());
            int hour = local.get(Calendar.HOUR_OF_DAY);
            if (hour >= 6 && hour < 18) day += Math.abs(transaction.getAmount());
            else night += Math.abs(transaction.getAmount());
        }
        return new DayNightTotals(day, night);
    }
}
