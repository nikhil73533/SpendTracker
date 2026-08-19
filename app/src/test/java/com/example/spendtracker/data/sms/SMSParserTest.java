package com.example.spendtracker.data.sms;

import static org.junit.Assert.*;

import com.example.spendtracker.domain.model.Transaction;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;

/**
 * Unit tests for {@link SMSParser}.
 *
 * <p>All tests use the no-arg constructor (context-free) so they can run as pure JVM
 * unit tests without an Android emulator. The JSON bank-config pipeline is skipped;
 * only the generic rule-based parser and the direct helper methods are exercised.
 *
 * <p>Coverage:
 * <ul>
 *   <li>ICICI Bank – credit card debit, account debit, account credit</li>
 *   <li>AU Bank – UPI debit</li>
 *   <li>HDFC Bank – account debit, credit card debit</li>
 *   <li>SBI – account debit / credit</li>
 *   <li>Axis Bank – account debit / credit</li>
 *   <li>Kotak Bank – account debit</li>
 *   <li>Generic / multi-bank – various wording and abbreviation styles</li>
 *   <li>OTP filter – must return null for OTP and non-transactional messages</li>
 *   <li>Edge cases – huge amounts, missing amount, partial messages, ambiguous wording</li>
 * </ul>
 */
public class SMSParserTest {

    private SMSParser parser;

    @Before
    public void setUp() {
        // Context-free constructor – skips JSON config assets but enables generic parsing.
        parser = new SMSParser();
    }

    // =========================================================================
    // ICICI Bank
    // =========================================================================

    @Test
    public void testICICICreditCardDebit() {
        String msg = "ICICI Bank Credit Card XX7007 debited for INR 800.00 on 17-Jul-26 " +
                "for UPI-656428212422-JAIN ENT. To dispute call 18001080/SMS BLOCK 7007 to 9215676766";
        Transaction t = parser.parseSMS("ICICIB", msg, new ArrayList<>());

        assertNotNull("Expected non-null transaction for ICICI CC debit", t);
        assertEquals(800.0, t.getAmount(), 0.001);
        assertEquals("EXPENSE", t.getType());
    }

    @Test
    public void testICICIAccountDebit() {
        String msg = "ICICI Bank Acct XX110 debited for Rs 10.00 on 02-Aug-26; " +
                "TANISHA KHANDEL credited. UPI:658011591943. Call 18002662 for dispute.";
        Transaction t = parser.parseSMS("ICICIB", msg, new ArrayList<>());

        assertNotNull("Expected non-null transaction for ICICI account debit", t);
        assertEquals(10.0, t.getAmount(), 0.001);
        assertEquals("EXPENSE", t.getType());
    }

    @Test
    public void testICICIAccountCredit() {
        String msg = "Acct XX9823 credited with INR 5000.00 on 15-Aug-26 from SALARY EMPLOYER. UPI:712345678901";
        Transaction t = parser.parseSMS("ICICIB", msg, new ArrayList<>());

        assertNotNull("Expected non-null transaction for ICICI account credit", t);
        assertEquals(5000.0, t.getAmount(), 0.001);
        assertEquals("INCOME", t.getType());
    }

    // =========================================================================
    // AU Bank
    // =========================================================================

    @Test
    public void testAUBankDebit() {
        String msg = "Dr INR 148.00 - AU A/c X3698 02-AUG-2026 " +
                "UPI/DR/687943750944/Aryan medical/YESB Fraud? Call 180012001200/SMS BLOCK UPI to 5676767";
        Transaction t = parser.parseSMS("AU-BANK", msg, new ArrayList<>());

        assertNotNull("Expected non-null transaction for AU Bank debit", t);
        assertEquals(148.0, t.getAmount(), 0.001);
        assertEquals("EXPENSE", t.getType());
        assertEquals("AU Bank", t.getBankName());
    }

    @Test
    public void testAUBankCredit() {
        String msg = "Cr INR 2500.00 - AU A/c X3698 05-AUG-2026 " +
                "UPI/CR/987654321000/John Doe/HDFC Avl Bal:Rs.12345.00";
        Transaction t = parser.parseSMS("AU-BANK", msg, new ArrayList<>());

        assertNotNull("Expected non-null transaction for AU Bank credit", t);
        assertEquals(2500.0, t.getAmount(), 0.001);
        assertEquals("INCOME", t.getType());
    }

    // =========================================================================
    // HDFC Bank
    // =========================================================================

