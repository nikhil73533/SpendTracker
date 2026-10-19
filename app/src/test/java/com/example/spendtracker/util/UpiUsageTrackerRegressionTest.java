package com.example.spendtracker.util;

import com.example.spendtracker.domain.model.Transaction;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import static org.junit.Assert.assertEquals;

/** 400 real input vectors covering source, identifier, debit/credit, and transfer combinations. */
@RunWith(Parameterized.class)
public class UpiUsageTrackerRegressionTest {
    private final String type, category, direction, source, sourceType, upiId;
    private final boolean expected;

    public UpiUsageTrackerRegressionTest(String type, String category, String direction, String source,
                                         String sourceType, String upiId, boolean expected) {
        this.type = type; this.category = category; this.direction = direction;
        this.source = source; this.sourceType = sourceType; this.upiId = upiId; this.expected = expected;
    }

    @Parameterized.Parameters(name = "{index}: {0}/{2}/{3}/{4}/{5}")
    public static Collection<Object[]> cases() {
        String[][] paymentKinds = {
                {"EXPENSE", "Food", "DEBIT", "true"},
                {"EXPENSE", "Food", "CREDIT", "false"},
                {"INCOME", "Salary", "CREDIT", "false"},
                {"TRANSFER", "Transfer", "DEBIT", "true"},
                {"TRANSFER", "Transfer", "CREDIT", "false"}
        };
        String[][] sourceKinds = {
                {"UPI", "", "true"}, {"upi payment", "", "true"}, {"Bank (UPI)", "", "true"},
                {"", "UPI", "true"}, {"Card", "", "false"}, {"Cash", "", "false"},
                {"NEFT", "", "false"}, {"Bank transfer", "", "false"}, {"Wallet", "", "false"}, {"", "", "false"}
        };
        String[][] identifiers = {
                {"merchant@upi", "true"}, {"payee@okaxis", "true"}, {"a@ibl", "true"}, {"name@paytm", "true"},
                {"", "false"}, {"REF123", "false"}, {"card ending 1234", "false"}, {"not-an-id", "false"}
        };
        Collection<Object[]> values = new ArrayList<>();
        for (String[] payment : paymentKinds) for (String[] sourceKind : sourceKinds) for (String[] identifier : identifiers) {
            boolean upi = Boolean.parseBoolean(sourceKind[2]) || Boolean.parseBoolean(identifier[1]);
            boolean debitLike = Boolean.parseBoolean(payment[3]);
            values.add(new Object[]{payment[0], payment[1], payment[2], sourceKind[0], sourceKind[1], identifier[0], upi && debitLike});
        }
        return values;
    }

    @Test public void classifiesOnlyOutgoingUpiPayments() {
        assertEquals(expected, UpiUsageTracker.isOutgoingUpi(type, category, direction, source, sourceType, upiId));
    }

}
