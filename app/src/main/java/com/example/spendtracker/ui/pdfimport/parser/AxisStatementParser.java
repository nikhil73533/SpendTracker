package com.example.spendtracker.ui.pdfimport.parser;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AxisStatementParser implements BankStatementParser {

    private static final String BANK_NAME = "Axis";

    private static final Pattern DATE_PATTERN = Pattern.compile(
            "^(\\d{2}[/\\-]\\d{2}[/\\-]\\d{2,4})\\b");

    private static final Pattern AMOUNT_PATTERN = Pattern.compile("[0-9,]+\\.\\d{2}");

    private static final String[] IGNORE_KEYWORDS = {
        "AXIS BANK", "STATEMENT OF ACCOUNT", "Page ", "CLOSING BALANCE", "OPENING BALANCE",
        "Tran Date", "Value Date", "Transaction Details", "Chq No", "Amount", "DR/CR", "Balance"
    };

    @Override
    public String getBankName() {
        return BANK_NAME;
    }

    @Override
    public boolean canParse(String textHeader, String fullText) {
        if (textHeader == null && fullText == null) return false;
        String combined = ((textHeader != null ? textHeader : "") + " " + (fullText != null ? fullText : "")).toLowerCase();
        return combined.contains("axis bank") || combined.contains("axisbank");
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

            Matcher dateMatcher = DATE_PATTERN.matcher(trimmed);
            if (dateMatcher.find()) {
                if (current != null && isRowValid(current)) {
                    rows.add(current);
                }

                current = parseAxisLine(trimmed, dateMatcher.group(1));
            } else if (current != null) {
                if (!trimmed.matches(".*(?:Page|Total|Summary).*")) {
                    current.setNarration(current.getNarration() + " " + trimmed.replaceAll("\\s+", " "));
                    current.setRawLine(current.getRawLine() + "\n" + trimmed);
                }
            }
        }

        if (current != null && isRowValid(current)) {
            rows.add(current);
        }

        return rows;
    }

    private RawTransactionRow parseAxisLine(String line, String dateStr) {
        RawTransactionRow row = new RawTransactionRow();
        row.setDateStr(dateStr);
        row.setRawLine(line);

        List<String> amounts = new ArrayList<>();
        Matcher am = AMOUNT_PATTERN.matcher(line);
        while (am.find()) {
            amounts.add(am.group().replace(",", ""));
        }

        boolean isCredit = line.toUpperCase().contains(" CR") || line.toUpperCase().endsWith(" CR") ||
                           line.toLowerCase().contains("credited") || line.toLowerCase().contains("deposit");

        double debit = 0.0;
        double credit = 0.0;
        Double balance = null;

        if (amounts.size() >= 3) {
            double a1 = parseDouble(amounts.get(amounts.size() - 3));
            double a2 = parseDouble(amounts.get(amounts.size() - 2));
            balance = parseDouble(amounts.get(amounts.size() - 1));

            if (a1 > 0 && a2 == 0) debit = a1;
            else if (a2 > 0 && a1 == 0) credit = a2;
            else if (isCredit) credit = a2 > 0 ? a2 : a1;
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

        String contentAfterDate = line.substring(dateStr.length()).trim();
        for (String amtStr : amounts) {
            int idx = contentAfterDate.lastIndexOf(amtStr);
            if (idx > 0) {
                contentAfterDate = contentAfterDate.substring(0, idx).trim();
            }
        }

        row.setNarration(contentAfterDate);
        row.setUpiId(extractUpiId(line));
        row.setReferenceNo(extractRefNo(line));

        return row;
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
        for (String kw : IGNORE_KEYWORDS) {
            if (line.contains(kw)) return true;
        }
        return false;
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