    @Test
    public void testHDFCAccountDebitViaUPI() {
        String msg = "Rs.1,200.00 debited from A/c XX1234 on 18-Aug-26 " +
                "for VPA paytm@paytm Avl Bal Rs.8900.50 - HDFC Bank";
        Transaction t = parser.parseSMS("HDFCBK", msg, new ArrayList<>());

        assertNotNull("Expected non-null transaction for HDFC UPI debit", t);
        assertEquals(1200.0, t.getAmount(), 0.001);
        assertEquals("EXPENSE", t.getType());
        assertEquals("HDFC Bank", t.getBankName());
    }

    @Test
    public void testHDFCCreditCardDebit() {
        String msg = "HDFC Bank: Rs 3500.00 charged on HDFC Bank Credit Card XX5678 " +
                "at AMAZON on 18-Aug-26. Call 18002676161 if not done by you.";
        Transaction t = parser.parseSMS("HDFCBK", msg, new ArrayList<>());

        assertNotNull("Expected non-null transaction for HDFC CC debit", t);
        assertEquals(3500.0, t.getAmount(), 0.001);
        assertEquals("EXPENSE", t.getType());
    }

    @Test
    public void testHDFCAccountCredit() {
        String msg = "Rs.50,000.00 credited to A/c XX9999 on 01-Aug-26. " +
                "Avl Bal Rs.55000.00. If not done by you call 18002676161.";
        Transaction t = parser.parseSMS("HDFCBK", msg, new ArrayList<>());

        assertNotNull("Expected non-null transaction for HDFC credit", t);
        assertEquals(50000.0, t.getAmount(), 0.001);
        assertEquals("INCOME", t.getType());
    }

    // =========================================================================
    // SBI
    // =========================================================================

    @Test
    public void testSBIAccountDebit() {
        // Note: the generic parser extracts bank name from the body text.
        // This message contains no "sbi" keyword, so bank defaults to "Bank".
        // The important assertions are amount and type.
        String msg = "Your A/C XX7890 is Debited by Rs 500 on date 10Aug26. " +
                "Info: UPI/1234567890/GROCERY STORE. Avl Bal Rs 12000.";
        Transaction t = parser.parseSMS("SBIUPI", msg, new ArrayList<>());

        assertNotNull("Expected non-null transaction for SBI debit", t);
        assertEquals(500.0, t.getAmount(), 0.001);
        assertEquals("EXPENSE", t.getType());
        // When body contains "sbi", the bank name is extracted; here it doesn't,
        // so we only assert amount and type.
    }

    @Test
    public void testSBIAccountCredit() {
        String msg = "Your A/C XX7890 is Credited by Rs 15,000 on date 05Aug26. " +
                "Info: NEFT-EMPLOYER. Avl Bal Rs 27000.";
        Transaction t = parser.parseSMS("SBIUPI", msg, new ArrayList<>());

        assertNotNull("Expected non-null transaction for SBI credit", t);
        assertEquals(15000.0, t.getAmount(), 0.001);
        assertEquals("INCOME", t.getType());
    }

    // =========================================================================
    // Axis Bank
    // =========================================================================

    @Test
    public void testAxisBankDebit() {
        String msg = "INR 750.00 has been debited from your Axis Bank A/c XX3344 " +
                "on 12-Aug-26. If not done by you, call 18004195555.";
        Transaction t = parser.parseSMS("AXISBK", msg, new ArrayList<>());

        assertNotNull("Expected non-null transaction for Axis debit", t);
        assertEquals(750.0, t.getAmount(), 0.001);
        assertEquals("EXPENSE", t.getType());
        assertEquals("Axis Bank", t.getBankName());
    }

    @Test
    public void testAxisBankCredit() {
        String msg = "INR 25,000.00 has been credited to your Axis Bank A/c XX3344 " +
                "on 01-Aug-26. Avl Bal: INR 40000.00.";
        Transaction t = parser.parseSMS("AXISBK", msg, new ArrayList<>());

        assertNotNull("Expected non-null transaction for Axis credit", t);
        assertEquals(25000.0, t.getAmount(), 0.001);
        assertEquals("INCOME", t.getType());
    }

    // =========================================================================
    // Kotak Bank
    // =========================================================================

    @Test
    public void testKotakBankDebit() {
        String msg = "Rs.450 debited from Kotak A/c XX5566 on 15-Aug-26. " +
                "UPI ref 987654. If not by you call 1860 266 0811.";
        Transaction t = parser.parseSMS("KOTAK", msg, new ArrayList<>());

        assertNotNull("Expected non-null transaction for Kotak debit", t);
        assertEquals(450.0, t.getAmount(), 0.001);
        assertEquals("EXPENSE", t.getType());
        assertEquals("Kotak Bank", t.getBankName());
    }

