package com.example.spendtracker.data.sms;

import static org.junit.Assert.*;

import com.example.spendtracker.domain.model.Transaction;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;

/**
 * Integration / regression tests for {@link SMSParser}.
 *
 * <p>These tests verify end-to-end parsing correctness and ensure that changes
 * to the parser don't silently break previously-supported message formats.
 */
public class SMSParserIntegrationTest {

    private SMSParser parser;

    @Before
    public void setUp() {
        parser = new SMSParser();
    }

    // =========================================================================
    // Regression: original test messages must still parse correctly
    // =========================================================================

    /** Regression for ICICI credit-card debit format originally in SMSParserTest. */
    @Test
    public void regression_ICICICreditCard_800() {
        String msg = "ICICI Bank Credit Card XX7007 debited for INR 800.00 on 17-Jul-26 " +
                "for UPI-656428212422-JAIN ENT. To dispute call 18001080/SMS BLOCK 7007 to 9215676766";
        Transaction t = parser.parseSMS("ICICI", msg, new ArrayList<>());

        assertNotNull(t);
        assertEquals(800.0, t.getAmount(), 0.001);
        assertEquals("EXPENSE", t.getType());
    }

    /** Regression for ICICI account debit format. */
    @Test
    public void regression_ICICIAccount_10() {
        String msg = "ICICI Bank Acct XX110 debited for Rs 10.00 on 02-Aug-26; " +
                "TANISHA KHANDEL credited. UPI:658011591943. Call 18002662 for dispute. SMS BLOCK 110 to 9215676766.";
        Transaction t = parser.parseSMS("ICICI", msg, new ArrayList<>());

        assertNotNull(t);
        assertEquals(10.0, t.getAmount(), 0.001);
        assertEquals("EXPENSE", t.getType());
    }

    /** Regression for AU Bank UPI debit format. */
    @Test
    public void regression_AUBank_148() {
        String msg = "Dr INR 148.00 - AU A/c X3698 02-AUG-2026 " +
                "UPI/DR/687943750944/Aryan medical/YESB Fraud? Call 180012001200/SMS BLOCK UPI to 5676767";
        Transaction t = parser.parseSMS("AU-BANK", msg, new ArrayList<>());

        assertNotNull(t);
        assertEquals(148.0, t.getAmount(), 0.001);
        assertEquals("EXPENSE", t.getType());
        assertEquals("AU Bank", t.getBankName());
    }

    // =========================================================================
    // Cross-format integration
    // =========================================================================

    @Test
    public void integration_MultiBank_SameMessageSequence() {
        // Simulate processing a stream of incoming messages from different banks
        String[] messages = {
            "INR 500 debited from your SBI A/C X1234 via UPI.",
            "Rs.1,200.00 debited from A/c XX9999 on 18-Aug-26 for VPA merchant@hdfc. Avl Bal Rs.3000 - HDFC Bank",
            "INR 750.00 has been debited from your Axis Bank A/c XX3344 on 12-Aug-26.",
            "Rs.450 debited from Kotak A/c XX5566 on 15-Aug-26.",
            "Your OTP is 123456. Do not share with anyone.",
            "Dear Customer, you've spent Rs.299 at NETFLIX.",
        };
        String[] senders = { "SBIUPI", "HDFCBK", "AXISBK", "KOTAK", "VM-OTP", "VM-CARD" };

        int transactionCount = 0;
        for (int i = 0; i < messages.length; i++) {
            Transaction t = parser.parseSMS(senders[i], messages[i], new ArrayList<>());
            if (t != null) transactionCount++;
        }

        // All non-OTP messages (5 of 6) should parse as transactions
        assertEquals("Expected 5 transactions (OTP should be skipped)", 5, transactionCount);
    }

    @Test
    public void integration_DebitAndCredit_CorrectTypes() {
        String debitMsg = "Rs.1000 debited from your account. UPI Ref 123456.";
        String creditMsg = "Rs.5000 credited to your account. NEFT from EMPLOYER.";

        Transaction debit  = parser.parseSMS("BANK", debitMsg, new ArrayList<>());
        Transaction credit = parser.parseSMS("BANK", creditMsg, new ArrayList<>());

        assertNotNull(debit);
        assertNotNull(credit);
        assertEquals("EXPENSE", debit.getType());
        assertEquals("INCOME",  credit.getType());
        assertEquals(1000.0, debit.getAmount(),  0.001);
        assertEquals(5000.0, credit.getAmount(), 0.001);
    }

    @Test
    public void integration_UPIVpaExtractedCorrectly() {
        String msg = "INR 399.00 debited from A/c XX2222. " +
                "UPI Ref: vendor@oksbi. Avl Bal: Rs.600. If not done by you call 1800XXX.";
        Transaction t = parser.parseSMS("BANK", msg, new ArrayList<>());

        assertNotNull(t);
        assertEquals(399.0, t.getAmount(), 0.001);
        assertNotNull(t.getUpiId());
        assertTrue("UPI ID should contain '@'", t.getUpiId().contains("@"));
    }

    @Test
    public void integration_LargeAmountWithCommas() {
        String msg = "INR 2,50,000.00 credited to your account. RTGS from PROPERTY SALE.";
        Transaction t = parser.parseSMS("BANK", msg, new ArrayList<>());

        assertNotNull(t);
        assertEquals(250000.0, t.getAmount(), 0.001);
        assertEquals("INCOME", t.getType());
    }

    @Test
    public void integration_BothDebitsAndCreditsMixedBatch() {
        // Ensure parser correctly distinguishes types in a realistic batch
        String[] messages = {
            "INR 100 credited to your SBI A/C.",
            "INR 200 debited from your HDFC A/C.",
            "INR 300 credited to your Axis A/C.",
            "INR 400 debited from your Kotak A/C.",
        };
        String[] expectedTypes = { "INCOME", "EXPENSE", "INCOME", "EXPENSE" };
        double[] expectedAmounts = { 100, 200, 300, 400 };

        for (int i = 0; i < messages.length; i++) {
            Transaction t = parser.parseSMS("BANK", messages[i], new ArrayList<>());
            assertNotNull("Message " + i + " must parse", t);
            assertEquals("Type mismatch at msg " + i, expectedTypes[i], t.getType());
            assertEquals("Amount mismatch at msg " + i, expectedAmounts[i], t.getAmount(), 0.001);
        }
    }
}
