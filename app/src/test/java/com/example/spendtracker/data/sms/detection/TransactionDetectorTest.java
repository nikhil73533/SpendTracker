package com.example.spendtracker.data.sms.detection;

import static org.junit.Assert.*;

import com.example.spendtracker.data.sms.model.DetectionResult;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit tests for {@link TransactionDetector}.
 */
public class TransactionDetectorTest {

    private TransactionDetector detector;

    @Before
    public void setUp() { detector = new TransactionDetector(); }

    @Test
    public void testDebitTransaction() {
        DetectionResult r = detector.detect(
            "Rs 500 debited from your A/c XX1234.",
            "rs 500 debited from your a/c xx1234.");
        assertTrue("Debit transaction must be detected", r.isDetected());
        assertTrue(r.getConfidence() > 0.5);
    }

    @Test
    public void testCreditTransaction() {
        DetectionResult r = detector.detect(
            "INR 5000 credited to your account.",
            "inr 5000 credited to your account.");
        assertTrue(r.isDetected());
    }

    @Test
    public void testUPITransaction() {
        DetectionResult r = detector.detect(
            "Rs 199 spent via UPI. Ref: 123456789.",
            "rs 199 spent via upi. ref: 123456789.");
        assertTrue(r.isDetected());
    }

    @Test
    public void testPromoRejected() {
        DetectionResult r = detector.detect(
            "Congratulations! You have won a cashback of Rs 500. Click here to claim.",
            "congratulations! you have won a cashback of rs 500. click here to claim.");
        assertFalse("Promotional SMS must not be detected as transaction", r.isDetected());
    }

    @Test
    public void testLoanOfferRejected() {
        DetectionResult r = detector.detect(
            "Get a personal loan of Rs 5,00,000 from HDFC Bank. Apply now.",
            "get a personal loan of rs 5,00,000 from hdfc bank. apply now.");
        assertFalse("Loan offer must not be detected as transaction", r.isDetected());
    }

    @Test
    public void testRandomTextRejected() {
        DetectionResult r = detector.detect(
            "Hello, your appointment is confirmed for tomorrow at 10 AM.",
            "hello, your appointment is confirmed for tomorrow at 10 am.");
        assertFalse(r.isDetected());
    }

    @Test
    public void testLoginAlertWithoutFinancial() {
        DetectionResult r = detector.detect(
            "Alert: Login attempt detected on your account.",
            "alert: login attempt detected on your account.");
        assertFalse(r.isDetected());
    }
}
