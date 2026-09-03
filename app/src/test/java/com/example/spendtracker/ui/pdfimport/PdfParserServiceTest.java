package com.example.spendtracker.ui.pdfimport;

import com.example.spendtracker.domain.model.Transaction;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class PdfParserServiceTest {

    private PdfParserService parserService;

    @Before
    public void setUp() {
        parserService = new PdfParserService();
    }

    @Test
    public void testParseJsonToTransactionsSuccess() throws Exception {
        JSONObject rootJson = new JSONObject();
        rootJson.put("fileName", "HDFC_Statement.pdf");
        rootJson.put("bankName", "HDFC");
        rootJson.put("totalFound", 2);

        JSONArray jsonArray = new JSONArray();

        JSONObject row1 = new JSONObject();
        row1.put("bankName", "HDFC");
        row1.put("date", "05/01/2024");
        row1.put("dateMillis", 1704412800000L);
        row1.put("narration", "UPI-SWIGGY-12345678-SWIGGY@OKAXIS");
        row1.put("referenceNo", "000000123456");
        row1.put("upiId", "SWIGGY@OKAXIS");
        row1.put("merchant", "SWIGGY");
        row1.put("type", "EXPENSE");
        row1.put("amount", 450.00);
        row1.put("debitAmount", 450.00);

        JSONObject row2 = new JSONObject();
        row2.put("bankName", "HDFC");
        row2.put("date", "06/01/2024");
        row2.put("dateMillis", 1704499200000L);
        row2.put("narration", "FUND TRANSFER TO SAVINGS");
        row2.put("referenceNo", "000000876543");
        row2.put("upiId", "");
        row2.put("merchant", "SAVINGS");
        row2.put("type", "TRANSFER");
        row2.put("amount", 10000.00);
        row2.put("debitAmount", 10000.00);

        jsonArray.put(row1);
        jsonArray.put(row2);
        rootJson.put("transactions", jsonArray);

        List<Transaction> existing = new ArrayList<>();
        PdfParserService.FileImportResult result = parserService.parseJsonToTransactions(rootJson, existing, null);

        assertNotNull(result);
        assertEquals("HDFC_Statement.pdf", result.fileName);
        assertEquals("HDFC", result.bankName);
        assertEquals(2, result.totalFound);
        assertEquals(2, result.successfullyParsed);
        assertEquals(0, result.duplicatesSkipped);
        assertEquals(2, result.transactions.size());

        Transaction t1 = result.transactions.get(0);
        assertEquals(450.00, t1.getAmount(), 0.001);
        assertEquals("EXPENSE", t1.getType());
        assertEquals("HDFC", t1.getBankName());

        Transaction t2 = result.transactions.get(1);
        assertEquals(10000.00, t2.getAmount(), 0.001);
        assertEquals("TRANSFER", t2.getType());
        assertEquals("Transfer", t2.getCategory());
    }

    @Test
    public void testParseJsonToTransactionsWithDuplicates() throws Exception {
        JSONObject rootJson = new JSONObject();
        rootJson.put("fileName", "HDFC_Statement.pdf");
        rootJson.put("bankName", "HDFC");
        rootJson.put("totalFound", 1);

        JSONArray jsonArray = new JSONArray();

        JSONObject row1 = new JSONObject();
        row1.put("bankName", "HDFC");
        row1.put("date", "05/01/2024");
        row1.put("dateMillis", 1704412800000L);
        row1.put("narration", "UPI-SWIGGY-12345678-SWIGGY@OKAXIS");
        row1.put("referenceNo", "");
        row1.put("upiId", "SWIGGY@OKAXIS");
        row1.put("merchant", "SWIGGY");
        row1.put("type", "EXPENSE");
        row1.put("amount", 450.00);
        row1.put("debitAmount", 450.00);

        jsonArray.put(row1);
        rootJson.put("transactions", jsonArray);

        Transaction existingTx = new Transaction();
        existingTx.setBankName("HDFC");
        existingTx.setAmount(450.00);
        existingTx.setDate(1704412800000L);
        existingTx.setReceiverName("SWIGGY");

        List<Transaction> existing = new ArrayList<>();
        existing.add(existingTx);

        PdfParserService.FileImportResult result = parserService.parseJsonToTransactions(rootJson, existing, null);

        assertNotNull(result);
        assertEquals(0, result.successfullyParsed);
        assertEquals(1, result.duplicatesSkipped);
        assertNotNull(result.error);
        assertTrue(result.error.contains("duplicates"));
    }
}
