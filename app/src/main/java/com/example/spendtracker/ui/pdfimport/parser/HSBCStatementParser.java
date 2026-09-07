package com.example.spendtracker.ui.pdfimport.parser;

import java.util.List;

/** Parser and bank-name detection for HSBC statements. */
public class HSBCStatementParser implements BankStatementParser {
    @Override public String getBankName() { return "HSBC"; }

    @Override
    public boolean canParse(String textHeader, String fullText) {
        String combined = ((textHeader == null ? "" : textHeader) + " "
                + (fullText == null ? "" : fullText)).toLowerCase();
        return combined.contains("hsbc") || combined.contains("hongkong and shanghai banking");
    }

    @Override public List<RawTransactionRow> parse(String fullText) {
        return IndianStatementRowParser.parse(fullText);
    }
}
