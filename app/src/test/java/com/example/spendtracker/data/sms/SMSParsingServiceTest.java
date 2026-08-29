package com.example.spendtracker.data.sms;

import static org.junit.Assert.*;

import com.example.spendtracker.data.sms.detection.*;
import com.example.spendtracker.data.sms.duplicate.DuplicateDetector;
import com.example.spendtracker.data.sms.extraction.*;
import com.example.spendtracker.data.sms.model.*;
import com.example.spendtracker.data.sms.normalization.*;
import com.example.spendtracker.data.sms.preprocessing.SMSPreprocessor;
import com.example.spendtracker.data.sms.validation.TransactionValidator;
import com.example.spendtracker.domain.model.Transaction;

import org.junit.Before;
import org.junit.Test;

/**
 * Integration tests for the full {@link SMSParsingService} pipeline.
 *
 * Uses the test constructor (no Android context) so these run as pure JVM tests.
 * Bank JSON configs are not loaded; only generic extraction is exercised.
 */
public class SMSParsingServiceTest {

    private SMSParsingService service;

    @Before
    public void setUp() {
        service = new SMSParsingService(
            new SMSPreprocessor(),
            new TransactionDetector(),
            new BankIdentifier(),
            new AmountExtractor(),
            new TransactionTypeExtractor(),
            new MerchantExtractor(),
            new AccountExtractor(),
            new UpiExtractor(),
            new DateExtractor(),
            new SourceTypeExtractor(),
            new TransactionStatusExtractor(),
            new BankNormalizer(),
            new MerchantNormalizer(),
            new TransactionValidator(),
            new DuplicateDetector()
        );
    }

    // =========================================================================
    // Complete pipeline: HDFC
    // =========================================================================

    @Test
    public void testHDFCDebit() {
        ParseResult r = service.parse("HDFCBK",
            "Rs.500.00 debited from A/c XX1234 on 28-Aug-2026. UPI transaction to Zomato. Ref No 123456789.",
            System.currentTimeMillis());

        assertTrue("HDFC debit should parse successfully", r.isSuccess());
        assertNotNull(r.getTransaction());
        assertEquals(500.0, r.getTransaction().getAmount(), 0.001);
        assertEquals("EXPENSE", r.getTransaction().getType());
        assertEquals("HDFC", r.getDetectedBank());
    }

    @Test
    public void testHDFCCredit() {
        ParseResult r = service.parse("HDFCBK",
            "Rs.50,000.00 credited to A/c XX9999 on 01-Aug-26. Avl Bal Rs.55000.00.",
            System.currentTimeMillis());

        assertTrue(r.isSuccess());
        assertEquals(50000.0, r.getTransaction().getAmount(), 0.001);
        assertEquals("INCOME", r.getTransaction().getType());
    }

    @Test
    public void testHDFCCreditCardDebit() {
        ParseResult r = service.parse("HDFCBK",
            "HDFC Bank: Rs 3500.00 charged on HDFC Bank Credit Card XX5678 at AMAZON on 18-Aug-26.",
            System.currentTimeMillis());

        assertTrue(r.isSuccess());
        assertEquals(3500.0, r.getTransaction().getAmount(), 0.001);
        assertEquals("EXPENSE", r.getTransaction().getType());
    }

    // =========================================================================
    // Complete pipeline: ICICI
    // =========================================================================

    @Test
    public void testICICICreditCardDebit() {
        ParseResult r = service.parse("ICICIB",
            "ICICI Bank Credit Card XX7007 debited for INR 800.00 on 17-Jul-26 " +
            "for UPI-656428212422-JAIN ENT.",
            System.currentTimeMillis());

        assertTrue(r.isSuccess());
        assertEquals(800.0, r.getTransaction().getAmount(), 0.001);
        assertEquals("EXPENSE", r.getTransaction().getType());
    }

    @Test
    public void testICICIAccountDebit() {
        ParseResult r = service.parse("ICICIB",
            "ICICI Bank Acct XX110 debited for Rs 10.00 on 02-Aug-26; " +
            "TANISHA KHANDEL credited. UPI:658011591943.",
            System.currentTimeMillis());

        assertTrue(r.isSuccess());
        assertEquals(10.0, r.getTransaction().getAmount(), 0.001);
        assertEquals("EXPENSE", r.getTransaction().getType());
    }

    // =========================================================================
    // Complete pipeline: AU Bank
    // =========================================================================

