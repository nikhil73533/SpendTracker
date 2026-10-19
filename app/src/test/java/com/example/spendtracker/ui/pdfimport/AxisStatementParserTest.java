package com.example.spendtracker.ui.pdfimport;

import com.example.spendtracker.ui.pdfimport.parser.AxisStatementParser;
import com.example.spendtracker.ui.pdfimport.parser.RawTransactionRow;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public class AxisStatementParserTest {

    private AxisStatementParser parser;

    @Before
    public void setUp() {
        parser = new AxisStatementParser();
    }

    @Test
    public void testBankNameAndDetection() {
        assertEquals("Axis", parser.getBankName());
        assertTrue(parser.canParse("AXIS BANK STATEMENT OF ACCOUNT", "Text"));
    }

    @Test
    public void testParseAxisSampleStatement() {
        String sampleText = "AXIS BANK\n" +
                "STATEMENT OF ACCOUNT\n" +
                "Tran Date Value Date Transaction Details Chq No Amount DR/CR Balance\n" +
                "20-01-2024 20-01-2024 UPI/DR/402011/UBER uber@axis 220.00 DR 14200.00\n" +
                "22-01-2024 22-01-2024 CASH DEPOSIT AT BRANCH 5000.00 CR 19200.00\n";

        List<RawTransactionRow> rows = parser.parse(sampleText);
        assertEquals(2, rows.size());

        RawTransactionRow row1 = rows.get(0);
        assertEquals("20-01-2024", row1.getDateStr());
        assertEquals(220.00, row1.getDebitAmount(), 0.001);
        assertEquals("uber@axis", row1.getUpiId());

        RawTransactionRow row2 = rows.get(1);
        assertEquals("22-01-2024", row2.getDateStr());
        assertEquals(5000.00, row2.getCreditAmount(), 0.001);
    }
}
