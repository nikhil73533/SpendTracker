package com.example.spendtracker.ui.pdfimport;

import com.example.spendtracker.ui.pdfimport.parser.BankStatementParserFactory;
import com.example.spendtracker.ui.pdfimport.parser.HDFCStatementParser;
import com.example.spendtracker.ui.pdfimport.parser.ICICIStatementParser;
import com.example.spendtracker.ui.pdfimport.parser.GenericStatementParser;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class BankStatementParserFactoryTest {

    private BankStatementParserFactory factory;

    @Before
    public void setUp() {
        factory = new BankStatementParserFactory();
    }

    @Test
    public void testParserSelection() {
        assertTrue(factory.getParser("HDFC BANK STATEMENT", "Text") instanceof HDFCStatementParser);
        assertTrue(factory.getParser("ICICI BANK STATEMENT", "Text") instanceof ICICIStatementParser);
        assertTrue(factory.getParser("CUSTOM STATEMENT", "Text") instanceof GenericStatementParser);
    }

    @Test
    public void genericFallbackPreservesDetectedBank() {
        String text = "HDFC BANK\n" +
                "1 04/Sep/2026 UPI payment 500.00 DR 10,000.00\n";

        BankStatementParserFactory.ParseOutput output = factory.parse("HDFC BANK", text);

        assertEquals("HDFC", output.parser.getBankName());
        assertEquals(1, output.rows.size());
    }

    @Test
    public void parsesIciciOpTransactionHistoryWhenPdfColumnsAreSplitAcrossLines() {
        String text = "ICICI BANK DETAILED STATEMENT\n" +
                "S No. Value Date Transaction Date Cheque Number Transaction Remarks " +
                "Withdrawal Amount(INR) Deposit Amount(INR) Balance(INR)\n" +
                "31/10/2025\n" +
                "31/10/2025\n" +
                "NEFT-ICICN00299844006936 SALARY FOR OCT25\n" +
                "0.00\n" +
                "96,468.00\n" +
                "229,585.98\n" +
                "1 KUDUM MURALI\n" +
                "04/10/2025\n" +
                "04/10/2025\n" +
                "UPI/506301540694/PAYMENT\n" +
                "200.00\n" +
                "0.00\n" +
                "228,730.88\n";

        BankStatementParserFactory.ParseOutput output = factory.parse("ICICI BANK DETAILED STATEMENT", text);

        assertEquals("ICICI", output.parser.getBankName());
        assertEquals(2, output.rows.size());
        assertEquals(96468.00, output.rows.get(0).getCreditAmount(), 0.001);
        assertEquals(200.00, output.rows.get(1).getDebitAmount(), 0.001);
    }
}
