package com.example.spendtracker.ui.pdfimport;

import com.example.spendtracker.domain.model.Transaction;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class PdfParserServiceTest {

    private PdfParserService parserService;

    @Test
    public void iciciCounterpartyNamesAndHandlesRemainDistinctThroughJson() throws Exception {
        String text = "ICICI Bank\nDATE\tNARRATION\tDEBIT\tCREDIT\tBALANCE\n"
                + "16/09/2026\tMMT/IMPS/123456789012/SHINE HAIR/AXIS BANK LTD\t\t500.00\t1500.00\n"
                + "17/09/2026\tUPI/123456789013/UPI/ravi@okaxis/State Bank Of India\t100.00\t\t1400.00\n"
                + "17/09/2026\tVPS/ACT/202609161234/123456789014/HYDERABAD\t200.00\t\t1200.00\n";
        JSONArray rows = parserService.parsePageTexts(java.util.Collections.singletonList(text),
                java.util.Collections.emptyMap(), false).getJSONArray("transactions");
        assertEquals(3, rows.length());
        assertEquals("SHINE HAIR", rows.getJSONObject(0).getString("senderName"));
        assertEquals("CREDIT", rows.getJSONObject(0).getString("direction"));
        assertEquals("ravi@okaxis", rows.getJSONObject(1).getString("merchant"));
        assertTrue(rows.getJSONObject(1).getBoolean("counterpartyIsHandle"));
        assertTrue(rows.getJSONObject(1).isNull("counterpartyName"));
        assertEquals("ACT", rows.getJSONObject(2).getString("receiverName"));
        assertEquals(200, rows.getJSONObject(2).getDouble("amount"), .001);
    }

    @Test
    public void combinesDigitalAndScannedPagesWithoutReplacingValidDigitalRows() throws Exception {
        String header = "DATE\tNARRATION\tDEBIT\tCREDIT\tBALANCE\n";
        String digital = "SBI\n" + header + "04/09/2026\tSHOP\t125.00\t\t875.00\n";
        String scanned = header + "05/09/2026\tSALARY\t\t5000.00\t5875.00\n";
        java.util.Map<Integer, String> ocr = new java.util.LinkedHashMap<>();
        // Simulate less accurate OCR of the digital page. Equal counts retain embedded cells.
        ocr.put(1, header + "04/09/2026\tSHOP\t725.00\t\t875.00\n");
        ocr.put(2, scanned);
        JSONObject result = parserService.parsePageTexts(java.util.Arrays.asList(digital, ""), ocr, true);
        assertEquals("MIXED", result.getString("extractionMethod"));
        JSONArray rows = result.getJSONArray("transactions");
        assertEquals(2, rows.length());
        assertEquals(125, rows.getJSONObject(0).getDouble("amount"), .001);
        assertEquals(5000, rows.getJSONObject(1).getDouble("amount"), .001);
        assertEquals("CREDIT", rows.getJSONObject(1).getString("direction"));
        assertEquals("SBI", rows.getJSONObject(1).getString("bankName"));
        assertEquals(2, rows.getJSONObject(1).getInt("pageNumber"));
    }

    @Test
    public void preservesPrintedTimeAndDoesNotInventTimeForDateOnlyRows() throws Exception {
        String text = "DATE\tNARRATION\tAMOUNT\tTYPE\tBALANCE\n"
                + "04/09/2026 9:35 PM\tSHOP\t125.00\tDebited\t875.00\n"
                + "05/09/2026\tPAYROLL\t5000.00\tIncome\t5875.00\n";
        JSONArray rows = parserService.parsePageTexts(java.util.Collections.singletonList(text),
                java.util.Collections.emptyMap(), false).getJSONArray("transactions");
        assertEquals(2, rows.length());
        assertEquals("DATE_TIME", rows.getJSONObject(0).getString("timestampPrecision"));
        java.time.LocalDateTime firstDate = java.time.Instant.ofEpochMilli(rows.getJSONObject(0).getLong("dateMillis"))
                .atZone(java.time.ZoneId.systemDefault()).toLocalDateTime();
        assertEquals(21, firstDate.getHour());
        assertEquals(35, firstDate.getMinute());
        assertEquals("DATE_ONLY", rows.getJSONObject(1).getString("timestampPrecision"));
    }

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

    @Test
    public void testStatementMetadataIsPreservedForStableImports() throws Exception {
        JSONObject rootJson = new JSONObject();
        rootJson.put("fileName", "statement.pdf");
        rootJson.put("bankName", "ICICI");
        rootJson.put("totalFound", 1);
        JSONObject row = new JSONObject();
        row.put("amount", 125.50);
        row.put("type", "EXPENSE");
        row.put("direction", "DEBIT");
        row.put("dateMillis", 1704412800000L);
        row.put("merchant", "PHARMACY");
        row.put("referenceNo", "UPI12345");
        row.put("sourceTransactionId", "ICICI:UPI12345");
        row.put("timestampPrecision", "DATE_ONLY");
        rootJson.put("transactions", new JSONArray().put(row));

        PdfParserService.FileImportResult result = parserService.parseJsonToTransactions(rootJson, new ArrayList<>(), null);

        assertEquals(1, result.successfullyParsed);
        Transaction transaction = result.transactions.get(0);
        assertEquals("ICICI:UPI12345", transaction.getSourceTransactionId());
        assertEquals("UPI12345", transaction.getReferenceNumber());
        assertEquals("DEBIT", transaction.getDirection());
        assertEquals("DATE_ONLY", transaction.getTimestampPrecision());
    }

    @Test
    public void testSyntheticSourceIdForTransactionWithoutReferenceNumber() throws Exception {
        Method createSourceTransactionId = PdfParserService.class.getDeclaredMethod(
                "createSourceTransactionId", String.class, String.class, String.class,
                String.class, String.class, double.class, String.class);
        createSourceTransactionId.setAccessible(true);

        String sourceId = (String) createSourceTransactionId.invoke(
                parserService, "ICICI", "", "04/09/2026", "23:06:10",
                "DEBIT", 1250.50, "UPI PAYMENT TO MERCHANT");

        assertNotNull(sourceId);
        assertTrue(sourceId.startsWith("SYN:"));
        assertEquals(68, sourceId.length());
    }

    @Test
    public void debitFundTransferRemainsExpenseWhenStatementDirectionIsDebit() throws Exception {
        JSONObject rootJson = new JSONObject();
        rootJson.put("fileName", "statement.pdf");
        rootJson.put("bankName", "SBI");
        rootJson.put("totalFound", 1);
        JSONObject row = new JSONObject();
        row.put("amount", 2000.00);
        row.put("type", "EXPENSE");
        row.put("direction", "DEBIT");
        row.put("dateMillis", 1788451200000L);
        row.put("narration", "FUND TRANSFER TO MERCHANT");
        row.put("merchant", "MERCHANT");
        rootJson.put("transactions", new JSONArray().put(row));

        PdfParserService.FileImportResult result = parserService.parseJsonToTransactions(
                rootJson, new ArrayList<>(), null);

        assertEquals(1, result.transactions.size());
        assertEquals("EXPENSE", result.transactions.get(0).getType());
        assertEquals("DEBIT", result.transactions.get(0).getDirection());
    }
}
