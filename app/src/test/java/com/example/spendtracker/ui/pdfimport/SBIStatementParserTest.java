package com.example.spendtracker.ui.pdfimport;

import com.example.spendtracker.ui.pdfimport.parser.RawTransactionRow;
import com.example.spendtracker.ui.pdfimport.parser.SBIStatementParser;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public class SBIStatementParserTest {

    private SBIStatementParser parser;

    @Before
    public void setUp() {
        parser = new SBIStatementParser();
    }

    @Test
    public void testBankNameAndDetection() {
        assertEquals("SBI", parser.getBankName());
        assertTrue(parser.canParse("STATE BANK OF INDIA - ACCOUNT STATEMENT", "Details"));
    }

    @Test
    public void testParseSbiSampleStatement() {
        String sampleText = "STATE BANK OF INDIA\n" +
                "ACCOUNT STATEMENT\n" +
                "Txn Date Value Date Description Ref No. Debit Credit Balance\n" +
                "15 Jan 2024 15 Jan 2024 TO TRANSFER-UPI/401512/ZOMATO zomato@upi 350.00 0.00 18500.00\n" +
                "18 Jan 2024 18 Jan 2024 BY TRANSFER-INB NEFT REF123 0.00 12000.00 30500.00\n";

        List<RawTransactionRow> rows = parser.parse(sampleText);
        assertEquals(2, rows.size());

        RawTransactionRow row1 = rows.get(0);
        assertEquals("15 Jan 2024", row1.getDateStr());
        assertEquals(350.00, row1.getDebitAmount(), 0.001);
        assertEquals("zomato@upi", row1.getUpiId());

        RawTransactionRow row2 = rows.get(1);
        assertEquals("18 Jan 2024", row2.getDateStr());
        assertEquals(12000.00, row2.getCreditAmount(), 0.001);
    }
}
