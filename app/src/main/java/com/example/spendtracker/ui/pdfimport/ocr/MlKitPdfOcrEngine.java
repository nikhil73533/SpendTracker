package com.example.spendtracker.ui.pdfimport.ocr;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** On-device OCR for scanned statement pages. Runs only from a background thread. */
public class MlKitPdfOcrEngine implements OcrEngine {
    private static final float RENDER_SCALE = 2.5f;
    private static final long OCR_TIMEOUT_SECONDS = 45;

    @Override
    public OcrDocument recognizePdf(Context context, Uri uri) throws Exception {
        List<OcrLine> allLines = new ArrayList<>();
        TextRecognizer recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        try (ParcelFileDescriptor descriptor = context.getContentResolver().openFileDescriptor(uri, "r")) {
            if (descriptor == null) throw new IllegalStateException("Unable to open selected PDF");
            try (PdfRenderer renderer = new PdfRenderer(descriptor)) {
                for (int pageIndex = 0; pageIndex < renderer.getPageCount(); pageIndex++) {
                    try (PdfRenderer.Page page = renderer.openPage(pageIndex)) {
                        Bitmap original = renderPage(page);
                        Text text = recognize(recognizer, original);
                        // A low-output page benefits from a high-contrast retry. This is deliberately
                        // conditional to avoid damaging clean digital scans.
                        if (text.getText().trim().length() < 20) {
                            Bitmap processed = preprocess(original);
                            try {
                                Text retry = recognize(recognizer, processed);
                                if (retry.getText().length() > text.getText().length()) text = retry;
                            } finally {
                                processed.recycle();
                            }
                        }
                        appendLines(allLines, pageIndex + 1, text);
                        original.recycle();
                    }
                }
            }
        } finally {
            recognizer.close();
        }
        return new OcrDocument(allLines);
    }

    private Bitmap renderPage(PdfRenderer.Page page) {
        int width = Math.max(1, Math.round(page.getWidth() * RENDER_SCALE));
        int height = Math.max(1, Math.round(page.getHeight() * RENDER_SCALE));
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Matrix matrix = new Matrix();
        matrix.postScale(RENDER_SCALE, RENDER_SCALE);
        page.render(bitmap, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
        return bitmap;
    }

    private Text recognize(TextRecognizer recognizer, Bitmap bitmap) throws Exception {
        return Tasks.await(recognizer.process(InputImage.fromBitmap(bitmap, 0)), OCR_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    private void appendLines(List<OcrLine> output, int pageNumber, Text text) {
        List<Text.Line> lines = new ArrayList<>();
        for (Text.TextBlock block : text.getTextBlocks()) lines.addAll(block.getLines());
        lines.sort(Comparator.comparingInt(line -> line.getBoundingBox() == null ? 0 : line.getBoundingBox().top));
        for (Text.Line line : lines) {
            if (!line.getText().trim().isEmpty()) output.add(new OcrLine(pageNumber, line.getText(), line.getBoundingBox()));
        }
    }

    private Bitmap preprocess(Bitmap source) {
        Bitmap output = Bitmap.createBitmap(source.getWidth(), source.getHeight(), Bitmap.Config.ARGB_8888);
        int[] pixels = new int[source.getWidth() * source.getHeight()];
        source.getPixels(pixels, 0, source.getWidth(), 0, 0, source.getWidth(), source.getHeight());
        for (int i = 0; i < pixels.length; i++) {
            int color = pixels[i];
            int gray = (Color.red(color) * 30 + Color.green(color) * 59 + Color.blue(color) * 11) / 100;
            int value = gray > 170 ? 255 : (gray < 90 ? 0 : gray);
            pixels[i] = Color.rgb(value, value, value);
        }
        output.setPixels(pixels, 0, source.getWidth(), 0, 0, source.getWidth(), source.getHeight());
        return output;
    }
}
