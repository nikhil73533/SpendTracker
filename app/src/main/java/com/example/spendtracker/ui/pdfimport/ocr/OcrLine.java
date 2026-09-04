package com.example.spendtracker.ui.pdfimport.ocr;

import android.graphics.Rect;

/** A line of OCR text with page geometry retained for table-row reconstruction. */
public class OcrLine {
    private final int pageNumber;
    private final String text;
    private final Rect bounds;

    public OcrLine(int pageNumber, String text, Rect bounds) {
        this.pageNumber = pageNumber;
        this.text = text == null ? "" : text;
        this.bounds = bounds == null ? new Rect() : new Rect(bounds);
    }

    public int getPageNumber() { return pageNumber; }
    public String getText() { return text; }
    public Rect getBounds() { return new Rect(bounds); }
}
