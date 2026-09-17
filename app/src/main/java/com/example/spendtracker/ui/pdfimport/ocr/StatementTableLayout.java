package com.example.spendtracker.ui.pdfimport.ocr;

import com.example.spendtracker.ui.pdfimport.parser.StatementFields;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Reconstructs table cells from word coordinates shared by PDFBox and ML Kit.
 * Empty cells survive as tabs; wrapped narrations/references stay inside their date row.
 */
final class StatementTableLayout {
    private StatementTableLayout() { }

    static String reconstruct(List<OcrLine> words) {
        return String.join("\n", reconstructPages(words).values());
    }

    static java.util.Map<Integer, String> reconstructPages(List<OcrLine> words) {
        List<OcrLine> ordered = new ArrayList<>(words);
        ordered.sort(Comparator.comparingInt(OcrLine::getPageNumber)
                .thenComparingInt(OcrLine::centerY).thenComparingInt(OcrLine::getLeft));
        java.util.Map<Integer, String> pages = new java.util.LinkedHashMap<>();
        List<Column> previousColumns = null;
        for (int from = 0; from < ordered.size();) {
            StringBuilder output = new StringBuilder();
            int to = from + 1;
            while (to < ordered.size() && ordered.get(to).getPageNumber() == ordered.get(from).getPageNumber()) to++;
            List<VisualRow> rows = visualRows(ordered.subList(from, to));
            List<Column> columns = previousColumns;
            int tableStart = 0;
            for (int i = 0; i < rows.size(); i++) {
                Header header = headerAt(rows, i);
                if (header != null) {
                    for (int j = 0; j < i; j++) output.append(rows.get(j).text()).append('\n');
                    columns = header.columns;
                    tableStart = header.end;
                    break;
                }
            }
            if (columns == null) {
                for (VisualRow row : rows) output.append(row.text()).append('\n');
            } else {
                previousColumns = columns;
                appendTable(output, rows, tableStart, columns);
            }
            output.append('\n');
            pages.put(ordered.get(from).getPageNumber(), output.toString());
            from = to;
        }
        return pages;
    }

    private static List<VisualRow> visualRows(List<OcrLine> words) {
        List<VisualRow> rows = new ArrayList<>();
        for (OcrLine word : words) {
            if (word.getText().trim().isEmpty()) continue;
            VisualRow last = rows.isEmpty() ? null : rows.get(rows.size() - 1);
            // A fixed anchor prevents transitive merging of neighboring rows.
            if (last == null || Math.abs(word.centerY() - last.anchor.centerY())
                    > Math.min(word.height(), last.anchor.height()) * 0.55) {
                last = new VisualRow(word);
                rows.add(last);
            }
            last.words.add(word);
        }
        for (VisualRow row : rows) row.words.sort(Comparator.comparingInt(OcrLine::getLeft));
        return rows;
    }

    private static Header headerAt(List<VisualRow> rows, int start) {
        List<Column> columns = new ArrayList<>();
        for (int end = start; end < rows.size() && end < start + 3; end++) {
            VisualRow row = rows.get(end);
            if (row.anchor.centerY() - rows.get(start).anchor.centerY()
                    > rows.get(start).anchor.height() * 3.5) break;
            for (int i = 0; i < row.words.size(); i++) {
                OcrLine first = row.words.get(i);
                String text = "";
                Column best = null;
                int bestEnd = i;
                for (int j = i; j < row.words.size() && j < i + 4; j++) {
                    OcrLine word = row.words.get(j);
                    if (j > i && word.getLeft() - row.words.get(j - 1).getRight() > first.height() * 2) break;
                    text += (text.isEmpty() ? "" : " ") + word.getText();
                    String role = role(text);
                    if (role != null) {
                        best = new Column(role, (first.getLeft() + word.getRight()) / 2.0);
                        bestEnd = j;
                    }
                }
                if (best != null) {
                    Column candidate = best;
                    boolean duplicate = columns.stream().anyMatch(c -> (c.role.equals(candidate.role)
                            || (c.role.equals("VALUE_DATE") && candidate.role.equals("DATE")))
                            && Math.abs(c.center - candidate.center) < first.height() * 3);
                    if (!duplicate) columns.add(best);
                    i = bestEnd;
                }
            }
            if (has(columns, "DATE") && has(columns, "NARRATION")
                    && ((has(columns, "DEBIT") && has(columns, "CREDIT")) || has(columns, "AMOUNT"))) {
                columns.sort(Comparator.comparingDouble(c -> c.center));
                boolean explicitValueDate = has(columns, "VALUE_DATE");
                boolean firstDate = true;
                for (Column column : columns) {
                    if (!explicitValueDate && column.role.equals("DATE")) {
                        column.role = firstDate ? "DATE" : "VALUE_DATE";
                        firstDate = false;
                    }
                }
                return new Header(columns, end + 1);
            }
        }
        return null;
    }

