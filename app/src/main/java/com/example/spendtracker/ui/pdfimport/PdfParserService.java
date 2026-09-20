package com.example.spendtracker.ui.pdfimport;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.util.Log;

import com.example.prediction.domain.model.IncrementalPredictionResult;
import com.example.prediction.domain.model.PredictionTransaction;
import com.example.prediction.domain.service.IncrementalPredictionService;
import com.example.spendtracker.data.sms.duplicate.DuplicateDetector;
import com.example.spendtracker.data.sms.extraction.CounterpartyExtractor;
import com.example.spendtracker.domain.model.Transaction;
import com.example.spendtracker.ui.pdfimport.parser.BankStatementParserFactory;
import com.example.spendtracker.ui.pdfimport.parser.RawTransactionRow;
import com.example.spendtracker.ui.pdfimport.ocr.MlKitPdfOcrEngine;
import com.example.spendtracker.ui.pdfimport.ocr.PositionedPdfTextStripper;
import com.example.spendtracker.ui.pdfimport.parser.StatementFields;
import com.example.spendtracker.ui.pdfimport.parser.StatementExtractionQuality;
import com.example.spendtracker.ui.pdfimport.ocr.OcrEngine;
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;
import com.tom_roush.pdfbox.pdmodel.PDDocument;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;

public class PdfParserService {

    private static final String TAG = "PdfParserService";
    private boolean isInitialized = false;

    private final DuplicateDetector duplicateDetector;
    private final OcrEngine ocrEngine;

    public PdfParserService() {
        this.duplicateDetector = new DuplicateDetector();
        this.ocrEngine = new MlKitPdfOcrEngine();
    }

    public synchronized void init(Context context) {
        if (!isInitialized) {
            try {
                PDFBoxResourceLoader.init(context.getApplicationContext());
                isInitialized = true;
            } catch (Exception e) {
                Log.e(TAG, "Failed to initialize PDFBoxResourceLoader", e);
            }
        }
    }

    public static class FileImportResult {
        public String fileName;
        public String bankName = "Bank";
        public int totalFound = 0;
        public int successfullyParsed = 0;
        public int duplicatesSkipped = 0;
        public List<Transaction> transactions = new ArrayList<>();
        public String error = null;
        public String warning = null;

        public FileImportResult(String fileName) {
            this.fileName = fileName != null ? fileName : "Statement.pdf";
        }
    }

    /**
     * Stage 1 & 2: Extracts text from PDF and normalizes extracted table rows into a clean JSON Schema.
     *
     * @param context Application context
     * @param uri     PDF document Uri
     * @return JSONObject containing bank metadata and a JSONArray of normalized transaction row key-value pairs
     */
    public JSONObject parsePdfToJson(Context context, Uri uri) throws Exception {
        Context appContext = context.getApplicationContext();
        init(appContext);

        String fileName = getFileName(appContext, uri);
        BankStatementParserFactory factory = new BankStatementParserFactory();
        List<String> embeddedPages = extractEmbeddedTextFromPdf(appContext, uri);
        String embeddedHeader = headerOf(String.join("\n", embeddedPages));
        java.util.Map<Integer, String> ocrPages = java.util.Collections.emptyMap();
        boolean ocrAttempted = false;
        String ocrWarning = null;
        for (String page : embeddedPages) {
            if (!isUsableStatementText(page) || needsOcrRetry(page, factory.parse(embeddedHeader, page).rows)) {
                ocrAttempted = true;
                break;
            }
        }
        if (ocrAttempted) {
            try {
                ocrPages = ocrEngine.recognizePdf(appContext, uri).getPageTexts();
            } catch (Exception ocrError) {
                Log.w(TAG, "OCR fallback failed", ocrError);
                ocrWarning = "OCR could not read all pages. Check the preview against your statement.";
            }
        }
        JSONObject rootJson = parsePageTexts(embeddedPages, ocrPages, ocrAttempted);
        rootJson.put("fileName", fileName);
        if (ocrWarning != null) rootJson.put("warning", ocrWarning);
        return rootJson;
    }

