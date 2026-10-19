package com.example.spendtracker.ui.pdfimport.parser;

import java.util.List;

public class AxisStatementParser implements BankStatementParser {
    @Override public String getBankName() { return "Axis"; }
    @Override public boolean canParse(String textHeader, String fullText) {
        String combined = ((textHeader == null ? "" : textHeader) + " " + (fullText == null ? "" : fullText)).toLowerCase();
        return combined.contains("axis bank") || combined.contains("axisbank");
    }
    @Override public List<RawTransactionRow> parse(String fullText) { return IndianStatementRowParser.parse(fullText); }
}
