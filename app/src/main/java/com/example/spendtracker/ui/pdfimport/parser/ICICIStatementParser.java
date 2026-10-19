package com.example.spendtracker.ui.pdfimport.parser;

import java.util.List;

public class ICICIStatementParser implements BankStatementParser {
    @Override public String getBankName() { return "ICICI"; }
    @Override public boolean canParse(String textHeader, String fullText) {
        String combined = ((textHeader == null ? "" : textHeader) + " " + (fullText == null ? "" : fullText)).toLowerCase();
        return combined.contains("icici bank") || combined.contains("icicibank");
    }
    @Override public List<RawTransactionRow> parse(String fullText) { return IndianStatementRowParser.parse(fullText); }
}