    /** Choose one extraction per page; a scanned page cannot replace correct digital rows elsewhere. */
    JSONObject parsePageTexts(List<String> embeddedPages, java.util.Map<Integer, String> ocrPages,
                             boolean ocrAttempted) throws JSONException {
        BankStatementParserFactory factory = new BankStatementParserFactory();
        String documentText = String.join("\n", embeddedPages) + "\n" + String.join("\n", ocrPages.values());
        String bankName = factory.getParser(headerOf(documentText), documentText).getBankName();
        JSONObject root = new JSONObject();
        JSONArray transactions = new JSONArray();
        boolean usedOcr = false, usedText = false;
        int found = 0, incompletePages = 0;
        int pageCount = Math.max(embeddedPages.size(), ocrPages.keySet().stream().mapToInt(Integer::intValue).max().orElse(0));
        for (int page = 1; page <= pageCount; page++) {
            String embedded = page <= embeddedPages.size() ? embeddedPages.get(page - 1) : "";
            String ocr = ocrPages.getOrDefault(page, "");
            List<RawTransactionRow> textRows = factory.parse(documentText, embedded).rows;
            List<RawTransactionRow> ocrRows = factory.parse(documentText, ocr).rows;
            boolean preferOcr = StatementExtractionQuality.prefer(ocr, ocrRows, embedded, textRows);
            List<RawTransactionRow> selected = preferOcr ? ocrRows : textRows;
            String selectedText = preferOcr ? ocr : embedded;
            if (needsOcrRetry(selectedText, selected)
                    || (selected.isEmpty() && (embedded.trim().isEmpty() || ocrAttempted))) incompletePages++;
            found += Math.max(selected.size(), Math.max(StatementFields.countDatedRows(embedded),
                    StatementFields.countDatedRows(ocr)));
            for (RawTransactionRow row : selected) {
                JSONObject item = mapRawRowToJson(row, bankName, preferOcr ? 0.75 : 1.0);
                if (item != null) {
                    item.put("pageNumber", page);
                    transactions.put(item);
                    if (preferOcr) usedOcr = true;
                    else usedText = true;
                }
            }
        }
        root.put("bankName", bankName);
        root.put("extractionMethod", usedOcr ? (usedText ? "MIXED" : "OCR") : "PDF_TEXT");
        root.put("ocrAttempted", ocrAttempted);
        root.put("totalFound", found);
        root.put("transactions", transactions);
        if (incompletePages > 0) root.put("warning",
                "Some rows may be missing, have unreadable descriptions, or disagree with the running balance. Compare the preview with the statement before importing.");
        if (documentText.trim().isEmpty()) root.put("error", "No readable text found in PDF. The file may be blank or an unsupported scan.");
        return root;
    }

    /**
     * Stage 3: Converts normalized JSON structure into SpendTracker domain Transaction objects with ML categorization and de-duplication.
     */
    public FileImportResult parseJsonToTransactions(JSONObject rootJson, List<Transaction> existingTransactions,
                                                    IncrementalPredictionService predictionService) {
        String fileName = rootJson.optString("fileName", "Statement.pdf");
        FileImportResult result = new FileImportResult(fileName);

        if (rootJson.has("error")) {
            result.error = rootJson.optString("error");
            return result;
        }

        result.bankName = rootJson.optString("bankName", "Bank");
        result.warning = rootJson.optString("warning", null);
        result.totalFound = rootJson.optInt("totalFound", 0);

        JSONArray jsonArray = rootJson.optJSONArray("transactions");
        if (jsonArray == null || jsonArray.length() == 0) {
            String extractionMethod = rootJson.optString("extractionMethod", "PDF_TEXT");
            boolean ocrAttempted = rootJson.optBoolean("ocrAttempted", false);
            result.error = "No transaction rows recognized after "
                    + (ocrAttempted && !"OCR".equals(extractionMethod)
                    ? "PDF text extraction and OCR"
                    : ("OCR".equals(extractionMethod) ? "OCR" : "PDF text extraction"))
                    + ". The statement layout may not expose date and amount columns clearly.";
            return result;
        }

        Set<String> existingFingerprints = buildExistingFingerprints(existingTransactions);
        Set<String> existingSourceIds = buildExistingSourceIds(existingTransactions);

        for (int i = 0; i < jsonArray.length(); i++) {
            JSONObject item = jsonArray.optJSONObject(i);
            if (item == null) continue;

            Transaction t = mapJsonToTransaction(item, result.bankName, predictionService);
            if (t == null) continue;

            if (!t.getSourceTransactionId().isEmpty() && existingSourceIds.contains(t.getSourceTransactionId())) {
                result.duplicatesSkipped++;
                continue;
            }

            String refNo = item.optString("referenceNo", "");
            String fp = duplicateDetector.generateFingerprint(
                    t.getBankName(), t.getAmount(), t.getDate(),
                    refNo, "", t.getReceiverName().isEmpty() ? t.getSender() : t.getReceiverName()
            );

            if (existingFingerprints.contains(fp)) {
                result.duplicatesSkipped++;
                continue;
            }

            existingFingerprints.add(fp);
            if (!t.getSourceTransactionId().isEmpty()) existingSourceIds.add(t.getSourceTransactionId());
            result.transactions.add(t);
            result.successfullyParsed++;
        }

        if (result.successfullyParsed == 0 && result.duplicatesSkipped > 0) {
            result.error = "All " + result.duplicatesSkipped + " transactions in file were already imported (duplicates).";
        } else if (result.successfullyParsed == 0) {
            result.error = "Could not parse valid transactions from file format.";
        }

        return result;
    }

