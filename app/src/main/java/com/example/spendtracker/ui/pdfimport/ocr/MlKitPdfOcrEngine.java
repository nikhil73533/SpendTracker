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
import com.example.spendtracker.ui.pdfimport.parser.GenericStatementParser;
import com.example.spendtracker.ui.pdfimport.parser.StatementExtractionQuality;
import com.example.spendtracker.ui.pdfimport.parser.RawTransactionRow;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.io.File;

/** On-device OCR for scanned statement pages. Runs only from a background thread. */
public class MlKitPdfOcrEngine implements OcrEngine {
    // Small statement fonts need more pixels per glyph; retain the memory ceiling.
    private static final float RENDER_SCALE = 4.0f;
    private static final int MAX_RENDER_PIXELS = 8_000_000;
    private static final long OCR_TIMEOUT_SECONDS = 45;

    @Override
    public OcrDocument recognizePdf(Context context, Uri uri) throws Exception {
        try (ParcelFileDescriptor descriptor = context.getContentResolver().openFileDescriptor(uri, "r")) {
            if (descriptor == null) throw new IllegalStateException("Unable to open selected PDF");
            return recognizePdf(descriptor);
        }
    }

    @Override
    public OcrDocument recognizePdf(File file) throws Exception {
        try (ParcelFileDescriptor descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)) {
            return recognizePdf(descriptor);
        }
    }

    @Override
    public OcrDocument recognizeImage(Context context, Uri uri) throws Exception {
        TextRecognizer recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        try {
            InputImage image = InputImage.fromFilePath(context, uri);
            return new OcrDocument(lines(1, recognize(recognizer, image)));
        } finally {
            recognizer.close();
        }
    }

    private OcrDocument recognizePdf(ParcelFileDescriptor descriptor) throws Exception {
        List<OcrLine> allLines = new ArrayList<>();
        TextRecognizer recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        try (PdfRenderer renderer = new PdfRenderer(descriptor)) {
            for (int pageIndex = 0; pageIndex < renderer.getPageCount(); pageIndex++) {
                try (PdfRenderer.Page page = renderer.openPage(pageIndex)) {
                    Bitmap original = renderPage(page);
                    try {
                        List<OcrLine> best = lines(pageIndex + 1, recognize(recognizer, InputImage.fromBitmap(original, 0)));
                        String table = new OcrDocument(best).getText();
                        List<RawTransactionRow> rows = new GenericStatementParser().parse(table);
                        // Names alone can be plentiful. Retry when transaction fields are missing.
                        if (StatementExtractionQuality.needsRetry(table, rows)) {
                            Bitmap processed = preprocess(original);
                            try {
                                List<OcrLine> retry = lines(pageIndex + 1, recognize(recognizer, InputImage.fromBitmap(processed, 0)));
                                String retryTable = new OcrDocument(retry).getText();
                                if (StatementExtractionQuality.prefer(retryTable,
                                        new GenericStatementParser().parse(retryTable), table, rows)) best = retry;
                            } catch (Exception retryError) {
                                // Keep the original recognition if an optional retry fails.
                                android.util.Log.w("StatementOCR", "Contrast retry failed", retryError);
                            } finally {
                                processed.recycle();
                            }
                        }
                        allLines.addAll(best);
                    } finally {
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
        float scale = Math.min(RENDER_SCALE,
                (float) Math.sqrt((double) MAX_RENDER_PIXELS / ((double) page.getWidth() * page.getHeight())));
        int width = Math.max(1, Math.round(page.getWidth() * scale));
        int height = Math.max(1, Math.round(page.getHeight() * scale));
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(Color.WHITE);
        Matrix matrix = new Matrix();
        matrix.postScale(scale, scale);
        page.render(bitmap, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
        return bitmap;
    }

    private Text recognize(TextRecognizer recognizer, InputImage image) throws Exception {
        return Tasks.await(recognizer.process(image), OCR_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    private List<OcrLine> lines(int pageNumber, Text text) {
        List<OcrLine> output = new ArrayList<>();
        List<Text.Line> lines = new ArrayList<>();
        for (Text.TextBlock block : text.getTextBlocks()) lines.addAll(block.getLines());
        lines.sort(Comparator.comparingInt(line -> line.getBoundingBox() == null ? 0 : line.getBoundingBox().top));
        for (Text.Line line : lines) {
            if (line.getElements().isEmpty()) {
                if (!line.getText().trim().isEmpty()) output.add(new OcrLine(pageNumber, line.getText(), line.getBoundingBox()));
            } else {
                for (Text.Element word : line.getElements()) {
                    if (!word.getText().trim().isEmpty()) output.add(new OcrLine(pageNumber, word.getText(), word.getBoundingBox()));
                }
            }
        }
        return output;
    }

    private Bitmap preprocess(Bitmap source) {
        Bitmap output = Bitmap.createBitmap(source.getWidth(), source.getHeight(), Bitmap.Config.ARGB_8888);
        int[] pixels = new int[source.getWidth()];
        for (int y = 0; y < source.getHeight(); y++) {
            source.getPixels(pixels, 0, source.getWidth(), 0, y, source.getWidth(), 1);
            for (int i = 0; i < pixels.length; i++) {
                int color = pixels[i];
                int gray = (Color.red(color) * 30 + Color.green(color) * 59 + Color.blue(color) * 11) / 100;
                int value = gray > 170 ? 255 : (gray < 90 ? 0 : gray);
                pixels[i] = Color.rgb(value, value, value);
            }
            output.setPixels(pixels, 0, source.getWidth(), 0, y, source.getWidth(), 1);
        }
        return output;
    }
}