    private static boolean has(List<Column> columns, String role) {
        return columns.stream().anyMatch(c -> c.role.equals(role));
    }

    private static String role(String value) {
        String text = value.toUpperCase(Locale.ENGLISH).replaceAll("[^A-Z/ ]", "").replaceAll("\\s+", " ").trim();
        if (text.matches("(TXN|TRAN|TRANS|TRANSACTION|POST|POSTING)? ?(DATE|DT)")) return "DATE";
        if (text.matches("VALUE(?: (DATE|DT))?")) return "VALUE_DATE";
        if (text.matches("DESCRIPTION(/NARRATION)?|NARRATION|PARTICULARS|(TRANSACTION )?DETAILS|PAYMENT TYPE AND DETAILS")) return "NARRATION";
        if (text.matches("REF(ERENCE)?( NO)?|REF/CHEQUE( NO)?|CHEQUE(/REFERENCE)?( NO)?|CHQ(/REF)?( NO)?")) return "REFERENCE";
        if (text.equals("DEBIT/CREDIT")) return "AMOUNT";
        if (text.matches("DR/CR|CR/DR")) return "TYPE";
        if (text.matches("(?:DEBIT|DEBITS|WITHDRAWAL(S)?|WITHDRAWN|WDL|PAID OUT|EXPENSE)(?: (?:AMT|AMOUNT|DR))?")) return "DEBIT";
        if (text.matches("(?:CREDIT|CREDITS|DEPOSIT(S)?|PAID IN|INCOME)(?: (?:AMT|AMOUNT|CR))?")) return "CREDIT";
        if (text.matches("(CLOSING |RUNNING )?BALANCE")) return "BALANCE";
        if (text.matches("(TRANSACTION |TXN )?AMOUNT")) return "AMOUNT";
        if (text.matches("(TRANSACTION |TXN )?TYPE")) return "TYPE";
        if (text.equals("TIME")) return "TIME";
        if (text.matches("MODE|INIT( BR)?|BRANCH|S NO|SR NO")) return "OTHER";
        return null;
    }

    private static void appendTable(StringBuilder output, List<VisualRow> rows, int start,
                                    List<Column> columns) {
        output.append(String.join("\t", columns.stream().map(c -> c.role).toArray(String[]::new))).append('\n');
        List<VisualRow> section = new ArrayList<>();
        for (int i = start; i < rows.size(); i++) {
            VisualRow row = rows.get(i);
            String text = row.text().toUpperCase(Locale.ENGLISH);
            Header repeated = headerAt(rows, i);
            if (repeated != null) {
                appendDatedRows(output, section, columns);
                section.clear();
                columns = repeated.columns;
                output.append(String.join("\t", columns.stream().map(c -> c.role).toArray(String[]::new))).append('\n');
                i = repeated.end - 1;
                continue;
            }
            if (text.matches("^(TOTAL|SUMMARY|CLOSING BALANCE|BALANCE CARRIED|REWARD POINTS|ACCOUNT RELATED|THIS IS|PLEASE|PAGE|CALL US)\\b.*")) {
                appendDatedRows(output, section, columns);
                section.clear();
                if (!text.startsWith("PAGE")) break;
                continue;
            }
            section.add(row);
        }
        appendDatedRows(output, section, columns);
    }

