package com.example.spendtracker.ui.pdfimport.parser;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GenericStatementParser implements BankStatementParser {

    private static final String BANK_NAME = "Bank";

    private static final String DATE_EXPRESSION =
            "(?:\\d{4}[-/.]\\d{1,2}[-/.]\\d{1,2}"
                    + "|\\d{1,2}[-/.](?:\\d{1,2}|[A-Za-z]{3,9})[-/.]\\d{2,4}"
                    + "|\\d{1,2}(?:\\s+|-)[A-Za-z]{3,9}(?:\\s+|-)\\d{2,4})";

    // A serial-number column is common in exported transaction-history PDFs.
    private static final Pattern ROW_START_PATTERN = Pattern.compile(
            "^(?:\\s*\\d{1,4}[.)]?\\s+)?(" + DATE_EXPRESSION + ")(?=\\s|$)",
            Pattern.CASE_INSENSITIVE);

    // Accept Indian grouping, optional currency labels, OCR spacing around decimals, and DR/CR suffixes.
    private static final Pattern AMOUNT_PATTERN = Pattern.compile(
            "(?i)(?:₹|INR\\s*|RS\\.?\\s*)?\\(?[+-]?(?:\\d{1,3}(?:,\\d{2,3})+|\\d+)"
                    + "(?:[.]\\s*\\d{1,2})\\)?(?:\\s*(?:CR|DR))?");

    private static final String[] METADATA_KEYWORDS = {
        "STATEMENT OF ACCOUNT", "ACCOUNT STATEMENT", "CLOSING BALANCE", "OPENING BALANCE"
    };

    private static final String[] COLUMN_KEYWORDS = {
        "DATE", "NARRATION", "DESCRIPTION", "WITHDRAWAL", "DEPOSIT", "BALANCE", "TRANSACTION DETAILS",
        "DEBIT", "CREDIT", "AMOUNT", "DR/CR"
    };

    @Override
    public String getBankName() {
        return BANK_NAME;
    }

    @Override
    public boolean canParse(String textHeader, String fullText) {
        // Fallback parser accepts any text
        return true;
    }

    @Override
    public List<RawTransactionRow> parse(String fullText) {
        List<RawTransactionRow> rows = new ArrayList<>();
        if (fullText == null || fullText.trim().isEmpty()) return rows;

        String[] lines = fullText.split("\\r?\\n");
        RawTransactionRow current = null;

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;

            if (shouldIgnoreLine(trimmed)) {
                continue;
            }

            Matcher dateMatcher = ROW_START_PATTERN.matcher(trimmed);
            if (dateMatcher.find()) {
                addIfValid(rows, current);

                current = startRow(trimmed, dateMatcher.group(1), dateMatcher.end());
            } else if (current != null) {
                if (!Pattern.compile("(?:Page|Total|Summary)", Pattern.CASE_INSENSITIVE).matcher(trimmed).find()) {
                    current.setNarration(current.getNarration() + " " + trimmed.replaceAll("\\s+", " "));
                    current.setRawLine(current.getRawLine() + "\n" + trimmed);
                }
            }
        }

        addIfValid(rows, current);

        return rows;
    }

    private RawTransactionRow startRow(String line, String dateStr, int contentStart) {
        RawTransactionRow row = new RawTransactionRow();
        row.setDateStr(dateStr);
        row.setRawLine(line);
        row.setNarration(contentStart < line.length() ? line.substring(contentStart).trim() : "");
        return row;
    }

    private void addIfValid(List<RawTransactionRow> rows, RawTransactionRow row) {
        if (row == null) return;
        populateFields(row);
        if (isRowValid(row)) rows.add(row);
    }

    /** Parse the completed logical row, including any continuation/cell lines appended after its date. */
    private void populateFields(RawTransactionRow row) {
        String fullRow = row.getRawLine().replace('\n', ' ').replaceAll("\\s+", " ").trim();

        List<String> amounts = new ArrayList<>();
        Matcher am = AMOUNT_PATTERN.matcher(fullRow);
        while (am.find()) {
            amounts.add(normalizeAmount(am.group()));
        }

        String upperLine = fullRow.toUpperCase();
        String lowerLine = fullRow.toLowerCase();

        boolean isCredit = Pattern.compile("(?:^|\\s)(?:CR|CREDIT)(?:\\s|$)", Pattern.CASE_INSENSITIVE).matcher(fullRow).find()
                || lowerLine.contains("credited") || lowerLine.contains("deposit")
                || lowerLine.contains("by transfer") || lowerLine.contains("received")
                || lowerLine.contains("refund");
        boolean isDebit = Pattern.compile("(?:^|\\s)(?:DR|DEBIT)(?:\\s|$)", Pattern.CASE_INSENSITIVE).matcher(fullRow).find()
                || lowerLine.contains("debited") || lowerLine.contains("withdrawal")
                || lowerLine.contains("purchase") || lowerLine.contains("paid to");

        double debit = 0.0;
        double credit = 0.0;
        Double balance = null;

        if (amounts.size() >= 3) {
            double a1 = parseDouble(amounts.get(amounts.size() - 3));
            double a2 = parseDouble(amounts.get(amounts.size() - 2));
            balance = parseDouble(amounts.get(amounts.size() - 1));

            if (a1 > 0 && a2 == 0) debit = a1;
            else if (a2 > 0 && a1 == 0) credit = a2;
            else if (isCredit && !isDebit) credit = a2 > 0 ? a2 : a1;
            else debit = a1;
        } else if (amounts.size() == 2) {
            double amt = parseDouble(amounts.get(0));
            balance = parseDouble(amounts.get(1));
            if (isCredit) credit = amt;
            else debit = amt;
        } else if (amounts.size() == 1) {
            double amt = parseDouble(amounts.get(0));
            if (isCredit) credit = amt;
            else debit = amt;
        }

        if (debit > 0) row.setDebitAmount(debit);
        if (credit > 0) row.setCreditAmount(credit);
        row.setBalance(balance);

        String narration = row.getNarration().replace('\n', ' ');
        narration = AMOUNT_PATTERN.matcher(narration).replaceAll(" ")
                .replaceAll("(?i)(?:^|\\s)(?:CR|DR)(?=\\s|$)", " ")
                .replaceAll("\\s+", " ").trim();
        row.setNarration(narration);
        row.setUpiId(extractUpiId(fullRow));
        row.setReferenceNo(extractRefNo(fullRow));
    }

    private String normalizeAmount(String value) {
        return value.toUpperCase()
                .replace("₹", "")
                .replace("INR", "")
                .replaceAll("RS\\.?", "")
                .replaceAll("(?:CR|DR)", "")
                .replace("(", "-")
                .replace(")", "")
                .replace(",", "")
                .replaceAll("\\s+", "")
                .trim();
    }

    private String extractUpiId(String line) {
        Matcher matcher = Pattern.compile("\\b([a-zA-Z0-9._%]+@[a-zA-Z0-9.\\-]+)").matcher(line);
        if (matcher.find()) return matcher.group(1);
        return "";
    }

    private String extractRefNo(String line) {
        Matcher matcher = Pattern.compile("\\b(\\d{9,18})\\b").matcher(line);
        if (matcher.find()) return matcher.group(1);
        return "";
    }

    private boolean shouldIgnoreLine(String line) {
        String upper = line.toUpperCase();
        for (String keyword : METADATA_KEYWORDS) {
            if (upper.contains(keyword)) return true;
        }
        if (upper.matches("^PAGE\\s+\\d+.*")) return true;

        // Only treat a line as a table header when several column labels occur together.
        // Transaction narrations such as "CASH DEPOSIT" must remain parseable.
        int columnKeywordCount = 0;
        for (String keyword : COLUMN_KEYWORDS) {
            if (upper.contains(keyword)) columnKeywordCount++;
        }
        return columnKeywordCount >= 2 && !ROW_START_PATTERN.matcher(line).find();
    }

    private boolean isRowValid(RawTransactionRow row) {
        if (row == null || row.getDateStr().isEmpty()) return false;
        return (row.getDebitAmount() != null && row.getDebitAmount() > 0) ||
               (row.getCreditAmount() != null && row.getCreditAmount() > 0);
    }

    private double parseDouble(String str) {
        try { return Double.parseDouble(str.replace(",", "")); }
        catch (NumberFormatException e) { return 0.0; }
    }
}
