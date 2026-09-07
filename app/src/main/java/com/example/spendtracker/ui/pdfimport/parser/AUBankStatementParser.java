package com.example.spendtracker.ui.pdfimport.parser;

import java.util.List;

/** Parser and bank-name detection for AU Small Finance Bank statements. */
public class AUBankStatementParser implements BankStatementParser {
    @Override public String getBankName() { return "AU Bank"; }

    @Override
    public boolean canParse(String textHeader, String fullText) {
        String combined = ((textHeader == null ? "" : textHeader) + " "
                + (fullText == null ? "" : fullText)).toLowerCase();
        return combined.contains("au small finance bank") || combined.contains("au bank")
                || combined.contains("aubank") || combined.contains("aubfin");
    }

    @Override public List<RawTransactionRow> parse(String fullText) {
        return IndianStatementRowParser.parse(fullText);
    }
}
