package com.example.spendtracker.ui.pdfimport.parser;

import java.util.List;

public class HDFCStatementParser implements BankStatementParser {
    @Override public String getBankName() { return "HDFC"; }
    @Override public boolean canParse(String textHeader, String fullText) {
        String combined = ((textHeader == null ? "" : textHeader) + " " + (fullText == null ? "" : fullText)).toLowerCase();
        return combined.contains("hdfc bank") || combined.contains("hdfcbank");
    }
    @Override public List<RawTransactionRow> parse(String fullText) { return IndianStatementRowParser.parse(fullText); }
}
