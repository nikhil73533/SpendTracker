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

    @Test public void extractsDigitalRemarksTable() throws Exception { verifyRemarks(false); }
    @Test public void extractsScannedRemarksTable() throws Exception { verifyRemarks(true); }

    /** Optional local reference image supplied at test time, never bundled into the app. */
    @Test public void extractsSuppliedScreenshot() throws Exception {
        String asset = androidx.test.platform.app.InstrumentationRegistry.getArguments().getString("statementScreenshot");
        org.junit.Assume.assumeNotNull(asset);
        Context context = ApplicationProvider.getApplicationContext();
        Bitmap screenshot;
        try (java.io.InputStream input = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
                .getContext().getAssets().open(asset)) {
            screenshot = android.graphics.BitmapFactory.decodeStream(input);
        }
        assertNotNull(screenshot);
        File file = File.createTempFile("reference-statement-", ".pdf", context.getCacheDir());
        PdfDocument document = new PdfDocument();
        try {
            PdfDocument.Page page = document.startPage(new PdfDocument.PageInfo.Builder(659, 932, 1).create());
            page.getCanvas().drawBitmap(screenshot, new android.graphics.Rect(29, 148, 688, 1080),
                    new android.graphics.Rect(0, 0, 659, 932), new Paint(Paint.FILTER_BITMAP_FLAG));
            document.finishPage(page);
            try (FileOutputStream output = new FileOutputStream(file)) { document.writeTo(output); }
            JSONObject result = new PdfParserService().parsePdfToJson(context, Uri.fromFile(file));
            verifyTenRows(result);
            JSONArray rows = result.getJSONArray("transactions");
            for (int i = 0; i < rows.length(); i++) {
                android.util.Log.i("StatementReferenceTest", i + ": " + rows.getJSONObject(i).optString("merchant")
                        + " / " + rows.getJSONObject(i).optDouble("amount"));
            }
        } finally { document.close(); screenshot.recycle(); assertTrue(file.delete()); }
    }

    private void verifyRemarks(boolean scanned) throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        File file = File.createTempFile("remarks-statement-", ".pdf", context.getCacheDir());
        PdfDocument document = new PdfDocument();
        try {
            PdfDocument.Page page = document.startPage(new PdfDocument.PageInfo.Builder(660, 932, 1).create());
            Bitmap bitmap = null;
            try {
                Canvas canvas = page.getCanvas();
                if (scanned) {
                    bitmap = Bitmap.createBitmap(1320, 1864, Bitmap.Config.ARGB_8888);
                    canvas = new Canvas(bitmap);
                    canvas.scale(2, 2);
                }
                drawRemarks(canvas);
                if (bitmap != null) page.getCanvas().drawBitmap(bitmap, null,
                        new android.graphics.Rect(0, 0, 660, 932), null);
                document.finishPage(page);
                try (FileOutputStream output = new FileOutputStream(file)) { document.writeTo(output); }
            } finally { if (bitmap != null) bitmap.recycle(); }
            JSONObject result = new PdfParserService().parsePdfToJson(context, Uri.fromFile(file));
            verifyTenRows(result);
            assertEquals(scanned ? "OCR" : "PDF_TEXT", result.getString("extractionMethod"));
            String[] names = {"Ambe Pujan", "JIO FINANCIAL SERV", "Radhika ge", "DEVRAJ KAH", "PAWAN MEEN",
                    "Shri Vrind", "RUCHI KANU", "", "YOGESH KUM", "VINAY KHAN"};
            for (int i = 0; i < names.length; i++) {
                String actual = result.getJSONArray("transactions").getJSONObject(i).getString("merchant");
                // Scans can split a printed word (YOGESH -> YO GESH). Require every
                // letter in order; the digital extraction must also preserve spacing.
                assertEquals(result.toString(), scanned ? names[i].replace(" ", "") : names[i],
                        scanned ? actual.replace(" ", "") : actual);
            }
        } finally { document.close(); assertTrue(file.delete()); }
    }

    private void verifyTenRows(JSONObject result) throws Exception {
        JSONArray rows = result.getJSONArray("transactions");
        assertEquals(result.toString(), 10, rows.length());
        double[] amounts = {230, 6, 276, 580, 500, 160, 420, 2100, 1500, 4000};
        for (int i = 0; i < amounts.length; i++) {
            JSONObject row = rows.getJSONObject(i);
            assertEquals(row.toString(), amounts[i], row.getDouble("amount"), .001);
            assertEquals(i == 1 ? "CREDIT" : "DEBIT", row.getString("direction"));
            assertEquals(i < 2 ? "2026-08-26" : i < 4 ? "2026-08-27" : i < 9
                    ? "2026-08-28" : "2026-08-29", row.getString("date"));
            assertEquals("DATE_ONLY", row.getString("timestampPrecision"));
            if (i != 7) assertFalse(result.toString(), row.getString("merchant").isEmpty());
        }
    }

    private void drawRemarks(Canvas canvas) {
        canvas.drawColor(Color.WHITE);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(Color.BLACK);
        paint.setTextSize(20);
        canvas.drawText("ICICI Bank", 260, 50, paint);
        paint.setTextSize(8);
        String[] labels = {"S No.", "Transaction", "Cheque Number", "Transaction Remarks", "Withdrawal", "Deposit", "Balance"};
        int[] x = {24, 65, 135, 270, 436, 519, 588};
        for (int i = 0; i < x.length; i++) canvas.drawText(labels[i], x[i], 230, paint);
        canvas.drawText("Date", 81, 240, paint);
        canvas.drawText("Amount (INR)", 437, 240, paint);
        canvas.drawText("Amount (INR)", 510, 240, paint);
        canvas.drawText("(INR)", 595, 240, paint);
        String[] names = {"Ambe Pujan", "NACH trxn", "Radhika ge", "DEVRAJ KAH", "PAWAN MEEN",
                "Shri Vrind", "RUCHI KANU", "ATM trxn", "YOGESH KUM", "VINAY KHAN"};
        double[] amounts = {230, 6, 276, 580, 500, 160, 420, 2100, 1500, 4000};
        double balance = 199414.73;
        for (int i = 0; i < names.length; i++) {
            int y = 265 + i * 55;
            canvas.drawText(Integer.toString(i + 1), 32, y, paint);
            canvas.drawText(i < 2 ? "26.08.2026" : i < 4 ? "27.08.2026" : i < 9 ? "28.08.2026" : "29.08.2026", 67, y, paint);
            paint.setFakeBoldText(true);
            canvas.drawText(names[i], 212, y - 4, paint);
            paint.setFakeBoldText(false);
            canvas.drawText(i == 1 ? "ACH/JIO FINANCIAL SERV/261092782" : i == 7 ? "NFS/CASH WDL/624012012802/JAIPUR"
                    : "UPI/" + names[i] + "/person" + i + "@ybl/Paid via C/YES", 212, y + 7, paint);
            canvas.drawText("BANK", 212, y + 18, paint);
            canvas.drawText("L/660406298332/YCD4cd23860b0384680bc1e618", 212, y + 29, paint);
            String amount = String.format(java.util.Locale.ROOT, "%.2f", amounts[i]);
            canvas.drawText(amount, (i == 1 ? 574 : 501) - paint.measureText(amount), y, paint);
            balance += i == 1 ? amounts[i] : -amounts[i];
            String balanceText = String.format(java.util.Locale.ROOT, "%.2f", balance);
            canvas.drawText(balanceText, 634 - paint.measureText(balanceText), y, paint);
        }
        canvas.drawText("Sincerely,", 20, 830, paint);
        canvas.drawText("Team ICICI Bank", 20, 845, paint);
    }

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