    // =========================================================================
    // Generic / miscellaneous formats
    // =========================================================================

    @Test
    public void testGenericSpentFormat() {
        String msg = "You've spent Rs.299.00 at NETFLIX. Avl bal: Rs.5,201.00";
        Transaction t = parser.parseSMS("VM-CARD", msg, new ArrayList<>());

        assertNotNull("Expected non-null for generic 'spent' format", t);
        assertEquals(299.0, t.getAmount(), 0.001);
        assertEquals("EXPENSE", t.getType());
    }

    @Test
    public void testGenericDebitedWithCommas() {
        String msg = "Dear Customer, INR 1,05,000.00 has been debited from your account.";
        Transaction t = parser.parseSMS("SOMEBANK", msg, new ArrayList<>());

        assertNotNull("Expected non-null for large amount with commas", t);
        assertEquals(105000.0, t.getAmount(), 0.001);
        assertEquals("EXPENSE", t.getType());
    }

    @Test
    public void testGenericCreditedFormat() {
        String msg = "INR 3000 credited to your account. NEFT from EMPLOYER LTD. Ref:TXN12345678.";
        Transaction t = parser.parseSMS("BANK", msg, new ArrayList<>());

        assertNotNull("Expected non-null for generic credit", t);
        assertEquals(3000.0, t.getAmount(), 0.001);
        assertEquals("INCOME", t.getType());
    }

    @Test
    public void testUpiVpaExtraction() {
        String msg = "INR 199.00 debited from A/c XX1111. UPI Ref: merchant@oksbi. Bal: Rs.500";
        Transaction t = parser.parseSMS("BANK", msg, new ArrayList<>());

        assertNotNull(t);
        assertEquals(199.0, t.getAmount(), 0.001);
        // UPI VPA should be captured
        assertNotNull(t.getUpiId());
        assertFalse(t.getUpiId().isEmpty());
    }

    @Test
    public void testRupeeSymbolFormat() {
        String msg = "₹2,500 has been debited from your account XX4321 via UPI.";
        Transaction t = parser.parseSMS("BANK", msg, new ArrayList<>());

        assertNotNull(t);
        assertEquals(2500.0, t.getAmount(), 0.001);
        assertEquals("EXPENSE", t.getType());
    }

    // =========================================================================
    // OTP / non-transactional rejection
    // =========================================================================

    @Test
    public void testOTPMessageRejected() {
        String msg = "Your OTP for login is 482910. Do not share this with anyone. Valid for 5 mins.";
        Transaction t = parser.parseSMS("VM-OTP", msg, new ArrayList<>());
        assertNull("OTP message must return null", t);
    }

    @Test
    public void testOneTimePasswordRejected() {
        String msg = "Your one-time-password for banking access is 123456. Expires in 10 minutes.";
        Transaction t = parser.parseSMS("VM-OTP", msg, new ArrayList<>());
        assertNull("One-time-password message must return null", t);
    }

    @Test
    public void testVerificationCodeRejected() {
        String msg = "Your verification code is 998877. Use it within 15 minutes.";
        Transaction t = parser.parseSMS("VM-OTP", msg, new ArrayList<>());
        assertNull("Verification code message must return null", t);
    }

    @Test
    public void testLoginAlertRejected() {
        String msg = "Alert: Login attempt detected on your account from IP 192.168.1.1.";
        Transaction t = parser.parseSMS("VM-ALERT", msg, new ArrayList<>());
        assertNull("Login alert message must return null", t);
    }

    @Test
    public void testMarketingMessageRejected() {
        String msg = "Congratulations! You have won a special cashback offer. Click here to claim.";
        Transaction t = parser.parseSMS("AD-BANK", msg, new ArrayList<>());
        assertNull("Marketing/promo message must return null", t);
    }

    @Test
    public void testNoAmountOrKeywordRejected() {
        String msg = "Your bank account has been updated. Visit the branch for details.";
        Transaction t = parser.parseSMS("BANK", msg, new ArrayList<>());
        assertNull("Non-transactional message with no amount must return null", t);
    }

    // =========================================================================
    // Edge cases
    // =========================================================================

    @Test
    public void testNullBodyReturnsNull() {
        Transaction t = parser.parseSMS("BANK", null, new ArrayList<>());
        assertNull("Null body must return null", t);
    }

    @Test
    public void testEmptyBodyReturnsNull() {
        Transaction t = parser.parseSMS("BANK", "", new ArrayList<>());
        assertNull("Empty body must return null", t);
    }

