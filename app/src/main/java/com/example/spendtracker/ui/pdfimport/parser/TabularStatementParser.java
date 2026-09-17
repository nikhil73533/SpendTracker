package com.example.spendtracker.ui.pdfimport.parser;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses reconstructed cells without guessing an amount from narration or running balance. */
final class TabularStatementParser {
    private TabularStatementParser() { }

    static List<RawTransactionRow> parse(String text) {
        List<RawTransactionRow> rows = new ArrayList<>();
        String[] header = null;
        for (String line : text.split("\\r?\\n")) {
            if (!line.contains("\t")) continue;
            String[] cells = line.split("\t", -1);
            if (java.util.Arrays.asList(cells).contains("DATE") && (line.contains("\tDEBIT") || line.contains("\tAMOUNT")
                    || line.contains("\tCREDIT"))) {
                header = cells;
                continue;
            }
            if (header == null || cells.length != header.length) continue;
            String date = StatementFields.date(cell(header, cells, "DATE"));
            String narration = cell(header, cells, "NARRATION");
            if (date == null || narration.toUpperCase(Locale.ENGLISH).matches(
                    ".*(?:OPENING BALANCE|CLOSING BALANCE|BROUGHT FORWARD|CARRIED FORWARD|B/F|C/F).*")) continue;
            Double debit = StatementFields.amount(cell(header, cells, "DEBIT"));
            Double credit = StatementFields.amount(cell(header, cells, "CREDIT"));
            if (debit == null || credit == null || debit < 0 || credit < 0) continue;
            if (debit > 0 && credit > 0) continue; // Conflicting cells require a better extraction pass.
            if (debit == 0 && credit == 0) {
                String amountText = cell(header, cells, "AMOUNT");
                Double amount = StatementFields.amount(amountText);
                if (amount == null || amount == 0) continue;
                String direction = StatementFields.direction(cell(header, cells, "TYPE"));
                if (direction.isEmpty()) direction = StatementFields.direction(amountText);
                if (direction.isEmpty()) direction = StatementFields.direction(narration);
                if (direction.isEmpty() && amount < 0) direction = "DEBIT";
                if (direction.isEmpty() && amountText.trim().startsWith("+")) direction = "CREDIT";
                if (direction.isEmpty()) continue;
                if (direction.equals("DEBIT")) debit = Math.abs(amount);
                else credit = Math.abs(amount);
            }
            String reference = cell(header, cells, "REFERENCE").replaceAll("\\s+", "");
            if (reference.matches("(?i)0+|[-–—]+|N/?A|NIL|NOTAVAILABLE")) reference = "";
            if (reference.isEmpty()) {
                Matcher ref = Pattern.compile("(?i)\\b(?:UPI[/\\-](?:(?:DR|CR)[/\\-])?|(?:UTR|REF|RRN)[ :/#-]+)([A-Z0-9]{6,30})")
                        .matcher(narration);
                if (ref.find()) reference = ref.group(1);
            }
            Matcher upi = Pattern.compile("(?i)([A-Z0-9._%+-]+@[A-Z0-9.-]+)").matcher(narration);
            String balanceText = cell(header, cells, "BALANCE");
            Double balance = balanceText.trim().isEmpty() ? null : StatementFields.amount(balanceText);
            rows.add(new RawTransactionRow(date, narration, reference, upi.find() ? upi.group(1) : "",
                    debit > 0 ? debit : null, credit > 0 ? credit : null, balance, line));
        }
        return rows;
    }

    private static String cell(String[] header, String[] cells, String key) {
        for (int i = 0; i < header.length; i++) if (header[i].equals(key)) return cells[i].trim();
        return "";
    }
}
