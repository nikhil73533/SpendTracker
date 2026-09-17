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

    public java.util.Map<Integer, String> getPageTexts() { return StatementTableLayout.reconstructPages(lines); }

    public String getText() {
        return StatementTableLayout.reconstruct(lines);
    }
}