    /**
     * Primary entry point: Parses a single PDF file Uri into SpendTracker transactions using the 3-stage pipeline.
     */
    public FileImportResult parsePdf(Context context, Uri uri, List<Transaction> existingTransactions) {
        Context appContext = context.getApplicationContext();

        IncrementalPredictionService predictionService = null;
        try {
            predictionService = com.example.spendtracker.util.CategoryPrediction.service(appContext);
        } catch (Exception e) {
            Log.w(TAG, "Could not initialize IncrementalPredictionService for category prediction", e);
        }

        try {
            JSONObject rootJson = parsePdfToJson(appContext, uri);
            return parseJsonToTransactions(rootJson, existingTransactions, predictionService);
        } catch (Exception e) {
            Log.e(TAG, "Error processing PDF file: " + uri, e);
            FileImportResult result = new FileImportResult(getFileName(appContext, uri));
            result.error = "Error parsing PDF: " + (e.getMessage() != null ? e.getMessage() : e.toString());
            return result;
        }
    }

    private List<String> extractEmbeddedTextFromPdf(Context context, Uri uri) throws Exception {
        List<String> pages = new ArrayList<>();
        try (InputStream is = context.getContentResolver().openInputStream(uri)) {
            if (is == null) throw new IllegalStateException("Unable to open selected PDF");

            try (PDDocument document = PDDocument.load(is)) {
                if (document.isEncrypted()) {
                    throw new IllegalStateException("PDF is password protected or encrypted");
                }
                PositionedPdfTextStripper stripper = new PositionedPdfTextStripper();
                stripper.getText(document);
                java.util.Map<Integer, String> positioned = stripper.getDocument().getPageTexts();
                for (int page = 1; page <= document.getNumberOfPages(); page++) {
                    String layout = positioned.getOrDefault(page, "");
                    pages.add(layout);
                }
            }
        }

        return pages;
    }

    static int validRowCount(List<RawTransactionRow> rows) {
        return StatementExtractionQuality.validRows(rows);
    }

    static boolean needsOcrRetry(String text, List<RawTransactionRow> rows) {
        return StatementExtractionQuality.needsRetry(text, rows);
    }

    private JSONObject mapRawRowToJson(RawTransactionRow rawRow, String bankName, double extractionConfidence) {
        if (rawRow == null) return null;

        double amount;
        String type;
        String direction;

        if (rawRow.getDebitAmount() != null && rawRow.getDebitAmount() > 0) {
            amount = rawRow.getDebitAmount();
            type = "EXPENSE";
            direction = "DEBIT";
        } else if (rawRow.getCreditAmount() != null && rawRow.getCreditAmount() > 0) {
            amount = rawRow.getCreditAmount();
            type = "INCOME";
            direction = "CREDIT";
        } else {
            return null;
        }

        String narration = rawRow.getNarration().trim();
        String time = extractTimeFromNarration(rawRow.getRawLine());
        if (time.isEmpty()) time = extractTimeFromNarration(narration);
        long timestamp = parseDateToMillis(rawRow.getDateStr(), time);
        if (timestamp <= 0) return null;

        String upiId = rawRow.getUpiId();
        if (upiId == null || upiId.isEmpty()) {
            upiId = extractUpiFromNarration(narration);
        }

        CounterpartyExtractor.Result counterparty = new CounterpartyExtractor().extract(narration);
        if (!counterparty.handle.isEmpty()) upiId = counterparty.handle;
        String merchant = counterparty.displayName();
        String referenceNo = rawRow.getReferenceNo();
        String sourceTransactionId = createSourceTransactionId(bankName, referenceNo, rawRow.getDateStr(),
                time, direction, amount, narration);

        try {
            JSONObject json = new JSONObject();
            json.put("bankName", bankName);
            json.put("date", rawRow.getDateStr());
            json.put("time", time.isEmpty() ? JSONObject.NULL : time);
            json.put("dateMillis", timestamp);
            json.put("timestampPrecision", time.isEmpty() ? "DATE_ONLY" : "DATE_TIME");
            json.put("narration", narration);
            json.put("referenceNo", referenceNo);
            json.put("sourceTransactionId", sourceTransactionId);
            json.put("upiId", upiId);
            json.put("merchant", merchant);
            json.put("counterpartySource", counterparty.source);
            json.put("counterpartyConfidence", counterparty.confidence);
            json.put("counterpartyIsHandle", counterparty.name.isEmpty() && !counterparty.handle.isEmpty());
            json.put("counterpartyName", counterparty.name.isEmpty() ? JSONObject.NULL : counterparty.name);
            json.put("senderName", "CREDIT".equals(direction) && !counterparty.name.isEmpty() ? counterparty.name : JSONObject.NULL);
            json.put("receiverName", "DEBIT".equals(direction) && !counterparty.name.isEmpty() ? counterparty.name : JSONObject.NULL);
            json.put("type", type);
            json.put("direction", direction);
            json.put("amount", amount);
            json.put("currency", "INR");
            json.put("extractionConfidence", extractionConfidence);
            if (rawRow.getDebitAmount() != null) json.put("debitAmount", rawRow.getDebitAmount());
            if (rawRow.getCreditAmount() != null) json.put("creditAmount", rawRow.getCreditAmount());
            if (rawRow.getBalance() != null) json.put("balance", rawRow.getBalance());
            json.put("rawLine", rawRow.getRawLine());
            return json;
        } catch (JSONException e) {
            return null;
        }
    }

