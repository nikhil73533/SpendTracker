package com.example.spendtracker.ui.pdfimport;

import com.example.spendtracker.ui.pdfimport.parser.ICICIStatementParser;
import com.example.spendtracker.ui.pdfimport.parser.RawTransactionRow;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public class ICICIStatementParserTest {

    private ICICIStatementParser parser;

    @Before
    public void setUp() {
        parser = new ICICIStatementParser();
    }

    @Test
    public void testBankNameAndDetection() {
        assertEquals("ICICI", parser.getBankName());
        assertTrue(parser.canParse("ICICI BANK STATEMENT", "Details"));
        assertFalse(parser.canParse("AXIS BANK", "Details"));
    }

    @Test
    public void testParseIciciSampleStatement() {
        String sampleText = "ICICI BANK LIMITED\n" +
                "Statement of Transactions\n" +
                "Date Remarks Withdrawal Deposit Balance\n" +
                "10/01/2024 UPI/123456/AMAZON/amazon@apl/Payment 1500.00 0.00 45000.00\n" +
                "12/01/2024 INF/NEFT/INCOME FROM CLIENT 0.00 25000.00 70000.00 CR\n";

        List<RawTransactionRow> rows = parser.parse(sampleText);
        assertEquals(2, rows.size());

        RawTransactionRow row1 = rows.get(0);
        assertEquals("10/01/2024", row1.getDateStr());
        assertEquals(1500.00, row1.getDebitAmount(), 0.001);
        assertEquals("amazon@apl", row1.getUpiId());

        RawTransactionRow row2 = rows.get(1);
        assertEquals("12/01/2024", row2.getDateStr());
        assertEquals(25000.00, row2.getCreditAmount(), 0.001);
    }
}
