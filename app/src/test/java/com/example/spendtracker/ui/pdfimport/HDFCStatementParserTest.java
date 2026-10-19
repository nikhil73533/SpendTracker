package com.example.spendtracker.ui.pdfimport;

import com.example.spendtracker.ui.pdfimport.parser.HDFCStatementParser;
import com.example.spendtracker.ui.pdfimport.parser.RawTransactionRow;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public class HDFCStatementParserTest {

    private HDFCStatementParser parser;

    @Before
    public void setUp() {
        parser = new HDFCStatementParser();
    }

    @Test
    public void testBankNameAndDetection() {
        assertEquals("HDFC", parser.getBankName());
        assertTrue(parser.canParse("HDFC BANK - STATEMENT OF ACCOUNT", "Some text"));
        assertFalse(parser.canParse("STATE BANK OF INDIA", "Some text"));
    }

    @Test
    public void testParseHdfcSampleStatement() {
        String sampleText = "HDFC BANK LIMITED\n" +
                "STATEMENT OF ACCOUNT\n" +
                "Date Narration Chq/Ref No Value Dt Withdrawal Amt Deposit Amt Closing Balance\n" +
                "05/01/24 UPI-SWIGGY-12345678-SWIGGY@OKAXIS 000000123456 05/01/24 450.00 0.00 12500.50\n" +
                "06/01/24 SALARY CREDIT FROM ACME CORP 000000876543 06/01/24 0.00 75000.00 87500.50\n" +
                "07/01/24 ACH D- NETFLIX ENTERTAINMENT\n" +
                "MONTHLY SUBSCRIPTION 000000999111 07/01/24 199.00 0.00 87301.50\n";

        List<RawTransactionRow> rows = parser.parse(sampleText);
        assertEquals(3, rows.size());

        RawTransactionRow row1 = rows.get(0);
        assertEquals("05/01/24", row1.getDateStr());
        assertNotNull(row1.getDebitAmount());
        assertEquals(450.00, row1.getDebitAmount(), 0.001);
        assertNull(row1.getCreditAmount());
        assertEquals("SWIGGY@OKAXIS", row1.getUpiId());

        RawTransactionRow row2 = rows.get(1);
        assertEquals("06/01/24", row2.getDateStr());
        assertNotNull(row2.getCreditAmount());
        assertEquals(75000.00, row2.getCreditAmount(), 0.001);

        RawTransactionRow row3 = rows.get(2);
        assertEquals("07/01/24", row3.getDateStr());
        assertTrue(row3.getNarration().contains("MONTHLY SUBSCRIPTION"));
        assertEquals(199.00, row3.getDebitAmount(), 0.001);
    }
}
