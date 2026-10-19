package com.example.spendtracker.ui.pdfimport.parser;

import java.util.List;

/** Parser and bank-name detection for statements issued by Indian Bank. */
public class IndianBankStatementParser implements BankStatementParser {
    @Override public String getBankName() { return "Indian Bank"; }
    @Override public boolean canParse(String textHeader, String fullText) {
        String combined = ((textHeader == null ? "" : textHeader) + " " + (fullText == null ? "" : fullText)).toLowerCase();
        return combined.contains("indian bank") || combined.contains("indianbank");
    }
    @Override public List<RawTransactionRow> parse(String fullText) { return IndianStatementRowParser.parse(fullText); }
}
