package com.example.spendtracker.ui.pdfimport.parser;

import com.example.spendtracker.data.sms.extraction.CounterpartyExtractor;
import java.util.List;

/** Shared checks for choosing between digital, original OCR and enhanced OCR pages. */
public final class StatementExtractionQuality {
    private StatementExtractionQuality() { }

    public static int validRows(List<RawTransactionRow> rows) {
        int count = 0;
        for (RawTransactionRow row : rows) {
            Double amount = row.getDebitAmount() != null ? row.getDebitAmount() : row.getCreditAmount();
            if (StatementFields.date(row.getDateStr()) != null && amount != null
                    && Double.isFinite(amount) && amount > 0) count++;
        }
        return count;
    }

    public static boolean needsRetry(String text, List<RawTransactionRow> rows) {
        int valid = validRows(rows);
        if (valid == 0 || valid < rows.size() || StatementFields.countDatedRows(text) > valid) return true;
        CounterpartyExtractor extractor = new CounterpartyExtractor();
        return rows.stream().anyMatch(row -> !hasDescription(row)
                || (row.getNarration().contains("@") && extractor.extract(row.getNarration()).displayName().isEmpty()))
                || balanceMismatches(rows) > 0;
    }

    public static boolean prefer(String candidateText, List<RawTransactionRow> candidate,
                                 String currentText, List<RawTransactionRow> current) {
        int count = validRows(candidate), oldCount = validRows(current);
        if (count != oldCount) return count > oldCount;
        if (count == 0) return false;
        int errors = balanceMismatches(candidate), oldErrors = balanceMismatches(current);
        if (errors != oldErrors) return errors < oldErrors;
        int quality = descriptionQuality(candidate), oldQuality = descriptionQuality(current);
        if (quality != oldQuality) return quality > oldQuality;
        return candidateText.contains("DATE\t") && !currentText.contains("DATE\t");
    }

    private static boolean hasDescription(RawTransactionRow row) {
        return row.getNarration() != null && row.getNarration().matches("(?s).*[\\p{L}].*");
    }

    private static int descriptionQuality(List<RawTransactionRow> rows) {
        CounterpartyExtractor extractor = new CounterpartyExtractor();
        int quality = 0;
        for (RawTransactionRow row : rows) {
            if (hasDescription(row)) quality++;
            CounterpartyExtractor.Result party = extractor.extract(row.getNarration());
            if (!party.name.isEmpty()) quality += 2;
            else if (!party.handle.isEmpty()) quality++;
        }
        return quality;
    }

    /** Audit evidence only: never rewrite an explicit amount or debit/credit cell to fit a balance. */
    public static int balanceMismatches(List<RawTransactionRow> rows) {
        if (rows.size() < 2) return 0;
        String first = StatementFields.date(rows.get(0).getDateStr());
        String last = StatementFields.date(rows.get(rows.size() - 1).getDateStr());
        if (first == null || last == null) return 0;
        if (first.equals(last)) return Math.min(mismatches(rows, true), mismatches(rows, false));
        return mismatches(rows, first.compareTo(last) < 0);
    }

    private static int mismatches(List<RawTransactionRow> rows, boolean ascending) {
        int errors = 0;
        for (int i = 1; i < rows.size(); i++) {
            RawTransactionRow previous = rows.get(i - 1), current = rows.get(i);
            if (previous.getBalance() == null || current.getBalance() == null) continue;
            RawTransactionRow transaction = ascending ? current : previous;
            double movement = (transaction.getCreditAmount() == null ? 0 : transaction.getCreditAmount())
                    - (transaction.getDebitAmount() == null ? 0 : transaction.getDebitAmount());
            double delta = ascending ? current.getBalance() - previous.getBalance()
                    : previous.getBalance() - current.getBalance();
            if (Math.abs(movement - delta) > .02) errors++;
        }
        return errors;
    }
}