    private Transaction mapJsonToTransaction(JSONObject json, String bankName,
                                            IncrementalPredictionService predictionService) {
        if (json == null) return null;

        double amount = json.optDouble("amount", 0.0);
        if (amount <= 0.0) return null;

        String type = json.optString("type", "EXPENSE");
        long timestamp = json.optLong("dateMillis", 0L);
        if (timestamp <= 0L) return null;
        String narration = json.optString("narration", "");
        String upiId = json.optString("upiId", "");
        String merchant = json.optString("merchant", "");

        double confidence = 0.0;
        String category;
        if ("TRANSFER".equals(type)) {
            category = "Transfer";
            confidence = 1.0;
        } else if (predictionService != null) {
            try {
                PredictionTransaction pt = new PredictionTransaction(
                        merchant, upiId, amount, type, timestamp, narration
                );
                IncrementalPredictionResult pred = predictionService.predict(pt);
                confidence = pred == null ? 0.0 : pred.getConfidence();
                category = (pred != null && pred.getCategory() != null && !pred.needsUserConfirmation())
                        ? pred.getCategory() : "Uncategorized";
            } catch (Exception e) {
                category = "Uncategorized";
            }
        } else {
            category = "Uncategorized";
        }

        Transaction t = new Transaction();
        t.setAmount(amount);
        t.setType(type);
        t.setCategory(category);
        t.setConfidenceScore(confidence);
        t.setDate(timestamp);
        t.setDescription(narration);
        t.setBankName(bankName);
        t.setSource(bankName + " (Account)");
        t.setSourceType("Account");
        t.setUpiId(upiId);
        t.setReceiverName(type.equals("INCOME") ? "" : merchant);
        t.setSender(type.equals("INCOME") ? merchant : "");
        t.setDirection(json.optString("direction", type.equals("INCOME") ? "CREDIT" : "DEBIT"));
        t.setReferenceNumber(json.optString("referenceNo", ""));
        t.setSourceTransactionId(json.optString("sourceTransactionId", ""));
        t.setTimestampPrecision(json.optString("timestampPrecision", "DATE_TIME"));
        t.setStatus("ACTIVE");

        return t;
    }

    private String extractMerchantFromNarration(String narration, String bankName) {
        return new CounterpartyExtractor().extract(narration).displayName();
    }

    private String extractUpiFromNarration(String narration) {
        Matcher matcher = Pattern.compile("([a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+)").matcher(narration);
        if (matcher.find()) return matcher.group(1);
        return "";
    }

