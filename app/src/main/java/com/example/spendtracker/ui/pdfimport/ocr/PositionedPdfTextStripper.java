package com.example.spendtracker.ui.pdfimport.ocr;

import com.tom_roush.pdfbox.text.PDFTextStripper;
import com.tom_roush.pdfbox.text.TextPosition;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Retains digital-PDF word coordinates so blank amount columns do not disappear. */
public final class PositionedPdfTextStripper extends PDFTextStripper {
    private final List<OcrLine> words = new ArrayList<>();

    public PositionedPdfTextStripper() throws IOException {
        setSortByPosition(true);
    }

    @Override
    protected void writeString(String text, List<TextPosition> positions) throws IOException {
        super.writeString(text, positions);
        StringBuilder word = new StringBuilder();
        float left = 0, top = 0, right = 0, bottom = 0;
        for (TextPosition position : positions) {
            String value = position.getUnicode();
            boolean whitespace = value == null || value.trim().isEmpty();
            float x = position.getXDirAdj();
            float y = position.getYDirAdj();
            if (whitespace || (word.length() > 0 && x - right > Math.max(1, position.getWidthOfSpace() * 0.6f))) {
                appendWord(word, left, top, right, bottom);
                word.setLength(0);
            }
            if (whitespace) continue;
            if (word.length() == 0) {
                left = x;
                top = y - position.getHeightDir();
                bottom = y;
            }
            word.append(value);
            right = x + position.getWidthDirAdj();
            top = Math.min(top, y - position.getHeightDir());
            bottom = Math.max(bottom, y);
        }
        appendWord(word, left, top, right, bottom);
    }

    private void appendWord(StringBuilder word, float left, float top, float right, float bottom) {
        if (word.length() > 0) words.add(new OcrLine(getCurrentPageNo(), word.toString(),
                Math.round(left * 4), Math.round(top * 4), Math.round(right * 4), Math.round(bottom * 4)));
    }

    public OcrDocument getDocument() { return new OcrDocument(words); }
}
