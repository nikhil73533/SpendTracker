package com.example.spendtracker.ui.pdfimport.parser;

import java.util.ArrayList;
import java.util.List;

public class BankStatementParserFactory {

    private final List<BankStatementParser> parsers;
    private final GenericStatementParser genericParser;

    public BankStatementParserFactory() {
        parsers = new ArrayList<>();
        parsers.add(new HDFCStatementParser());
        parsers.add(new ICICIStatementParser());
        parsers.add(new SBIStatementParser());
        parsers.add(new AxisStatementParser());
        genericParser = new GenericStatementParser();
    }

    /**
     * Selects the appropriate parser based on PDF header / content text.
     *
     * @param textHeader Top header text / first page text
     * @param fullText   Complete text of document
     * @return Best matching BankStatementParser
     */
    public BankStatementParser getParser(String textHeader, String fullText) {
        for (BankStatementParser parser : parsers) {
            if (parser.canParse(textHeader, fullText)) {
                return parser;
            }
        }
        return genericParser;
    }

    /**
     * Helper struct holding selected parser and parsed transaction rows.
     */
    public static class ParseOutput {
        public final BankStatementParser parser;
        public final List<RawTransactionRow> rows;

        public ParseOutput(BankStatementParser parser, List<RawTransactionRow> rows) {
            this.parser = parser;
            this.rows = rows;
        }
    }

    /**
     * Parses the PDF text by selecting the appropriate bank parser and falling back if necessary.
     *
     * @param textHeader First page text or header
     * @param fullText   Complete document text
     * @return ParseOutput containing selected parser and extracted rows
     */
    public ParseOutput parse(String textHeader, String fullText) {
        BankStatementParser parser = getParser(textHeader, fullText);
        List<RawTransactionRow> rows = parser.parse(fullText);

        // If specific bank parser yielded 0 rows, fallback to generic parser
        if (rows.isEmpty() && parser != genericParser) {
            List<RawTransactionRow> genericRows = genericParser.parse(fullText);
            if (!genericRows.isEmpty()) {
                return new ParseOutput(genericParser, genericRows);
            }
        }

        return new ParseOutput(parser, rows);
    }
}
