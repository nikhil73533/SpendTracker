package com.example.spendtracker.ui.pdfimport.ocr;

import android.graphics.Rect;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class OcrDocument {
    private final List<OcrLine> lines;

    public OcrDocument(List<OcrLine> lines) {
        this.lines = lines == null ? new ArrayList<>() : new ArrayList<>(lines);
    }

    public List<OcrLine> getLines() { return Collections.unmodifiableList(lines); }

    public String getText() {
        StringBuilder result = new StringBuilder();
        List<OcrLine> orderedLines = new ArrayList<>(lines);
        orderedLines.sort(Comparator
                .comparingInt(OcrLine::getPageNumber)
                .thenComparingInt(line -> line.getBounds().centerY())
                .thenComparingInt(line -> line.getBounds().left));

        List<OcrLine> row = new ArrayList<>();
        int currentPage = -1;
        for (OcrLine line : orderedLines) {
            if (currentPage != line.getPageNumber()) {
                appendRow(result, row);
                if (currentPage != -1) result.append('\n');
                row.clear();
                currentPage = line.getPageNumber();
            } else if (!row.isEmpty() && !belongsToSameVisualRow(row, line)) {
                appendRow(result, row);
                row.clear();
            }
            row.add(line);
        }
        appendRow(result, row);
        return result.toString();
    }

    /**
     * ML Kit frequently returns one Text.Line per table cell. Rebuild visual rows before
     * feeding OCR text to the statement parsers so date, narration, amount, and DR/CR
     * columns arrive on the same logical line.
     */
    private boolean belongsToSameVisualRow(List<OcrLine> row, OcrLine candidate) {
        Rect candidateBounds = candidate.getBounds();
        if (candidateBounds.isEmpty()) return false;

        for (OcrLine existing : row) {
            Rect existingBounds = existing.getBounds();
            if (existingBounds.isEmpty()) continue;

            int overlap = Math.min(existingBounds.bottom, candidateBounds.bottom)
                    - Math.max(existingBounds.top, candidateBounds.top);
            int minHeight = Math.min(existingBounds.height(), candidateBounds.height());
            if (minHeight > 0 && overlap >= Math.round(minHeight * 0.35f)) return true;

            int centerDistance = Math.abs(existingBounds.centerY() - candidateBounds.centerY());
            int tolerance = Math.max(8, Math.round(Math.max(existingBounds.height(), candidateBounds.height()) * 0.55f));
            if (centerDistance <= tolerance) return true;
        }
        return false;
    }

    private void appendRow(StringBuilder result, List<OcrLine> row) {
        if (row.isEmpty()) return;
        row.sort(Comparator.comparingInt(line -> line.getBounds().left));
        for (OcrLine line : row) {
            String text = line.getText().trim();
            if (text.isEmpty()) continue;
            if (result.length() > 0 && result.charAt(result.length() - 1) != '\n') result.append(' ');
            result.append(text);
        }
        result.append('\n');
    }
}
