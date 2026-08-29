package com.example.spendtracker.data.sms.extraction;

import static org.junit.Assert.*;

import com.example.spendtracker.data.sms.model.ExtractionResult;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit tests for {@link AmountExtractor}.
 */
public class AmountExtractorTest {

    private AmountExtractor extractor;

    @Before
    public void setUp() { extractor = new AmountExtractor(); }

    @Test
    public void testINRPrefix() {
        ExtractionResult<Double> r = extractor.extract("INR 1,500.50 debited");
        assertTrue(r.isPresent());
        assertEquals(1500.50, r.getValue(), 0.001);
    }

    @Test
    public void testRsDot() {
        ExtractionResult<Double> r = extractor.extract("Rs. 999 charged");
        assertTrue(r.isPresent());
        assertEquals(999.0, r.getValue(), 0.001);
    }

    @Test
    public void testRupeeSymbol() {
        ExtractionResult<Double> r = extractor.extract("₹250 paid");
        assertTrue(r.isPresent());
        assertEquals(250.0, r.getValue(), 0.001);
    }

    @Test
    public void testRupeeWithSpace() {
        ExtractionResult<Double> r = extractor.extract("₹ 2,500 debited");
        assertTrue(r.isPresent());
        assertEquals(2500.0, r.getValue(), 0.001);
    }

    @Test
    public void testIndianCommaFormat() {
        ExtractionResult<Double> r = extractor.extract("INR 1,05,000.00 debited");
        assertTrue(r.isPresent());
        assertEquals(105000.0, r.getValue(), 0.001);
    }

    @Test
    public void testDrPrefix() {
        ExtractionResult<Double> r = extractor.extract("Dr INR 148.00 - AU A/c");
        assertTrue(r.isPresent());
        assertEquals(148.0, r.getValue(), 0.001);
    }

    @Test
    public void testCrPrefix() {
        ExtractionResult<Double> r = extractor.extract("Cr INR 2500.00 - AU A/c");
        assertTrue(r.isPresent());
        assertEquals(2500.0, r.getValue(), 0.001);
    }

    @Test
    public void testNoAmountReturnsEmpty() {
        ExtractionResult<Double> r = extractor.extract("Hello world");
        assertFalse(r.isPresent());
    }

    @Test
    public void testHugeAmountRejected() {
        ExtractionResult<Double> r = extractor.extract("INR 99999999.00 debited");
        assertFalse("Amount > 10M must be rejected", r.isPresent());
    }

    @Test
    public void testNoDecimal() {
        ExtractionResult<Double> r = extractor.extract("Rs 5000 credited");
        assertTrue(r.isPresent());
        assertEquals(5000.0, r.getValue(), 0.001);
    }

    @Test
    public void testSmallAmount() {
        ExtractionResult<Double> r = extractor.extract("₹1 debited");
        assertTrue(r.isPresent());
        assertEquals(1.0, r.getValue(), 0.001);
    }
}
