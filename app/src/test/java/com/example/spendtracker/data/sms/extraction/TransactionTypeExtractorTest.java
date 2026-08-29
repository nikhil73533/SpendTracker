package com.example.spendtracker.data.sms.extraction;

import static org.junit.Assert.*;

import com.example.spendtracker.data.sms.model.ExtractionResult;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit tests for {@link TransactionTypeExtractor}.
 */
public class TransactionTypeExtractorTest {

    private TransactionTypeExtractor extractor;

    @Before
    public void setUp() { extractor = new TransactionTypeExtractor(); }

    @Test
    public void testDebitKeyword() {
        ExtractionResult<String> r = extractor.extract(
            "Rs 500 debited from your account.", "rs 500 debited from your account.");
        assertTrue(r.isPresent());
        assertEquals("EXPENSE", r.getValue());
    }

    @Test
    public void testCreditKeyword() {
        ExtractionResult<String> r = extractor.extract(
            "INR 5000 credited to your account.", "inr 5000 credited to your account.");
        assertTrue(r.isPresent());
        assertEquals("INCOME", r.getValue());
    }

    @Test
    public void testBothDebitAndCreditDebitWins() {
        ExtractionResult<String> r = extractor.extract(
            "ICICI Bank Acct XX110 debited for Rs 50.00; TANISHA KHANDEL credited.",
            "icici bank acct xx110 debited for rs 50.00; tanisha khandel credited.");
        assertTrue(r.isPresent());
        assertEquals("EXPENSE", r.getValue());
    }

    @Test
    public void testTransferKeyword() {
        ExtractionResult<String> r = extractor.extract(
            "Rs 10000 transferred from A/c XX1234 to A/c XX5678.",
            "rs 10000 transferred from a/c xx1234 to a/c xx5678.");
        assertTrue(r.isPresent());
        assertEquals("TRANSFER", r.getValue());
    }

    @Test
    public void testFundTransfer() {
        ExtractionResult<String> r = extractor.extract(
            "Fund Transfer of INR 5000 successful.",
            "fund transfer of inr 5000 successful.");
        assertTrue(r.isPresent());
        assertEquals("TRANSFER", r.getValue());
    }

    @Test
    public void testSelfTransfer() {
        ExtractionResult<String> r = extractor.extract(
            "Self transfer completed from Savings to Current account.",
            "self transfer completed from savings to current account.");
        assertTrue(r.isPresent());
        assertEquals("TRANSFER", r.getValue());
    }

    @Test
    public void testSpentKeyword() {
        ExtractionResult<String> r = extractor.extract(
            "You spent Rs 299 at Netflix.", "you spent rs 299 at netflix.");
        assertTrue(r.isPresent());
        assertEquals("EXPENSE", r.getValue());
    }

    @Test
    public void testReceivedKeyword() {
        ExtractionResult<String> r = extractor.extract(
            "INR 3000 received from John.", "inr 3000 received from john.");
        assertTrue(r.isPresent());
        assertEquals("INCOME", r.getValue());
    }

    @Test
    public void testNoKeywordsReturnsEmpty() {
        ExtractionResult<String> r = extractor.extract(
            "Your balance is Rs 12000.", "your balance is rs 12000.");
        assertFalse(r.isPresent());
    }
}
