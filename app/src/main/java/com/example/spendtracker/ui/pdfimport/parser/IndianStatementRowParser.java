package com.example.spendtracker.ui.pdfimport.parser;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Layout-tolerant row parser for the formats commonly exported by Indian banks:
 * separate Debit/Credit columns, separate Withdrawal/Deposit columns, and a
 * single signed Amount column accompanied by DR/CR.
 */
final class IndianStatementRowParser {
    private static final String DATE_EXPRESSION =
            "(?:\\d{4}[-/.]\\d{1,2}[-/.]\\d{1,2}"
                    + "|\\d{1,2}[-/.](?:\\d{1,2}|[A-Za-z]{3,9})[-/.]\\d{2,4}"
                    + "|\\d{1,2}(?:\\s+|-)[A-Za-z]{3,9}(?:\\s+|-)\\d{2,4})";

    private static final Pattern ROW_START_PATTERN = Pattern.compile(
            "^(?:\\s*\\d{1,4}[.)]?\\s+)?(" + DATE_EXPRESSION + ")(?=\\s|$)",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern AMOUNT_CANDIDATE_PATTERN = Pattern.compile(
            "(?i)(?<![A-Z0-9@])(?:₹|INR\\s*|RS\\.?\\s*)?\\(?[+-]?"
                    + "(?:\\d{1,3}(?:,\\d{2,3})+|\\d+)(?:[.]\\s*\\d{1,2})?\\)?"
                    + "(?:\\s*(?:CR|DR|C|D))?(?![A-Z0-9@])");

    private static final Pattern CREDIT_MARKER = Pattern.compile(
            "(?i)(?:^|[\\s/\\-])(?:CR|CREDIT|CREDITED|DEPOSIT|DEPOSITED|INCOME|RECEIVED|REFUND)(?=$|[\\s/\\-])"
                    + "|\\bBY\\s+TRANSFER\\b");
    private static final Pattern DEBIT_MARKER = Pattern.compile(
            "(?i)(?:^|[\\s/\\-])(?:DR|DEBIT|DEBITED|WITHDRAWAL|WITHDRAWN|WDL|EXPENSE|PURCHASE)(?=$|[\\s/\\-])"
                    + "|\\b(?:TO\\s+TRANSFER|PAID\\s+TO)\\b");

    private static final Pattern UNDATED_TRANSACTION_START = Pattern.compile(
            "(?i)^(?:BP|CR|DR|DD|SO|ATM|POS|UPI|NEFT|IMPS|RTGS|ACH|NACH|CHQ|CHEQUE|CARD|CASH)\\b");

    private static final String[] DATE_FORMATS = {
            "yyyy-MM-dd", "yyyy/MM/dd", "yyyy.MM.dd",
            "d/MM/yyyy", "d/MM/yy", "d-MM-yyyy", "d-MM-yy", "d.MM.yyyy", "d.MM.yy",
            "d/MMM/yyyy", "d/MMM/yy", "d-MMM-yyyy", "d-MMM-yy",
            "d MMM yyyy", "d MMM yy", "d MMMM yyyy", "d-MMMM-yyyy"
    };

    private IndianStatementRowParser() { }

    static List<RawTransactionRow> parse(String fullText) {
        List<CandidateRow> candidates = new ArrayList<>();
        if (fullText == null || fullText.trim().isEmpty()) return new ArrayList<>();

        // Do not reinterpret rejected structured rows using narration or balance as amounts.
        if (fullText.contains("DATE\t")) return TabularStatementParser.parse(fullText);

        ColumnLayout columnLayout = detectColumnLayout(fullText);
        CandidateRow current = null;

        for (String sourceLine : fullText.split("\\r?\\n")) {
            String line = sourceLine.trim();
            if (line.isEmpty() || isMetadataOrHeader(line)) continue;

            Matcher dateMatcher = ROW_START_PATTERN.matcher(line);
            if (dateMatcher.find()) {
                addCandidate(candidates, current, columnLayout);
                current = new CandidateRow(dateMatcher.group(1), line,
                        dateMatcher.end() < line.length() ? line.substring(dateMatcher.end()).trim() : "");
            } else if (current != null && !isFooter(line)) {
                if (isUndatedTransactionStart(line) && hasAmount(current.rawLine)) {
                    addCandidate(candidates, current, columnLayout);
                    current = new CandidateRow(current.date, line, line);
                } else {
                    current.rawLine += "\n" + line;
                    current.narration += " " + line.replaceAll("\\s+", " ");
                }
            }
        }
        addCandidate(candidates, current, columnLayout);
        reconcileAmbiguousDirections(candidates);

        List<RawTransactionRow> rows = new ArrayList<>();
        for (CandidateRow candidate : candidates) {
            if (candidate.amount <= 0 || !candidate.directionCertain) continue;
            RawTransactionRow row = new RawTransactionRow();
            row.setDateStr(candidate.date);
            row.setNarration(candidate.cleanedNarration);
            row.setRawLine(candidate.rawLine);
            row.setBalance(candidate.balance);
            if (candidate.credit) row.setCreditAmount(candidate.amount);
            else row.setDebitAmount(candidate.amount);
            row.setUpiId(extractUpiId(candidate.rawLine));
            row.setReferenceNo(extractReference(candidate.rawLine));
            rows.add(row);
        }
        return rows;
    }

    private static void addCandidate(List<CandidateRow> result, CandidateRow row, ColumnLayout columnLayout) {
        if (row == null) return;
        populateAmounts(row, columnLayout);
        if (row.amount > 0 || row.balance != null) result.add(row);
    }

    private static void populateAmounts(CandidateRow row, ColumnLayout columnLayout) {
        String flatRow = row.rawLine.replace('\n', ' ').replaceAll("\\s+", " ").trim();
        String monetaryText = flatRow.replaceAll(DATE_EXPRESSION, " ")
                .replaceAll("\\b\\d{1,2}:\\d{2}(?::\\d{2})?\\b", " ");
        List<AmountToken> amounts = extractAmounts(monetaryText);
        if (amounts.isEmpty()) return;

        if (isBalanceOnlyRow(row.narration)) {
            row.balance = amounts.get(amounts.size() - 1).absoluteValue;
            row.directionCertain = true;
            row.cleanedNarration = cleanNarration(row.narration);
            return;
        }

        // A CR on the running balance is not evidence that the transaction is a credit.
        Direction markerDirection = directionFromText(AMOUNT_CANDIDATE_PATTERN.matcher(monetaryText).replaceAll(" "));
        AmountToken transactionAmount;

        if (columnLayout.isSeparate() && amounts.size() >= 3) {
            AmountToken firstDirectionColumn = amounts.get(amounts.size() - 3);
            AmountToken secondDirectionColumn = amounts.get(amounts.size() - 2);
            AmountToken debit = columnLayout == ColumnLayout.CREDIT_DEBIT
                    ? secondDirectionColumn : firstDirectionColumn;
            AmountToken credit = columnLayout == ColumnLayout.CREDIT_DEBIT
                    ? firstDirectionColumn : secondDirectionColumn;
            row.balance = amounts.get(amounts.size() - 1).absoluteValue;
            if (debit.absoluteValue > 0 && credit.absoluteValue == 0) {
                row.credit = false;
                transactionAmount = debit;
                row.directionCertain = true;
            } else if (credit.absoluteValue > 0 && debit.absoluteValue == 0) {
                row.credit = true;
                transactionAmount = credit;
                row.directionCertain = true;
            } else if (markerDirection == Direction.CREDIT) {
                row.credit = true;
                transactionAmount = credit.absoluteValue > 0 ? credit : debit;
                row.directionCertain = true;
            } else {
                row.credit = false;
                transactionAmount = debit.absoluteValue > 0 ? debit : credit;
                row.directionCertain = markerDirection == Direction.DEBIT;
            }
        } else {
            transactionAmount = amounts.size() >= 2 ? amounts.get(amounts.size() - 2) : amounts.get(0);
            if (amounts.size() >= 2) row.balance = amounts.get(amounts.size() - 1).absoluteValue;

            Direction tokenDirection = transactionAmount.direction;
            if (tokenDirection != Direction.UNKNOWN) {
                row.credit = tokenDirection == Direction.CREDIT;
                row.directionCertain = true;
            } else if (markerDirection != Direction.UNKNOWN) {
                row.credit = markerDirection == Direction.CREDIT;
                row.directionCertain = true;
            } else if (transactionAmount.negative) {
                row.credit = false;
                row.directionCertain = true;
            } else if (transactionAmount.positiveSign) {
                row.credit = true;
                row.directionCertain = true;
            } else {
                // Debit is only a temporary fallback. Adjacent running balances can correct it.
                row.credit = false;
                row.directionCertain = false;
            }
        }

        row.amount = transactionAmount.absoluteValue;
        row.cleanedNarration = cleanNarration(row.narration);
    }

    private static List<AmountToken> extractAmounts(String row) {
        List<AmountToken> amounts = new ArrayList<>();
        Matcher matcher = AMOUNT_CANDIDATE_PATTERN.matcher(row);
        while (matcher.find()) {
            String raw = matcher.group();
            String upper = raw.toUpperCase(Locale.ENGLISH);
            boolean looksMonetary = raw.contains(".") || raw.contains(",") || raw.contains("₹")
                    || upper.contains("INR") || upper.matches(".*\\bRS\\.?.*")
                    || upper.matches(".*(?:CR|DR|C|D)\\s*$")
                    || raw.contains("+") || raw.contains("-") || raw.contains("(");
            if (!looksMonetary) continue; // Excludes dates, serial numbers and UTR/reference IDs.

            Direction direction = Direction.UNKNOWN;
            if (upper.matches(".*(?:CR|C)\\s*$")) direction = Direction.CREDIT;
            else if (upper.matches(".*(?:DR|D)\\s*$")) direction = Direction.DEBIT;

            String normalized = upper.replace("₹", "").replace("INR", "")
                    .replaceAll("RS\\.?", "").replaceAll("(?:CR|DR|C|D)\\s*$", "")
                    .replace("(", "-").replace(")", "").replace(",", "")
                    .replaceAll("\\s+", "").trim();
            try {
                double value = Double.parseDouble(normalized);
                amounts.add(new AmountToken(Math.abs(value), value < 0 || raw.contains("("),
                        raw.contains("+"), direction));
            } catch (NumberFormatException ignored) { }
        }
        return amounts;
    }

    private static boolean hasAmount(String row) {
        return !extractAmounts(row.replace('\n', ' ').replaceAll("\\s+", " ")).isEmpty();
    }

    private static boolean isUndatedTransactionStart(String line) {
        Matcher matcher = UNDATED_TRANSACTION_START.matcher(line);
        return matcher.find() && matcher.end() < line.length() && !line.substring(matcher.end()).trim().isEmpty();
    }

    private static Direction directionFromText(String row) {
        boolean credit = CREDIT_MARKER.matcher(row).find();
        boolean debit = DEBIT_MARKER.matcher(row).find();
        if (credit && !debit) return Direction.CREDIT;
        if (debit && !credit) return Direction.DEBIT;
        return Direction.UNKNOWN;
    }

    private static void reconcileAmbiguousDirections(List<CandidateRow> rows) {
        if (rows.size() < 2) return;
        boolean ascending = parseDate(rows.get(rows.size() - 1).date) >= parseDate(rows.get(0).date);
        for (int i = 1; i < rows.size(); i++) {
            CandidateRow previous = rows.get(i - 1);
            CandidateRow current = rows.get(i);
            if (previous.balance == null || current.balance == null) continue;
            double delta = current.balance - previous.balance;
            CandidateRow transaction = ascending ? current : previous;
            if (transaction.directionCertain || !approximately(Math.abs(delta), transaction.amount)) continue;
            transaction.credit = ascending ? delta > 0 : delta < 0;
            transaction.directionCertain = true;
        }
    }

    private static boolean approximately(double first, double second) {
        return Math.abs(first - second) <= Math.max(0.02, second * 0.0001);
    }

    private static long parseDate(String value) {
        for (String format : DATE_FORMATS) {
            try {
                SimpleDateFormat parser = new SimpleDateFormat(format, Locale.ENGLISH);
                parser.setLenient(false);
                Date parsed = parser.parse(value);
                if (parsed != null) return parsed.getTime();
            } catch (ParseException ignored) { }
        }
        return 0L;
    }

    private static String cleanNarration(String narration) {
        String clean = narration.replaceAll("(?i)^\\s*" + DATE_EXPRESSION + "\\s+", "")
                .replaceAll("(?i)(?:₹|INR\\s*|RS\\.?\\s*)?\\(?[+-]?"
                        + "(?:\\d{1,3}(?:,\\d{2,3})+|\\d+)[.]\\s*\\d{1,2}\\)?"
                        + "(?:\\s*(?:CR|DR|C|D))?", " ")
                .replaceAll("(?i)(?:^|\\s)(?:CR|DR)(?=\\s|$)", " ")
                .replaceAll("\\s+", " ").trim();
        return clean.isEmpty() ? narration.replaceAll("\\s+", " ").trim() : clean;
    }

    private static String extractUpiId(String line) {
        Matcher matcher = Pattern.compile("(?:^|[\\s/\\-])([A-Z0-9._%]+@[A-Z0-9.\\-]+)", Pattern.CASE_INSENSITIVE).matcher(line);
        return matcher.find() ? matcher.group(1) : "";
    }

    private static String extractReference(String line) {
        Matcher labelled = Pattern.compile(
                "(?i)\\b(?:UTR|RRN|REF(?:ERENCE)?(?:\\s+NO)?|CHEQUE(?:\\s+NO)?|CHQ(?:\\s+NO)?)"
                        + "[\\s:/#-]*([A-Z0-9-]{6,30})").matcher(line);
        if (labelled.find()) return labelled.group(1);
        Matcher upiReference = Pattern.compile("(?i)\\bUPI[/\\-](?:DR|CR)?[/\\-]?(\\d{9,18})\\b").matcher(line);
        if (upiReference.find()) return upiReference.group(1);
        Matcher numeric = Pattern.compile("\\b(\\d{9,18})\\b").matcher(line);
        return numeric.find() ? numeric.group(1) : "";
    }

    private static ColumnLayout detectColumnLayout(String text) {
        ColumnLayout detected = ColumnLayout.UNKNOWN;
        for (String sourceLine : text.split("\\r?\\n")) {
            String trimmed = sourceLine.trim();
            if (ROW_START_PATTERN.matcher(trimmed).find()) break;
            String line = trimmed.toUpperCase(Locale.ENGLISH);

            if (Pattern.compile("\\b(?:DEBIT\\s*/\\s*CREDIT|DR\\s*/\\s*CR|CR\\s*/\\s*DR)\\b")
                    .matcher(line).find()) {
                detected = ColumnLayout.SINGLE_AMOUNT;
                continue;
            }

            int debit = firstColumnIndex(line, "DEBIT", "WITHDRAWAL", "WITHDRAWALS", "PAID OUT");
            int credit = firstColumnIndex(line, "CREDIT", "DEPOSIT", "DEPOSITS", "PAID IN");
            if (debit >= 0 && credit >= 0) {
                detected = debit < credit ? ColumnLayout.DEBIT_CREDIT : ColumnLayout.CREDIT_DEBIT;
            }
        }
        return detected;
    }

    private static int firstColumnIndex(String line, String... labels) {
        int first = -1;
        for (String label : labels) {
            Matcher matcher = Pattern.compile("\\b" + label.replace(" ", "\\s+") + "\\b").matcher(line);
            if (matcher.find() && (first < 0 || matcher.start() < first)) first = matcher.start();
        }
        return first;
    }

    private static boolean isBalanceOnlyRow(String narration) {
        String upper = narration == null ? "" : narration.toUpperCase(Locale.ENGLISH);
        return upper.contains("OPENING BALANCE") || upper.contains("CLOSING BALANCE")
                || upper.contains("BALANCE BROUGHT FORWARD") || upper.contains("BROUGHT FORWARD")
                || upper.contains("BALANCE CARRIED FORWARD") || upper.contains("CARRIED FORWARD")
                || Pattern.compile("(?:^|\\s)B/F(?:\\s|$)").matcher(upper).find()
                || Pattern.compile("(?:^|\\s)C/F(?:\\s|$)").matcher(upper).find();
    }

    private static boolean isMetadataOrHeader(String line) {
        String upper = line.toUpperCase(Locale.ENGLISH);
        if (upper.matches("^PAGE\\s+\\d+.*")) return true;
        if (upper.contains("STATEMENT OF ACCOUNT") || upper.contains("ACCOUNT STATEMENT")) return true;
        int headers = 0;
        for (String word : new String[]{"DATE", "DESCRIPTION", "NARRATION", "DETAILS", "DEBIT", "CREDIT",
                "WITHDRAWAL", "DEPOSIT", "BALANCE", "AMOUNT", "DR/CR", "CR/DR"}) {
            if (upper.contains(word)) headers++;
        }
        return headers >= 2 && !ROW_START_PATTERN.matcher(line).find();
    }

    private static boolean isFooter(String line) {
        return Pattern.compile("(?i)^(?:PAGE|TOTAL|SUMMARY|CLOSING BALANCE|OPENING BALANCE)\\b").matcher(line).find();
    }

    private enum Direction { DEBIT, CREDIT, UNKNOWN }

    private enum ColumnLayout {
        DEBIT_CREDIT,
        CREDIT_DEBIT,
        SINGLE_AMOUNT,
        UNKNOWN;

        boolean isSeparate() {
            return this == DEBIT_CREDIT || this == CREDIT_DEBIT;
        }
    }

    private static final class AmountToken {
        final double absoluteValue;
        final boolean negative;
        final boolean positiveSign;
        final Direction direction;

        AmountToken(double absoluteValue, boolean negative, boolean positiveSign, Direction direction) {
            this.absoluteValue = absoluteValue;
            this.negative = negative;
            this.positiveSign = positiveSign;
            this.direction = direction;
        }
    }

    private static final class CandidateRow {
        final String date;
        String rawLine;
        String narration;
        String cleanedNarration;
        double amount;
        Double balance;
        boolean credit;
        boolean directionCertain;

        CandidateRow(String date, String rawLine, String narration) {
            this.date = date;
            this.rawLine = rawLine;
            this.narration = narration;
            this.cleanedNarration = narration;
        }
    }
}
