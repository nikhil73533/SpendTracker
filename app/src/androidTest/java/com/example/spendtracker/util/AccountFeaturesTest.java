package com.example.spendtracker.util;
import android.content.Context;
import androidx.room.Room;
import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.example.spendtracker.data.local.database.SpendTrackerDatabase;
import com.example.spendtracker.data.local.entity.TransactionEntity;
import com.example.spendtracker.domain.model.Transaction;
import org.junit.*;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;
@RunWith(AndroidJUnit4.class)
public class AccountFeaturesTest {
    @Rule public InstantTaskExecutorRule rule=new InstantTaskExecutorRule();
    @Test public void accountTotalsAndHistoryIncludeTransfersAndBookmarkSurvivesReload() throws Exception {
        Context c=ApplicationProvider.getApplicationContext();
        SpendTrackerDatabase db=Room.inMemoryDatabaseBuilder(c,SpendTrackerDatabase.class).allowMainThreadQueries().build();
        try {
            for(String type:new String[]{"INCOME","EXPENSE","TRANSFER"}) {
                TransactionEntity row=new TransactionEntity(); row.type=type; row.amount=10; row.date=1000;
                row.category=type.equals("TRANSFER")?"Transfer":"Other"; row.direction=type.equals("INCOME")?"CREDIT":"DEBIT";
                if(type.equals("INCOME")) row.sender="Alex"; else row.receiverName="Alex";
                db.transactionDao().insertTransaction(row);
            }
            java.util.List<com.example.spendtracker.data.local.dao.TransactionDao.AccountSummary> rows=LiveDataTestUtil.getOrAwaitValue(db.transactionDao().getUniqueAccounts());
            assertEquals(1,rows.size()); assertEquals(10,rows.get(0).totalIncome,0); assertEquals(10,rows.get(0).totalExpense,0); assertEquals(10,rows.get(0).totalTransfer,0);
            java.util.List<TransactionEntity> history=LiveDataTestUtil.getOrAwaitValue(db.transactionDao().getAccountHistory("Alex",0,2000)); assertEquals(3,history.size());
            int id=history.get(0).id; db.transactionDao().setBookmarked(id,true); assertTrue(db.transactionDao().getTransactionByIdSync(id).bookmarked);
            db.transactionDao().setBookmarked(id,false); assertFalse(db.transactionDao().getTransactionByIdSync(id).bookmarked);
        } finally { db.close(); }
    }
    @Test public void longAccountReportProducesPdfAndBoundedImages() throws Exception {
        Context c=ApplicationProvider.getApplicationContext(); java.util.List<Transaction> rows=new java.util.ArrayList<>();
        for(int i=0;i<80;i++) { Transaction t=new Transaction(); t.setAmount(i+1); t.setDate(1790812800000L); t.setCategory("Food"); rows.add(t); }
        java.util.ArrayList<android.net.Uri> pdfs=AccountReport.generate(c,"Test account",rows,false,false); assertEquals(1,pdfs.size());
        try(android.os.ParcelFileDescriptor fd=c.getContentResolver().openFileDescriptor(pdfs.get(0),"r"); android.graphics.pdf.PdfRenderer renderer=new android.graphics.pdf.PdfRenderer(fd)) { assertTrue(renderer.getPageCount()>1); }
        java.util.ArrayList<android.net.Uri> images=AccountReport.generate(c,"Private account",rows,true,true); assertTrue(images.size()>1);
        try(java.io.InputStream stream=c.getContentResolver().openInputStream(images.get(0))) { android.graphics.Bitmap bitmap=android.graphics.BitmapFactory.decodeStream(stream); assertEquals(1190,bitmap.getWidth()); bitmap.recycle(); }
    }
}