    private long parseDateToMillis(String dateStr, String time) {
        String normalized = StatementFields.date(dateStr);
        if (normalized == null) return 0L;
        java.time.LocalDate date = java.time.LocalDate.parse(normalized);
        java.time.ZoneId zone = java.time.ZoneId.systemDefault();
        if (time == null || time.trim().isEmpty()) return date.atStartOfDay(zone).toInstant().toEpochMilli();
        for (String pattern : new String[]{"h:mm:ss a", "h:mm a", "H:mm:ss", "H:mm"}) {
            try {
                java.time.format.DateTimeFormatter formatter = new java.time.format.DateTimeFormatterBuilder()
                        .parseCaseInsensitive().appendPattern(pattern).toFormatter(Locale.ENGLISH);
                String normalizedTime = time.trim().replaceAll("(?i)(\\d)([AP]M)$", "$1 $2");
                java.time.LocalTime parsed = java.time.LocalTime.parse(normalizedTime, formatter);
                return date.atTime(parsed).atZone(zone).toInstant().toEpochMilli();
            } catch (java.time.DateTimeException ignored) {
            }
        }
        return 0L;
    }

    private Set<String> buildExistingFingerprints(List<Transaction> existingTransactions) {
        Set<String> fps = new HashSet<>();
        if (existingTransactions == null) return fps;

        for (Transaction t : existingTransactions) {
            String fp = duplicateDetector.generateFingerprint(
                    t.getBankName(), t.getAmount(), t.getDate(),
                    t.getReferenceNumber(), "", t.getReceiverName().isEmpty() ? t.getSender() : t.getReceiverName()
            );
            fps.add(fp);
        }
        return fps;
    }

    private Set<String> buildExistingSourceIds(List<Transaction> transactions) {
        Set<String> sourceIds = new HashSet<>();
        if (transactions == null) return sourceIds;
        for (Transaction transaction : transactions) {
            if (!transaction.getSourceTransactionId().isEmpty()) sourceIds.add(transaction.getSourceTransactionId());
        }
        return sourceIds;
    }

    private boolean isUsableStatementText(String text) {
        if (text == null || text.trim().length() < 80) return false;
        Matcher dates = Pattern.compile(
                "(?i)\\b(?:\\d{4}[-/.]\\d{1,2}[-/.]\\d{1,2}"
                        + "|\\d{1,2}[-/.](?:\\d{1,2}|[A-Za-z]{3,9})[-/.]\\d{2,4}"
                        + "|\\d{1,2}(?:\\s+|-)[A-Za-z]{3,9}(?:\\s+|-)\\d{2,4})\\b")
                .matcher(text);
        Matcher amounts = Pattern.compile(
                "(?i)(?:₹|INR\\s*|RS\\.?\\s*)?(?:\\d{1,3}(?:,\\d{2,3})+|\\d+)[.]\\s*\\d{1,2}")
                .matcher(text);
        return dates.find() && amounts.find();
    }

    private String headerOf(String text) {
        if (text == null) return "";
        return text.length() > 1000 ? text.substring(0, 1000) : text;
    }

    private String extractTimeFromNarration(String narration) {
        if (narration == null) return "";
        Matcher matcher = Pattern.compile("\\b(?:[01]?\\d|2[0-3]):[0-5]\\d(?::[0-5]\\d)?(?:\\s?(?:AM|PM))?\\b", Pattern.CASE_INSENSITIVE).matcher(narration);
        return matcher.find() ? matcher.group().trim() : "";
    }

    private String createSourceTransactionId(String bankName, String referenceNo, String date, String time,
                                             String direction, double amount, String narration) {
        if (referenceNo != null && !referenceNo.trim().isEmpty()) {
            return (bankName + ":" + referenceNo.trim()).toUpperCase(Locale.ENGLISH);
        }
        String input = String.format(Locale.ROOT, "%s|%s|%s|%s|%.2f|%s",
                bankName == null ? "" : bankName.trim().toUpperCase(Locale.ENGLISH),
                date == null ? "" : date.trim(), time == null ? "" : time.trim(),
                direction == null ? "" : direction, amount,
                narration == null ? "" : narration.replaceAll("\\s+", " ").trim().toUpperCase(Locale.ENGLISH));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder("SYN:");
            for (byte b : digest) hex.append(String.format(Locale.ROOT, "%02x", b));
            return hex.toString();
        } catch (Exception ignored) {
            return "SYN:" + Integer.toHexString(input.hashCode());
        }
    }

    private String getFileName(Context context, Uri uri) {
        String result = null;
        if ("content".equals(uri.getScheme())) {
            try (Cursor cursor = context.getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (idx >= 0) {
                        result = cursor.getString(idx);
                    }
                }
            } catch (Exception ignored) {
            }
        }
        if (result == null) {
            result = uri.getPath();
            if (result != null) {
                int cut = result.lastIndexOf('/');
                if (cut != -1) {
                    result = result.substring(cut + 1);
                }
            }
        }
        return result != null ? result : "Bank_Statement.pdf";
    }
}
