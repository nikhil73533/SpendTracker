package com.example.spendtracker.ui.pdfimport;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.io.FileOutputStream;
import static org.junit.Assert.*;

/** Synthetic statements exercise the actual bundled OCR model, not mocked OCR output. */
@RunWith(AndroidJUnit4.class)
public class StatementOcrIntegrationTest {
    @Test public void extractsDigitalPdfWithEmptyDirectionColumns() throws Exception { verify(false); }
    @Test public void extractsScannedPdfWithEmptyDirectionColumns() throws Exception { verify(true); }

    private void verify(boolean scanned) throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        File file = File.createTempFile("synthetic-statement-", ".pdf", context.getCacheDir());
        try {
            PdfDocument document = new PdfDocument();
            try {
                PdfDocument.Page page = document.startPage(new PdfDocument.PageInfo.Builder(1000, 720, 1).create());
                if (scanned) {
                    Bitmap bitmap = Bitmap.createBitmap(1000, 720, Bitmap.Config.ARGB_8888);
                    try {
                        drawStatement(new Canvas(bitmap));
                        page.getCanvas().drawBitmap(bitmap, 0, 0, null);
                        document.finishPage(page);
                        try (FileOutputStream output = new FileOutputStream(file)) { document.writeTo(output); }
                    } finally { bitmap.recycle(); }
                } else {
                    drawStatement(page.getCanvas());
                    document.finishPage(page);
                    try (FileOutputStream output = new FileOutputStream(file)) { document.writeTo(output); }
                }
            }
            finally { document.close(); }
            JSONObject result = new PdfParserService().parsePdfToJson(context, Uri.fromFile(file));
            assertEquals("SBI", result.getString("bankName"));
            assertEquals(scanned ? "OCR" : "PDF_TEXT", result.getString("extractionMethod"));
            JSONArray rows = result.getJSONArray("transactions");
            assertEquals(result.toString(), 2, rows.length());
            assertEquals(1250.0, rows.getJSONObject(0).getDouble("amount"), .001);
            assertEquals("DEBIT", rows.getJSONObject(0).getString("direction"));
            assertEquals("2026-09-04", rows.getJSONObject(0).getString("date"));
            assertEquals(5000.0, rows.getJSONObject(1).getDouble("amount"), .001);
            assertEquals("CREDIT", rows.getJSONObject(1).getString("direction"));
            assertEquals("2026-09-05", rows.getJSONObject(1).getString("date"));
        } finally {
            assertTrue("Remove synthetic statement", file.delete());
        }
    }

    private void drawStatement(Canvas canvas) {
        canvas.drawColor(Color.WHITE);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.BLACK);
        paint.setTextSize(20);
        canvas.drawText("STATE BANK OF INDIA", 20, 45, paint);
        paint.setTextSize(16);
        String[] headers = {"Txn Date", "Value Date", "Description", "Ref No", "Debit", "Credit", "Balance"};
        int[] x = {20, 150, 300, 510, 620, 730, 850};
        for (int i = 0; i < headers.length; i++) canvas.drawText(headers[i], x[i], 120, paint);
        String[][] rows = {
                {"04-09-2026", "06-09-2026", "LOCAL STORE", "123456", "1,250.00", "", "8,750.00 CR"},
                {"05-09-2026", "07-09-2026", "ACME PAYROLL", "123457", "", "5,000.00", "13,750.00 CR"}
        };
        for (int row = 0; row < rows.length; row++) {
            for (int col = 0; col < x.length; col++) canvas.drawText(rows[row][col], x[col], 160 + row * 50, paint);
        }
    }
}
