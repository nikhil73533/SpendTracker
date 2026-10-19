package com.example.spendtracker.ui.pdfimport.ocr;

import android.graphics.Rect;

/** A line of OCR text with page geometry retained for table-row reconstruction. */
public class OcrLine {
    private final int pageNumber;
    private final String text;
    private final int left, top, right, bottom;

    public OcrLine(int pageNumber, String text, Rect bounds) {
        this(pageNumber, text, bounds == null ? 0 : bounds.left, bounds == null ? 0 : bounds.top,
                bounds == null ? 0 : bounds.right, bounds == null ? 0 : bounds.bottom);
    }

    public OcrLine(int pageNumber, String text, int left, int top, int right, int bottom) {
        this.pageNumber = pageNumber;
        this.text = text == null ? "" : text;
        this.left = left;
        this.top = top;
        this.right = right;
        this.bottom = bottom;
    }

    public int getPageNumber() { return pageNumber; }
    public String getText() { return text; }
    public Rect getBounds() { return new Rect(left, top, right, bottom); }
    public int getLeft() { return left; }
    public int getTop() { return top; }
    public int getRight() { return right; }
    public int getBottom() { return bottom; }
    public int centerX() { return (left + right) / 2; }
    public int centerY() { return (top + bottom) / 2; }
    public int height() { return Math.max(1, bottom - top); }
}
