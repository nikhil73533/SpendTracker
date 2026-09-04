package com.example.spendtracker.ui.pdfimport.ocr;

import android.content.Context;
import android.net.Uri;

/** Isolates OCR providers so a cloud provider can be added later without changing parsers. */
public interface OcrEngine {
    OcrDocument recognizePdf(Context context, Uri uri) throws Exception;
}