    @Test
    public void testAUBankDebit() {
        ParseResult r = service.parse("AU-BANK",
            "Dr INR 148.00 - AU A/c X3698 02-AUG-2026 " +
            "UPI/DR/687943750944/Aryan medical/YESB",
            System.currentTimeMillis());

        assertTrue(r.isSuccess());
        assertEquals(148.0, r.getTransaction().getAmount(), 0.001);
        assertEquals("EXPENSE", r.getTransaction().getType());
    }

    @Test
    public void testAUBankCredit() {
        ParseResult r = service.parse("AU-BANK",
            "Cr INR 2500.00 - AU A/c X3698 05-AUG-2026 " +
            "UPI/CR/987654321000/John Doe/HDFC",
            System.currentTimeMillis());

        assertTrue(r.isSuccess());
        assertEquals(2500.0, r.getTransaction().getAmount(), 0.001);
        assertEquals("INCOME", r.getTransaction().getType());
    }

    // =========================================================================
    // Complete pipeline: SBI
    // =========================================================================

    @Test
    public void testSBIDebit() {
        ParseResult r = service.parse("SBIUPI",
            "Your A/C XX7890 is Debited by Rs 500 on date 10Aug26. " +
            "Info: UPI/1234567890/GROCERY STORE. Avl Bal Rs 12000.",
            System.currentTimeMillis());

        assertTrue(r.isSuccess());
        assertEquals(500.0, r.getTransaction().getAmount(), 0.001);
        assertEquals("EXPENSE", r.getTransaction().getType());
    }

    @Test
    public void testSBICredit() {
        ParseResult r = service.parse("SBIUPI",
            "Your A/C XX7890 is Credited by Rs 15,000 on date 05Aug26. " +
            "Info: NEFT-EMPLOYER. Avl Bal Rs 27000.",
            System.currentTimeMillis());

        assertTrue(r.isSuccess());
        assertEquals(15000.0, r.getTransaction().getAmount(), 0.001);
        assertEquals("INCOME", r.getTransaction().getType());
    }

    // =========================================================================
    // Complete pipeline: Axis Bank
    // =========================================================================

    @Test
    public void testAxisDebit() {
        ParseResult r = service.parse("AXISBK",
            "INR 750.00 has been debited from your Axis Bank A/c XX3344 on 12-Aug-26.",
            System.currentTimeMillis());

        assertTrue(r.isSuccess());
        assertEquals(750.0, r.getTransaction().getAmount(), 0.001);
        assertEquals("EXPENSE", r.getTransaction().getType());
        assertEquals("Axis", r.getDetectedBank());
    }

    // =========================================================================
    // Complete pipeline: Kotak
    // =========================================================================

    @Test
    public void testKotakDebit() {
        ParseResult r = service.parse("KOTAK",
            "Rs.450 debited from Kotak A/c XX5566 on 15-Aug-26. UPI ref 987654.",
            System.currentTimeMillis());

        assertTrue(r.isSuccess());
        assertEquals(450.0, r.getTransaction().getAmount(), 0.001);
        assertEquals("EXPENSE", r.getTransaction().getType());
        assertEquals("Kotak", r.getDetectedBank());
    }

    // =========================================================================
    // Transfer detection
    // =========================================================================

    @Test
    public void testTransferDetection() {
        ParseResult r = service.parse("HDFCBK",
            "Rs 10,000 transferred from your Savings A/c XX1234 to Current A/c XX5678.",
            System.currentTimeMillis());

        assertTrue(r.isSuccess());
        assertEquals(10000.0, r.getTransaction().getAmount(), 0.001);
        assertEquals("TRANSFER", r.getTransaction().getType());
        assertNotEquals("EXPENSE", r.getTransaction().getType());
        assertNotEquals("INCOME", r.getTransaction().getType());
    }

    @Test
    public void testFundTransfer() {
        ParseResult r = service.parse("SBIUPI",
            "Fund Transfer of INR 5000 from your A/c XX1111 to A/c XX2222 successful.",
            System.currentTimeMillis());

        assertTrue(r.isSuccess());
        assertEquals("TRANSFER", r.getTransaction().getType());
    }

    // =========================================================================
    // Promotional / non-transaction rejection
    // =========================================================================

    @Test
    public void testPromoRejected() {
        ParseResult r = service.parse("AD-BANK",
            "Congratulations! You have won a special cashback offer. Click here to claim.",
            System.currentTimeMillis());

        assertFalse(r.isSuccess());
        assertNull(r.getTransaction());
    }