    private static void appendDatedRows(StringBuilder output, List<VisualRow> rows, List<Column> columns) {
        int dateColumn = 0, narrationColumn = -1;
        for (int i = 0; i < columns.size(); i++) {
            if (columns.get(i).role.equals("DATE")) dateColumn = i;
            if (columns.get(i).role.equals("NARRATION")) narrationColumn = i;
        }
        List<Integer> anchors = new ArrayList<>();
        List<String[]> cellRows = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            String[] values = cells(rows.get(i), columns);
            cellRows.add(values);
            String date = values[dateColumn];
            if (StatementFields.date(date) != null || date.matches(
                    "(?i)(?:[0-9OIl|]{1,4}\\s*[/.-].*[/.-].*|\\d{1,2}\\s+[A-Z]{3,9}\\s+\\d{2,4})")) anchors.add(i);
            else if (!anchors.isEmpty() && date.isEmpty() && narrationColumn >= 0
                    && values[narrationColumn].matches("(?i)^(?:BP|CR|DR|DD|SO)\\b.+")) anchors.add(i);
        }
        if (anchors.isEmpty()) return;
        // The largest inter-line gap handles both top-aligned (AU) and centered (SBI) dates.
        int start = anchors.get(0);
        while (start > 0 && cellRows.get(start - 1)[dateColumn].isEmpty()
                && rows.get(anchors.get(0)).anchor.centerY() - rows.get(start - 1).anchor.centerY()
                < rows.get(anchors.get(0)).anchor.height() * 3) start--;
        String previousDate = "";
        for (int index = 0; index < anchors.size(); index++) {
            int end = rows.size();
            if (index + 1 < anchors.size()) {
                int current = anchors.get(index), next = anchors.get(index + 1);
                double largestGap = -1;
                end = next;
                for (int j = current + 1; j <= next; j++) {
                    double gap = rows.get(j).anchor.getTop() - rows.get(j - 1).anchor.getBottom();
                    if (gap >= largestGap) { largestGap = gap; end = j; }
                }
            }
            String[] transaction = new String[columns.size()];
            java.util.Arrays.fill(transaction, "");
            for (int i = start; i < end; i++) {
                for (int j = 0; j < transaction.length; j++) {
                    String value = cellRows.get(i)[j];
                    if (!value.isEmpty()) transaction[j] += (transaction[j].isEmpty() ? "" : " ") + value;
                }
            }
            if (transaction[dateColumn].isEmpty()) transaction[dateColumn] = previousDate;
            else previousDate = transaction[dateColumn];
            appendCells(output, transaction);
            start = end;
        }
    }

    private static String[] cells(VisualRow row, List<Column> columns) {
        String[] cells = new String[columns.size()];
        java.util.Arrays.fill(cells, "");
        for (OcrLine word : row.words) {
            int column = 0;
            while (column + 1 < columns.size()
                    && word.centerX() > (columns.get(column).center + columns.get(column + 1).center) / 2) column++;
            cells[column] += (cells[column].isEmpty() ? "" : " ") + word.getText().trim();
        }
        return cells;
    }

    private static void appendCells(StringBuilder output, String[] cells) {
        if (cells != null) output.append(String.join("\t", cells)).append('\n');
    }

    private static final class Column {
        String role;
        final double center;
        Column(String role, double center) { this.role = role; this.center = center; }
    }
    private static final class Header {
        final List<Column> columns;
        final int end;
        Header(List<Column> columns, int end) { this.columns = columns; this.end = end; }
    }
    private static final class VisualRow {
        final OcrLine anchor;
        final List<OcrLine> words = new ArrayList<>();
        VisualRow(OcrLine anchor) { this.anchor = anchor; }
        String text() {
            return String.join(" ", words.stream().map(OcrLine::getText).toArray(String[]::new));
        }
    }
}
