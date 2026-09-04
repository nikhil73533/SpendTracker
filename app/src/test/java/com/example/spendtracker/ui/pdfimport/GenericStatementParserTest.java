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

    @Test
    public void parsesExportedHistoryWithSerialNumberAndTextMonth() {
        String sampleText = "S No Transaction Date Description Amount Type Balance\n" +
                "1 04/Sep/2026 UPI payment to PHARMACY 1,250.00 DR 8,750.00\n" +
                "2 05/Sep/2026 REFUND RECEIVED INR 250.00 CR 9,000.00\n";

        List<RawTransactionRow> rows = parser.parse(sampleText);

        assertEquals(2, rows.size());
        assertEquals("04/Sep/2026", rows.get(0).getDateStr());
        assertEquals(1250.00, rows.get(0).getDebitAmount(), 0.001);
        assertEquals(250.00, rows.get(1).getCreditAmount(), 0.001);
    }

    @Test
    public void parsesTransactionWhenOcrReturnsColumnsOnSeparateLines() {
        String sampleText = "04-09-2026\n" +
                "UPI/DR/123456789/LOCAL STORE/store@upi\n" +
                "₹ 499. 00\n" +
                "DR\n" +
                "12,001.00\n" +
                "05-09-2026\n" +
                "SALARY CREDIT\n" +
                "50,000.00 CR\n" +
                "62,001.00\n";

        List<RawTransactionRow> rows = parser.parse(sampleText);

        assertEquals(2, rows.size());
        assertEquals(499.00, rows.get(0).getDebitAmount(), 0.001);
        assertEquals("store@upi", rows.get(0).getUpiId());
        assertEquals(50000.00, rows.get(1).getCreditAmount(), 0.001);
    }

    @Test
    public void transactionNarrationContainingDepositIsNotDiscardedAsHeader() {
        String sampleText = "Date Description Deposit Balance\n" +
                "06/09/2026 CASH DEPOSIT AT BRANCH 5,000.00 CR 15,000.00\n";

        List<RawTransactionRow> rows = parser.parse(sampleText);

        assertEquals(1, rows.size());
        assertEquals(5000.00, rows.get(0).getCreditAmount(), 0.001);
    }
}
