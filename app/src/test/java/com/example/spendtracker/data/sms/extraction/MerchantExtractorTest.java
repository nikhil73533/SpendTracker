package com.example.spendtracker.data.sms.extraction;

import static org.junit.Assert.*;

import com.example.spendtracker.data.sms.model.ExtractionResult;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit tests for {@link MerchantExtractor}.
 */
public class MerchantExtractorTest {

    private MerchantExtractor extractor;

    @Before
    public void setUp() { extractor = new MerchantExtractor(); }

    @Test
    public void testAUBankFormat() {
        ExtractionResult<String> r = extractor.extract(
            "UPI/DR/687943750944/Aryan medical/YESB", "AU Bank");
        assertTrue(r.isPresent());
        assertEquals("Aryan medical", r.getValue());
    }

    @Test
    public void testICICIUpiFormat() {
        ExtractionResult<String> r = extractor.extract(
            "ICICI Bank Acct XX110 debited for Rs 10.00 for UPI-658011591943-TANISHA KHANDEL.",
            "ICICI");
        assertTrue(r.isPresent());
        assertEquals("TANISHA KHANDEL", r.getValue());
    }

    @Test
    public void testCreditorPattern() {
        ExtractionResult<String> r = extractor.extract(
            "TANISHA KHANDEL credited. UPI:99887766.", null);
        assertTrue(r.isPresent());
        assertEquals("TANISHA KHANDEL", r.getValue());
    }

    @Test
    public void testPaidToPattern() {
        ExtractionResult<String> r = extractor.extract(
            "Rs 500 paid to Zomato on 28-Aug-26.", null);
        assertTrue(r.isPresent());
        assertEquals("Zomato", r.getValue());
    }

    @Test
    public void testBankNameNotExtracted() {
        ExtractionResult<String> r = extractor.extract(
            "HDFC Bank credited to your account.", "HDFC");
        // "HDFC Bank" should be rejected as merchant
        assertFalse("Bank name itself should not be extracted as merchant", r.isPresent());
    }

    @Test
    public void testEmptyMessage() {
        ExtractionResult<String> r = extractor.extract("", null);
        assertFalse(r.isPresent());
    }
}
