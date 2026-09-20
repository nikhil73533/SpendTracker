package com.example.spendtracker.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.pdf.PdfRenderer;
import android.os.ParcelFileDescriptor;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.example.spendtracker.domain.model.Transaction;
import com.example.spendtracker.ui.dashboard.DashboardViewModel;
import com.example.spendtracker.ui.dashboard.MonthlySummaryAdapter;
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.text.PDFTextStripper;
import java.io.File;
import java.io.FileOutputStream;
import java.util.Arrays;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Device-side check for PDF text, page creation and first-page rendering. */
@RunWith(AndroidJUnit4.class)
public class PdfReportRenderIntegrationTest {
    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        PDFBoxResourceLoader.init(context);
    }

    @Test
    public void redesignedReceiptAndSummaryReportsRender() throws Exception {
        Transaction income = transaction(1, 5000, "Salary", "INCOME", "Employer", "Account");
        income.setSender("Example Employer");
        Transaction expense = transaction(2, 650, "Food", "EXPENSE", "Cafe", "UPI");
        Transaction incomingTransfer = transaction(3, 1200, "Transfer", "TRANSFER", "Savings", "Account");
        incomingTransfer.setDirection("CREDIT");
        incomingTransfer.setReferenceNumber("UTR-QA-1200");
        Transaction outgoingTransfer = transaction(4, 300, "Transfer", "TRANSFER", "Bills", "Account");
        outgoingTransfer.setDirection("DEBIT");

        File receipt = TransactionReceipt.create(context, incomingTransfer,
                new UserProfile("Nikhil Gupta", "nikhil@example.com", "+91 9000000000"));
        PdfReportService.ReportPayload payload = PdfReportService.buildPayload(
                new DashboardViewModel.TotalPageData(0, 0, 0, 0, 0, 0, 0),
                Arrays.asList(income, expense, incomingTransfer, outgoingTransfer), "PDF QA");
        File totalReport = PdfReportService.createPdfDocument(context, payload);
        File monthlyReport = PdfReportService.generateMonthlySummaryReport(context, Arrays.asList(
                new MonthlySummaryAdapter.MonthSummary(1788244200000L, 5000, 650, Arrays.asList(
                        new MonthlySummaryAdapter.WeeklySummary("01 Sep - 07 Sep", 5000, 650),
                        new MonthlySummaryAdapter.WeeklySummary("08 Sep - 14 Sep", 0, 0)))));

        assertPdfText(receipt, "Transaction receipt", "UTR-QA-1200", "Incoming transfer", "+91 9000000000");
        assertPdfText(totalReport, "SpendTracker Financial Analysis", "TRANSFER IN", "TRANSFER OUT");
        assertPdfText(monthlyReport, "Monthly summary", "September 2026", "01 Sep - 07 Sep");
        renderPage(receipt, "qa-receipt-preview.png", 0);
        renderPage(totalReport, "qa-total-preview.png", 0);
        renderPage(totalReport, "qa-total-last-preview.png", -1);
        renderPage(monthlyReport, "qa-monthly-preview.png", 0);
    }

    private Transaction transaction(int id, double amount, String category, String type, String receiver, String sourceType) {
        Transaction transaction = new Transaction(id, amount, category, "QA transaction", type,
                1788426000000L, "QA Bank", "", "", receiver, "QA Bank", sourceType);
        transaction.setDirection("INCOME".equals(type) ? "CREDIT" : "DEBIT");
        return transaction;
    }

    private void assertPdfText(File file, String... expected) throws Exception {
        try (PDDocument pdf = PDDocument.load(file)) {
            assertTrue(pdf.getNumberOfPages() > 0);
            String text = new PDFTextStripper().getText(pdf);
            for (String value : expected) assertTrue("Missing PDF text: " + value, text.contains(value));
        }
    }

    private void renderPage(File file, String previewName, int requestedPage) throws Exception {
        try (ParcelFileDescriptor descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
             PdfRenderer renderer = new PdfRenderer(descriptor)) {
            int pageIndex = requestedPage < 0 ? renderer.getPageCount() - 1 : requestedPage;
            PdfRenderer.Page page = renderer.openPage(pageIndex);
            Bitmap bitmap = Bitmap.createBitmap(page.getWidth(), page.getHeight(), Bitmap.Config.ARGB_8888);
            bitmap.eraseColor(Color.WHITE);
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
            page.close();
            assertFalse("PDF page rendered blank", bitmap.getPixel(50, 50) == Color.WHITE);
            try (FileOutputStream output = context.openFileOutput(previewName, Context.MODE_PRIVATE)) {
                assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output));
            }
            bitmap.recycle();
        }
    }
}
