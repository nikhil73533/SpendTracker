package com.example.spendtracker.ui.pdfimport.parser;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.ResolverStyle;
import java.util.Locale;

/** Strict field parsing. OCR substitutions are confined to known numeric cells. */
public final class StatementFields {
    private StatementFields() { }

    public static String date(String input) {
        if (input == null) return null;
        String value = input.trim().replace('\u00a0', ' ').replaceAll("[–—]", "-")
                .replaceAll("\\s*([/.-])\\s*", "$1").replaceAll("\\s+", " ");
        value = value.replaceFirst("(?i)[ T](?:[01]?\\d|2[0-3]):[0-5]\\d(?::[0-5]\\d)?(?: ?[AP]M)?$", "");
        if (value.matches("[0-9OoIl|/ .-]+")) {
            value = value.replace('O', '0').replace('o', '0').replace('I', '1')
                    .replace('l', '1').replace('|', '1');
        }
        for (String pattern : new String[]{"d/M/uuuu", "d/M/uu", "d-M-uuuu", "d-M-uu",
                "d.M.uuuu", "d.M.uu", "uuuu-M-d", "uuuu/M/d", "uuuu.M.d",
                "d MMM uuuu", "d MMM uu", "d MMMM uuuu", "d-MMM-uuuu", "d-MMM-uu",
                "d/MMM/uuuu", "d/MMM/uu", "d-MMMM-uuuu"}) {
            try {
                DateTimeFormatter formatter = new DateTimeFormatterBuilder().parseCaseInsensitive()
                        .appendPattern(pattern).toFormatter(Locale.ENGLISH).withResolverStyle(ResolverStyle.STRICT);
                LocalDate date = LocalDate.parse(value, formatter);
                if (date.getYear() >= 1900 && date.getYear() <= 2199) return date.toString();
            } catch (java.time.DateTimeException ignored) { }
        }
        return null;
    }

    /** Returns null for unreadable cells; zero is reserved for explicit blank/zero cells. */
    public static Double amount(String input) {
        String value = input == null ? "" : input.trim().toUpperCase(Locale.ENGLISH);
        if (value.isEmpty() || value.matches("[-–—]+|NIL|N/A")) return 0.0;
        value = value.replaceAll("^(?:INR|RS\\.?|₹|£)\\s*", "")
                .replaceAll("\\s*(?:CR|DR|C|D)$", "").replaceFirst("/-$", "").trim();
        // Two distinct whole numbers in one cell must not turn into a much larger amount.
        if (value.matches(".*\\d\\s+\\d{1,2}(?:\\D.*|$)")) return null;
        boolean negative = value.startsWith("(") && value.endsWith(")");
        if (negative) value = value.substring(1, value.length() - 1);
        // O/0 and I/l/1 are frequent scan errors, but never repair narration or references.
        value = value.replace('O', '0').replace('I', '1').replace('L', '1').replace('|', '1')
                .replaceAll("\\s+", "");
        if (!value.matches("[+-]?(?:\\d+|\\d{1,3}(?:,\\d{3})+|\\d{1,2}(?:,\\d{2})*,\\d{3})(?:\\.\\d{1,2})?")) return null;
        try {
            double result = new BigDecimal(value.replace(",", "")).doubleValue();
            return Double.isFinite(result) ? (negative ? -result : result) : null;
        } catch (NumberFormatException ignored) { return null; }
    }

    public static String direction(String value) {
        String upper = value == null ? "" : value.toUpperCase(Locale.ENGLISH);
        boolean debit = upper.matches(".*\\b(?:DR|D|DEBIT(?:ED)?|WITHDRAW(?:AL|ALS|N)?|WDL|EXPENSE|PAID OUT)\\b.*");
        boolean credit = upper.matches(".*\\b(?:CR|C|CREDIT(?:ED)?|DEPOSIT(?:ED|S)?|INCOME|PAID IN)\\b.*");
        return debit == credit ? "" : (debit ? "DEBIT" : "CREDIT");
    }

    public static int countDatedRows(String text) {
        int count = 0, dateColumn = 0;
        for (String line : text.split("\\r?\\n")) {
            String[] cells = line.split("\t", -1);
            int headerDate = java.util.Arrays.asList(cells).indexOf("DATE");
            if (headerDate >= 0) { dateColumn = headerDate; continue; }
            if (cells.length <= dateColumn) continue;
            if (line.toUpperCase(Locale.ENGLISH).matches(".*(?:OPENING BALANCE|CLOSING BALANCE|BROUGHT FORWARD|CARRIED FORWARD|B/F|C/F).*")) continue;
            String date = cells[dateColumn].trim();
            if (date(date) != null || date.matches(
                    "(?i)^(?:\\d{1,4}\\s*[/.-].*[/.-].*|\\d{1,2}\\s+[A-Z]{3,9}\\s+\\d{2,4}.*)")) count++;
        }
        return count;
    }
}