    @Test
    public void testAmountExceedsThresholdRejected() {
        // Sanity guard: amounts > 10,000,000 are treated as parse errors (phone number etc.)
        String msg = "INR 99999999.00 debited from your account.";
        Transaction t = parser.parseSMS("BANK", msg, new ArrayList<>());
        assertNull("Unrealistically large amount must be rejected", t);
    }

    @Test
    public void testZeroAmountRejected() {
        String msg = "INR 0.00 debited from your account.";
        Transaction t = parser.parseSMS("BANK", msg, new ArrayList<>());
        assertNull("Zero-amount transaction must be rejected", t);
    }

    @Test
    public void testAmountWithNoDecimal() {
        String msg = "Rs 5000 credited to your account. NEFT from EMPLOYER.";
        Transaction t = parser.parseSMS("BANK", msg, new ArrayList<>());
        assertNotNull(t);
        assertEquals(5000.0, t.getAmount(), 0.001);
        assertEquals("INCOME", t.getType());
    }

    @Test
    public void testDebitAndCreditBothPresent_DebitWins() {
        // "ICICI Bank Acct XX110 debited ... TANISHA KHANDEL credited" → EXPENSE wins
        String msg = "ICICI Bank Acct XX110 debited for Rs 50.00; TANISHA KHANDEL credited. UPI:99887766.";
        Transaction t = parser.parseSMS("ICICIB", msg, new ArrayList<>());
        assertNotNull(t);
        assertEquals("EXPENSE", t.getType());
    }

    @Test
    public void testPartialMessageWithAmount() {
        String msg = "debited Rs 200";
        Transaction t = parser.parseSMS("BANK", msg, new ArrayList<>());
        assertNotNull(t);
        assertEquals(200.0, t.getAmount(), 0.001);
        assertEquals("EXPENSE", t.getType());
    }

    // =========================================================================
    // Helper method unit tests
    // =========================================================================

    @Test
    public void testExtractAmount_INRPrefix() {
        assertEquals(1500.50, parser.extractAmount("INR 1,500.50 debited"), 0.001);
    }

    @Test
    public void testExtractAmount_RsDotPrefix() {
        assertEquals(999.0, parser.extractAmount("Rs. 999 charged"), 0.001);
    }

    @Test
    public void testExtractAmount_RupeeSymbol() {
        assertEquals(250.0, parser.extractAmount("₹250 paid"), 0.001);
    }

    @Test
    public void testExtractAmount_NoneReturnsZero() {
        assertEquals(0.0, parser.extractAmount("Hello world"), 0.001);
    }

    @Test
    public void testExtractBank_ICICI() {
        assertEquals("ICICI Bank", parser.extractBank("icici bank account debited"));
    }

    @Test
    public void testExtractBank_HDFC() {
        assertEquals("HDFC Bank", parser.extractBank("hdfc bank upi debited"));
    }

    @Test
    public void testExtractBank_SBI() {
        assertEquals("SBI", parser.extractBank("your sbi account"));
    }

    @Test
    public void testExtractBank_AUBank() {
        assertEquals("AU Bank", parser.extractBank("au a/c x3698 debited"));
    }

    @Test
    public void testExtractBank_Unknown() {
        assertEquals("Bank", parser.extractBank("some random text without bank name"));
    }

    @Test
    public void testExtractReceiver_AUBankStyle() {
        String msg = "UPI/DR/123456789/Aryan Medical/YESB";
        String receiver = parser.extractReceiver(msg, "AU Bank");
        assertEquals("Aryan Medical", receiver);
    }

    @Test
    public void testExtractReceiver_ICICIStyle() {
        String msg = "ICICI Bank Acct XX110 debited for Rs 10.00; TANISHA KHANDEL credited.";
        String receiver = parser.extractReceiver(msg, "ICICI Bank");
        assertEquals("TANISHA KHANDEL", receiver);
    }

    @Test
    public void testExtractUpiOrRef_NumericRef() {
        String result = parser.extractUpiOrRef("UPI Ref: 912345678901. Balance Rs.500");
        assertFalse("Numeric UPI ref must be non-empty", result.isEmpty());
        assertTrue(result.matches("[0-9]+"));
    }

    @Test
    public void testExtractUpiOrRef_VPA() {
        String result = parser.extractUpiOrRef("Paid to merchant@upi for groceries");
        assertFalse("VPA must be non-empty", result.isEmpty());
        assertTrue(result.contains("@"));
    }

    @Test
    public void testExtractUpiOrRef_NoRef() {
        String result = parser.extractUpiOrRef("Amount debited from account.");
        assertTrue("No ref found must return empty string", result.isEmpty());
    }
}
