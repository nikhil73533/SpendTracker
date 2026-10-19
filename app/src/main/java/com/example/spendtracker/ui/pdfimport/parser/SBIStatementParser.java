package com.example.spendtracker.ui.pdfimport.parser;

import java.util.List;

public class SBIStatementParser implements BankStatementParser {
    @Override public String getBankName() { return "SBI"; }
    @Override public boolean canParse(String textHeader, String fullText) {
        String combined = ((textHeader == null ? "" : textHeader) + " " + (fullText == null ? "" : fullText)).toLowerCase();
        return combined.contains("state bank of india") || combined.contains("sbi ")
                || combined.contains("sbi\n") || combined.contains("sbi-");
    }
    @Override public List<RawTransactionRow> parse(String fullText) { return IndianStatementRowParser.parse(fullText); }
}
