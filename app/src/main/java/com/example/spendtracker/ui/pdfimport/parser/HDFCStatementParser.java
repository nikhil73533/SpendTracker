package com.example.spendtracker.ui.pdfimport.parser;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HDFCStatementParser implements BankStatementParser {

    private static final String BANK_NAME = "HDFC";

    private static final Pattern DATE_PATTERN = Pattern.compile("^(\\d{2}/\\d{2}/(?:\\d{2}|\\d{4}))\\b");
    private static final Pattern AMOUNT_PATTERN = Pattern.compile("[0-9,]+\\.\\d{2}");

    private static final String[] IGNORE_KEYWORDS = {
        "STATEMENT OF ACCOUNT", "HDFC BANK", "Page ", "CLOSING BALANCE", "OPENING BALANCE",
        "Date Narration", "Chq/Ref", "Value Dt", "Withdrawal Amt", "Deposit Amt", "Closing Balance",
        "CONTENTS OF THIS STATEMENT", "REGISTERED OFFICE", "GSTIN", "IFSC Code", "Branch Code"
    };

    @Override
    public String getBankName() {
        return BANK_NAME;
    }

    @Override
    public boolean canParse(String textHeader, String fullText) {
        if (textHeader == null && fullText == null) return false;
        String combined = ((textHeader != null ? textHeader : "") + " " + (fullText != null ? fullText : "")).toLowerCase();
        return combined.contains("hdfc bank") || combined.contains("hdfcbank");
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

                current = parseHdfcLine(trimmed, dateMatcher.group(1));
            } else if (current != null) {
                if (!trimmed.matches(".*(?:Page|Total|Summary).*")) {
                    String cleanContinuation = trimmed.replaceAll("\\s+", " ");
                    current.setNarration(current.getNarration() + " " + cleanContinuation);
                    current.setRawLine(current.getRawLine() + "\n" + trimmed);

                    // If current row didn't have amounts, check if continuation line has amounts
                    if (!isRowValid(current)) {
                        extractAmountsIntoRow(current, trimmed);
                    }
                }
            }
        }

        if (current != null && isRowValid(current)) {
            rows.add(current);
        }

        return rows;
    }

    private RawTransactionRow parseHdfcLine(String line, String dateStr) {
        RawTransactionRow row = new RawTransactionRow();
        row.setDateStr(dateStr);
        row.setRawLine(line);

        String contentAfterDate = line.substring(dateStr.length()).trim();
        row.setNarration(contentAfterDate);

        extractAmountsIntoRow(row, line);

        row.setUpiId(extractUpiId(line));
        row.setReferenceNo(extractRefNo(line));

        return row;
    }

    private void extractAmountsIntoRow(RawTransactionRow row, String line) {
        List<String> amounts = new ArrayList<>();
        Matcher am = AMOUNT_PATTERN.matcher(line);
        while (am.find()) {
            amounts.add(am.group().replace(",", ""));
        }

        if (amounts.isEmpty()) return;

        double withdrawal = 0.0;
        double deposit = 0.0;
        Double balance = null;

        if (amounts.size() >= 3) {
            double a1 = parseDouble(amounts.get(amounts.size() - 3));
            double a2 = parseDouble(amounts.get(amounts.size() - 2));
            balance = parseDouble(amounts.get(amounts.size() - 1));

            if (a1 > 0 && a2 == 0) {
                withdrawal = a1;
            } else if (a2 > 0 && a1 == 0) {
                deposit = a2;
            } else if (a1 > 0 && a2 > 0) {
                withdrawal = a1;
                deposit = a2;
            } else {
                withdrawal = a1;
            }
        } else if (amounts.size() == 2) {
            double amt = parseDouble(amounts.get(0));
            balance = parseDouble(amounts.get(1));

            String lower = line.toLowerCase();
            if (lower.contains("cr") || lower.contains("deposit") || lower.contains("credited")) {
                deposit = amt;
            } else {
                withdrawal = amt;
            }
        } else if (amounts.size() == 1) {
            double amt = parseDouble(amounts.get(0));
            String lower = line.toLowerCase();
            if (lower.contains("cr") || lower.contains("deposit") || lower.contains("credited")) {
                deposit = amt;
            } else {
                withdrawal = amt;
            }
        }

        if (withdrawal > 0) row.setDebitAmount(withdrawal);
        if (deposit > 0) row.setCreditAmount(deposit);
        if (balance != null) row.setBalance(balance);

        if (row.getUpiId().isEmpty()) {
            row.setUpiId(extractUpiId(line));
        }
        if (row.getReferenceNo().isEmpty()) {
            row.setReferenceNo(extractRefNo(line));
        }
    }

    private String extractUpiId(String line) {
        Matcher matcher = Pattern.compile("\\b([a-zA-Z0-9._%]+@[a-zA-Z0-9.\\-]+)").matcher(line);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "";
    }

    private String extractRefNo(String line) {
        Matcher matcher = Pattern.compile("\\b(\\d{9,18})\\b").matcher(line);
        if (matcher.find()) {
            return matcher.group(1);
        }
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
        try {
            return Double.parseDouble(str.replace(",", ""));
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }
}
