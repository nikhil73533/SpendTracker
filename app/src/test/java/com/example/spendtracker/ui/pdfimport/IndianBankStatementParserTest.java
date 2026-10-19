package com.example.spendtracker.ui.pdfimport;

import com.example.spendtracker.ui.pdfimport.parser.IndianBankStatementParser;
import com.example.spendtracker.ui.pdfimport.parser.RawTransactionRow;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class IndianBankStatementParserTest {
    @Test
    public void parsesIndianBankDebitAndCreditColumns() {
        IndianBankStatementParser parser = new IndianBankStatementParser();
        String sample = "INDIAN BANK Account Statement\n" +
                "Post Date Value Date Details Debit Credit Balance\n" +
                "04-09-2026 04-09-2026 UPI/DR/123456789012/MEDICAL STORE 450.00 0.00 9,550.00\n" +
                "05-09-2026 05-09-2026 SALARY DEPOSIT 0.00 25,000.00 34,550.00\n";

        assertTrue(parser.canParse(sample, sample));
        List<RawTransactionRow> rows = parser.parse(sample);
        assertEquals(2, rows.size());
        assertEquals(450.00, rows.get(0).getDebitAmount(), 0.001);
        assertEquals(25000.00, rows.get(1).getCreditAmount(), 0.001);
    }
}
