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
}