    @Test
    public void testLoanOfferRejected() {
        ParseResult r = service.parse("HDFCBK",
            "Get a personal loan of Rs 5,00,000 from HDFC Bank. Apply now.",
            System.currentTimeMillis());

        assertFalse("Loan offer must not create a transaction", r.isSuccess());
    }

    @Test
    public void testMarketingRejected() {
        ParseResult r = service.parse("AD-PROMO",
            "Exclusive offer! Get 20% discount on your next purchase. Use code SAVE20.",
            System.currentTimeMillis());

        assertFalse(r.isSuccess());
    }

    // =========================================================================
    // Failed transaction handling
    // =========================================================================

    @Test
    public void testFailedTransactionNotStored() {
        ParseResult r = service.parse("HDFCBK",
            "Your transaction of Rs 500 has failed. Amount debited will be refunded.",
            System.currentTimeMillis());

        // Should be detected as FAILED_TRANSACTION
        assertEquals(ParseStatus.FAILED_TRANSACTION, r.getStatus());
        assertNull(r.getTransaction());
    }

    // =========================================================================
    // Edge cases
    // =========================================================================

    @Test
    public void testNullBody() {
        ParseResult r = service.parse("BANK", null, System.currentTimeMillis());
        assertFalse(r.isSuccess());
    }

    @Test
    public void testEmptyBody() {
        ParseResult r = service.parse("BANK", "", System.currentTimeMillis());
        assertFalse(r.isSuccess());
    }

    @Test
    public void testZeroAmount() {
        ParseResult r = service.parse("BANK",
            "INR 0.00 debited from your account.",
            System.currentTimeMillis());
        assertFalse("Zero amount should fail validation", r.isSuccess());
    }

    @Test
    public void testGenericSpentFormat() {
        ParseResult r = service.parse("VM-CARD",
            "You've spent Rs.299.00 at NETFLIX. Avl bal: Rs.5,201.00",
            System.currentTimeMillis());

        assertTrue(r.isSuccess());
        assertEquals(299.0, r.getTransaction().getAmount(), 0.001);
        assertEquals("EXPENSE", r.getTransaction().getType());
    }

    @Test
    public void testDebitAndCreditBothPresent() {
        ParseResult r = service.parse("ICICIB",
            "ICICI Bank Acct XX110 debited for Rs 50.00; TANISHA KHANDEL credited. UPI:99887766.",
            System.currentTimeMillis());

        assertTrue(r.isSuccess());
        assertEquals("EXPENSE", r.getTransaction().getType());
    }

    @Test
    public void testRupeeSymbolFormat() {
        ParseResult r = service.parse("BANK",
            "₹2,500 has been debited from your account XX4321 via UPI.",
            System.currentTimeMillis());

        assertTrue(r.isSuccess());
        assertEquals(2500.0, r.getTransaction().getAmount(), 0.001);
    }

    @Test
    public void testPartialMessage() {
        ParseResult r = service.parse("BANK",
            "debited Rs 200", System.currentTimeMillis());

        assertTrue(r.isSuccess());
        assertEquals(200.0, r.getTransaction().getAmount(), 0.001);
    }

    // =========================================================================
    // ParseResult structure
    // =========================================================================

    @Test
    public void testParseResultHasBank() {
        ParseResult r = service.parse("HDFCBK",
            "Rs 500 debited from HDFC Bank A/c XX1234.",
            System.currentTimeMillis());

        assertTrue(r.isSuccess());
        assertNotNull(r.getDetectedBank());
        assertEquals("HDFC", r.getDetectedBank());
    }

    @Test
    public void testParseResultHasConfidence() {
        ParseResult r = service.parse("HDFCBK",
            "Rs 500 debited from A/c XX1234 on 28-Aug-26.",
            System.currentTimeMillis());

        assertTrue(r.isSuccess());
        assertTrue("Confidence should be positive", r.getConfidence() > 0);
    }

    // =========================================================================
    // Backward compatibility
    // =========================================================================

    @Test
    public void testParseSMSBackwardCompat() {
        Transaction t = service.parseSMS("HDFCBK",
            "Rs 500 debited from HDFC Bank A/c XX1234.");

        assertNotNull("parseSMS should return Transaction on success", t);
        assertEquals(500.0, t.getAmount(), 0.001);
        assertEquals("EXPENSE", t.getType());
    }


}
