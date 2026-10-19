package com.example.spendtracker.ui.pdfimport.ocr;

import android.content.Context;
import android.net.Uri;

import java.io.File;

/** Isolates OCR providers so a cloud provider can be added later without changing parsers. */
public interface OcrEngine {
    OcrDocument recognizePdf(Context context, Uri uri) throws Exception;
    OcrDocument recognizePdf(File file) throws Exception;
    OcrDocument recognizeImage(Context context, Uri uri) throws Exception;
}
