package com.example.spendtracker.data.sms.validation;

import static org.junit.Assert.*;

import com.example.spendtracker.data.sms.model.ParsedTransaction;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit tests for {@link TransactionValidator}.
 */
public class TransactionValidatorTest {

    private TransactionValidator validator;

    @Before
    public void setUp() { validator = new TransactionValidator(); }

    @Test
    public void testValidTransaction() {
        ParsedTransaction p = new ParsedTransaction();
        p.setAmount(500.0);
        p.setAmountConfidence(0.99);
        p.setTransactionType("EXPENSE");
        p.setTransactionTypeConfidence(0.95);
        p.setSmsTimestamp(System.currentTimeMillis());

        TransactionValidator.ValidationResult r = validator.validate(p);
        assertTrue(r.isValid());
        assertTrue(r.getErrors().isEmpty());
    }

    @Test
    public void testMissingAmount() {
        ParsedTransaction p = new ParsedTransaction();
        p.setTransactionType("EXPENSE");
        p.setTransactionTypeConfidence(0.95);
        p.setSmsTimestamp(System.currentTimeMillis());

        TransactionValidator.ValidationResult r = validator.validate(p);
        assertFalse(r.isValid());
        assertTrue(r.getErrors().stream().anyMatch(e -> e.contains("Amount")));
    }

    @Test
    public void testZeroAmount() {
        ParsedTransaction p = new ParsedTransaction();
        p.setAmount(0.0);
        p.setTransactionType("EXPENSE");
        p.setSmsTimestamp(System.currentTimeMillis());

        TransactionValidator.ValidationResult r = validator.validate(p);
        assertFalse(r.isValid());
    }

    @Test
    public void testHugeAmount() {
        ParsedTransaction p = new ParsedTransaction();
        p.setAmount(99_999_999.0);
        p.setTransactionType("EXPENSE");
        p.setSmsTimestamp(System.currentTimeMillis());

        TransactionValidator.ValidationResult r = validator.validate(p);
        assertFalse(r.isValid());
    }

    @Test
    public void testMissingType() {
        ParsedTransaction p = new ParsedTransaction();
        p.setAmount(500.0);
        p.setSmsTimestamp(System.currentTimeMillis());

        TransactionValidator.ValidationResult r = validator.validate(p);
        assertFalse(r.isValid());
    }

    @Test
    public void testInvalidType() {
        ParsedTransaction p = new ParsedTransaction();
        p.setAmount(500.0);
        p.setTransactionType("INVALID_TYPE");
        p.setSmsTimestamp(System.currentTimeMillis());

        TransactionValidator.ValidationResult r = validator.validate(p);
        assertFalse(r.isValid());
    }

    @Test
    public void testTransferWithoutAccountsWarns() {
        ParsedTransaction p = new ParsedTransaction();
        p.setAmount(5000.0);
        p.setAmountConfidence(0.95);
        p.setTransactionType("TRANSFER");
        p.setTransactionTypeConfidence(0.90);
        p.setSmsTimestamp(System.currentTimeMillis());

        TransactionValidator.ValidationResult r = validator.validate(p);
        assertTrue(r.isValid());
        assertTrue(r.getWarnings().stream().anyMatch(w -> w.contains("Transfer")));
    }

    @Test
    public void testFailedStatusWarns() {
        ParsedTransaction p = new ParsedTransaction();
        p.setAmount(500.0);
        p.setAmountConfidence(0.95);
        p.setTransactionType("EXPENSE");
        p.setTransactionTypeConfidence(0.95);
        p.setTransactionStatus("FAILED");
        p.setSmsTimestamp(System.currentTimeMillis());

        TransactionValidator.ValidationResult r = validator.validate(p);
        assertTrue(r.isValid());
        assertTrue(r.getWarnings().stream().anyMatch(w -> w.contains("FAILED")));
    }
}
