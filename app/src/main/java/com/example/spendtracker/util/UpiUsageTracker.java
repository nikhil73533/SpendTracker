package com.example.spendtracker.util;

import com.example.spendtracker.domain.model.Transaction;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collection;

/** Calculates outgoing UPI usage without treating credits as spendable-limit consumption. */
public final class UpiUsageTracker {
    /** NPCI's normal UPI ceiling is per payment; special merchant categories can have higher limits. */
    public static final double STANDARD_PER_PAYMENT_LIMIT = 100_000d;

    private UpiUsageTracker() {}

    public static UpiUsage calculate(Collection<Transaction> transactions, long now, ZoneId zone,
                                     double dailyLimit, double monthlyLimit) {
        LocalDate today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate();
        double daily = 0, monthly = 0;
        int dailyCount = 0, monthlyCount = 0;
        if (transactions != null) {
            for (Transaction transaction : transactions) {
                if (transaction == null || !isOutgoingUpi(transaction)) continue;
                LocalDate date = Instant.ofEpochMilli(transaction.getDate()).atZone(zone).toLocalDate();
                if (date.getYear() == today.getYear() && date.getMonthValue() == today.getMonthValue()) {
                    monthly += transaction.getAmount();
                    monthlyCount++;
                    if (date.equals(today)) {
                        daily += transaction.getAmount();
                        dailyCount++;
                    }
                }
            }
        }
        return new UpiUsage(daily, monthly, dailyCount, monthlyCount, dailyLimit, monthlyLimit);
    }

    public static boolean isOutgoingUpi(Transaction transaction) {
        if (transaction == null) return false;
        return isOutgoingUpi(transaction.getType(), transaction.getCategory(), transaction.getDirection(),
                transaction.getSource(), transaction.getSourceType(), transaction.getUpiId());
    }

    public static boolean isOutgoingUpi(String type, String category, String direction, String source,
                                        String sourceType, String upiId) {
        String normalizedType = upper(type);
        if ("INCOME".equals(normalizedType) || "CREDIT".equals(upper(direction))) return false;
        boolean transfer = "TRANSFER".equals(normalizedType) || lower(category).contains("transfer");
        if (transfer && "CREDIT".equals(upper(direction))) return false;
        return upper(sourceType).contains("UPI") || upper(source).contains("UPI") || lower(upiId).contains("@");
    }

    private static String upper(String value) { return value == null ? "" : value.trim().toUpperCase(java.util.Locale.ROOT); }
    private static String lower(String value) { return value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT); }
}
