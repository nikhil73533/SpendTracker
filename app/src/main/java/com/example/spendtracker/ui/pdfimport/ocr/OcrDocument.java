package com.example.spendtracker.ui.pdfimport.ocr;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class OcrDocument {
    private final List<OcrLine> lines;

    public OcrDocument(List<OcrLine> lines) {
        this.lines = lines == null ? new ArrayList<>() : new ArrayList<>(lines);
    }

    public List<OcrLine> getLines() { return Collections.unmodifiableList(lines); }

    public String getText() {
        StringBuilder result = new StringBuilder();
        int lastPage = -1;
        for (OcrLine line : lines) {
            if (lastPage != -1 && lastPage != line.getPageNumber()) result.append('\n');
            result.append(line.getText()).append('\n');
            lastPage = line.getPageNumber();
        }
        return result.toString();
    }
}
