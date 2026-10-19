package com.example.spendtracker.ui.pdfimport.parser;

import java.util.List;

/** Fallback parser for Indian account statements whose bank-specific layout is unknown. */
public class GenericStatementParser implements BankStatementParser {
    @Override public String getBankName() { return "Bank"; }
    @Override public boolean canParse(String textHeader, String fullText) { return true; }
    @Override public List<RawTransactionRow> parse(String fullText) {
        return IndianStatementRowParser.parse(fullText);
    }
}
