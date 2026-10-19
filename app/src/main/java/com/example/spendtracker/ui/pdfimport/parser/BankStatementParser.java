package com.example.spendtracker.ui.pdfimport.parser;

import java.util.List;

public interface BankStatementParser {
    /**
     * @return Normalized name of the bank (e.g. "HDFC", "ICICI", "SBI", "Axis", "Generic")
     */
    String getBankName();

    /**
     * Determines whether this parser can handle the statement based on text header / content.
     *
     * @param textHeader First page or top header text
     * @param fullText   Complete text of the PDF document
     * @return true if this parser matches the statement layout
     */
    boolean canParse(String textHeader, String fullText);

    /**
     * Parses the PDF text into raw transaction rows.
     *
     * @param fullText Complete text of the PDF document
     * @return List of extracted raw transaction rows
     */
    List<RawTransactionRow> parse(String fullText);
}
