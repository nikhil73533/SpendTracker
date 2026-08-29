package com.example.spendtracker.data.sms.normalization;

import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit tests for {@link BankNormalizer}.
 */
public class BankNormalizerTest {

    private BankNormalizer normalizer;

    @Before
    public void setUp() { normalizer = new BankNormalizer(); }

    @Test
    public void testHDFC() { assertEquals("HDFC", normalizer.normalize("HDFC Bank")); }

    @Test
    public void testHDFCLtd() { assertEquals("HDFC", normalizer.normalize("HDFC BANK LTD")); }

    @Test
    public void testICICI() { assertEquals("ICICI", normalizer.normalize("ICICI Bank")); }

    @Test
    public void testICICICase() { assertEquals("ICICI", normalizer.normalize("icici bank")); }

    @Test
    public void testSBI() { assertEquals("SBI", normalizer.normalize("State Bank of India")); }

    @Test
    public void testSBIShort() { assertEquals("SBI", normalizer.normalize("SBI")); }

    @Test
    public void testAxis() { assertEquals("Axis", normalizer.normalize("Axis Bank")); }

    @Test
    public void testKotak() { assertEquals("Kotak", normalizer.normalize("Kotak Mahindra Bank")); }

    @Test
    public void testBankOfBaroda() { assertEquals("Bank of Baroda", normalizer.normalize("bob")); }

    @Test
    public void testOneCard() { assertEquals("OneCard", normalizer.normalize("One Card")); }

    @Test
    public void testPhonePe() { assertEquals("PhonePe", normalizer.normalize("PhonePe")); }

    @Test
    public void testUnknownReturnsAsIs() { assertEquals("Random Bank", normalizer.normalize("Random Bank")); }

    @Test
    public void testNullReturnsNull() { assertNull(normalizer.normalize(null)); }

    @Test
    public void testEmptyReturnsEmpty() { assertEquals("", normalizer.normalize("")); }
}
