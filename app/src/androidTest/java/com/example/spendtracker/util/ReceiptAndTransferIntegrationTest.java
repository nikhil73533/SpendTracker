package com.example.spendtracker.util;

import android.content.Context;
import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.example.spendtracker.data.local.database.SpendTrackerDatabase;
import com.example.spendtracker.data.local.entity.TransactionEntity;
import com.example.spendtracker.data.repository.TransactionRepositoryImpl;
import com.example.spendtracker.domain.model.Transaction;
import org.junit.*;
import org.junit.runner.RunWith;
import java.io.File;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class ReceiptAndTransferIntegrationTest {
    @Rule public InstantTaskExecutorRule rule = new InstantTaskExecutorRule();
    private SpendTrackerDatabase db, clone;
    private Context context;
    @Before public void setup() {
        context = ApplicationProvider.getApplicationContext();
        db = Room.inMemoryDatabaseBuilder(context, SpendTrackerDatabase.class).allowMainThreadQueries().build();
        clone = Room.inMemoryDatabaseBuilder(context, SpendTrackerDatabase.class).allowMainThreadQueries().build();
    }
    @After public void close() { db.close(); clone.close(); }
    @Test public void transferTotalsUseDirectionAndExcludeDeletedRows() throws Exception {
        TransactionEntity incoming = row(100, "CREDIT"); incoming.toAccount = "Own account";
        TransactionEntity outgoing = row(40, "DEBIT"); outgoing.sender = "BANK-SMS";
        db.transactionDao().insertTransaction(incoming); db.transactionDao().insertTransaction(outgoing);
        TransactionEntity deleted = row(999, "CREDIT"); deleted.status = "DELETED";
        db.transactionDao().insertTransaction(deleted);
        assertEquals(100, LiveDataTestUtil.getOrAwaitValue(db.transactionDao().getTransferIncoming(0, 2000)), .001);
        assertEquals(40, LiveDataTestUtil.getOrAwaitValue(db.transactionDao().getTransferOutgoing(0, 2000)), .001);
        assertEquals(60, LiveDataTestUtil.getOrAwaitValue(db.transactionDao().getTransferTotal(0, 2000)), .001);
        assertEquals(140, LiveDataTestUtil.getOrAwaitValue(db.transactionDao().getDailyTotals(0, 2000, "TRANSFER")).get(0).total, .001);
    }
    @Test public void repositoryRetainsUncertainConfidenceAndConfirmationClearsIt() throws Exception {
        TransactionRepositoryImpl repository = new TransactionRepositoryImpl(context, db.transactionDao(), clone.transactionDao(),
                db.categoryDao(), db.transactionGroupDao(), db.repeatedAlertDao(), db);
        Transaction tx = new Transaction(); tx.setAmount(20); tx.setCategory("Uncategorized"); tx.setConfidenceScore(0);
        repository.addTransaction(tx);
        long deadline = System.currentTimeMillis() + 5000;
        while (repository.getTransactionsSync().isEmpty() && System.currentTimeMillis() < deadline) Thread.sleep(20);
        Transaction saved = repository.getTransactionsSync().get(0);
        assertEquals(0, saved.getConfidenceScore(), 0);
        saved.setCategory("Food"); repository.updateConfirmedTransaction(saved);
        do { Thread.sleep(20); saved = repository.getTransactionsSync().get(0); }
        while (saved.getConfidenceScore() < 1 && System.currentTimeMillis() < deadline);
        assertEquals(1, saved.getConfidenceScore(), 0);
        // Wait for the asynchronous learning step before closing the test database.
        Thread.sleep(200);
    }
    @Test public void receiptContainsProfileAndReferenceAndRendersLongText() throws Exception {
        com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(context);
        Transaction tx = new Transaction(); tx.setAmount(1250.5); tx.setCategory("Transfer"); tx.setType("TRANSFER");
        tx.setDirection("CREDIT"); tx.setReferenceNumber("UTR-TEST-123"); tx.setDate(1789810200000L);
        tx.setSender("Alex Kumar"); tx.setBankName("Test Bank"); tx.setDescription("Synthetic receipt verification. ".repeat(160));
        File file = TransactionReceipt.create(context, tx, new UserProfile("Nikhil Patil", "nikhil@example.com", "+91 9000000000"));
        try (com.tom_roush.pdfbox.pdmodel.PDDocument pdf = com.tom_roush.pdfbox.pdmodel.PDDocument.load(file)) {
            String text = new com.tom_roush.pdfbox.text.PDFTextStripper().getText(pdf);
            assertTrue(text.contains("nikhil@example.com")); assertTrue(text.contains("UTR-TEST-123"));
            assertTrue(text.contains("Incoming transfer")); assertTrue(pdf.getNumberOfPages() > 1);
        }
        try (android.os.ParcelFileDescriptor fd = android.os.ParcelFileDescriptor.open(file, android.os.ParcelFileDescriptor.MODE_READ_ONLY);
             android.graphics.pdf.PdfRenderer renderer = new android.graphics.pdf.PdfRenderer(fd)) {
            android.graphics.pdf.PdfRenderer.Page page = renderer.openPage(0);
            android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(595, 842, android.graphics.Bitmap.Config.ARGB_8888);
            bitmap.eraseColor(android.graphics.Color.WHITE);
            page.render(bitmap, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY); page.close();
            try (java.io.FileOutputStream out = context.openFileOutput("receipt-preview.png", Context.MODE_PRIVATE)) {
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out);
            }
            bitmap.recycle();
        }
    }
    private TransactionEntity row(double amount, String direction) {
        TransactionEntity e = new TransactionEntity(); e.amount = amount; e.category = "Transfer";
        e.type = "TRANSFER"; e.direction = direction; e.date = 1000; return e;
    }
}
