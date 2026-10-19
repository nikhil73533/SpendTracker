package com.example.spendtracker.util;

import com.example.spendtracker.domain.model.Transaction;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class UpiUsageCalculationTest {
    @Test public void dailyAndMonthlyUsageExcludeIncomingAndOlderPayments() {
        ZoneId zone = ZoneId.of("Asia/Kolkata");
        long now = LocalDate.of(2026, 9, 20).atTime(12, 0).atZone(zone).toInstant().toEpochMilli();
        Transaction today = payment(2500, now, "EXPENSE", "DEBIT", "UPI", "merchant@upi");
        Transaction inbound = payment(9000, now, "INCOME", "CREDIT", "UPI", "friend@upi");
        Transaction earlierMonth = payment(750, LocalDate.of(2026, 9, 3).atTime(9, 0).atZone(zone).toInstant().toEpochMilli(), "TRANSFER", "DEBIT", "", "own@upi");
        Transaction priorMonth = payment(400, LocalDate.of(2026, 8, 30).atTime(9, 0).atZone(zone).toInstant().toEpochMilli(), "EXPENSE", "DEBIT", "UPI", "merchant@upi");
        UpiUsage usage = UpiUsageTracker.calculate(Arrays.asList(today, inbound, earlierMonth, priorMonth), now, zone, 3000, 4000);
        assertEquals(2500, usage.getDailyAmount(), 0.001);
        assertEquals(3250, usage.getMonthlyAmount(), 0.001);
        assertEquals(1, usage.getDailyPaymentCount());
        assertEquals(2, usage.getMonthlyPaymentCount());
        assertEquals(83, usage.getDailyPercent());
        assertEquals(81, usage.getMonthlyPercent());
    }

    private static Transaction payment(double amount, long date, String type, String direction, String sourceType, String upiId) {
        Transaction transaction = new Transaction();
        transaction.setAmount(amount); transaction.setDate(date); transaction.setType(type); transaction.setDirection(direction);
        transaction.setCategory("TRANSFER".equals(type) ? "Transfer" : "Food");
        transaction.setSourceType(sourceType); transaction.setUpiId(upiId);
        return transaction;
    }
}
