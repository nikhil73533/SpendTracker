package com.example.spendtracker.ui.pdfimport;

import com.example.spendtracker.ui.pdfimport.parser.GenericStatementParser;
import com.example.spendtracker.ui.pdfimport.parser.RawTransactionRow;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public class GenericStatementParserTest {

    private GenericStatementParser parser;

    @Before
    public void setUp() {
        parser = new GenericStatementParser();
    }

    @Test
    public void testGenericParser() {
        String sampleText = "UNKNOWN COOPERATIVE BANK\n" +
                "Date Description Amount Balance\n" +
                "2024-02-01 GROCERY STORE PURCHASE 850.00 12000.00\n" +
                "2024-02-02 SALARY CREDIT 45000.00 CR 57000.00\n";

        List<RawTransactionRow> rows = parser.parse(sampleText);
        assertEquals(2, rows.size());

        RawTransactionRow row1 = rows.get(0);
        assertEquals("2024-02-01", row1.getDateStr());
        assertEquals(850.00, row1.getDebitAmount(), 0.001);

        RawTransactionRow row2 = rows.get(1);
        assertEquals("2024-02-02", row2.getDateStr());
        assertEquals(45000.00, row2.getCreditAmount(), 0.001);
    }
}
