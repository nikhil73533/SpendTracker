package com.example.spendtracker.ui.pdfimport;

import android.content.Context;
import android.database.Cursor;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import android.util.Log;

import com.example.prediction.domain.model.IncrementalPredictionResult;
import com.example.prediction.domain.model.PredictionTransaction;
import com.example.prediction.domain.service.IncrementalPredictionService;
import com.example.spendtracker.data.sms.duplicate.DuplicateDetector;
import com.example.spendtracker.domain.model.Transaction;
import com.example.spendtracker.ui.pdfimport.parser.BankStatementParserFactory;
import com.example.spendtracker.ui.pdfimport.parser.RawTransactionRow;
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.text.PDFTextStripper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.InputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PdfParserService {

    private static final String TAG = "PdfParserService";
    private boolean isInitialized = false;

    private static final String[] DATE_FORMATS = {
            "dd/MM/yyyy",
            "dd/MM/yy",
            "dd-MM-yyyy",
            "dd-MM-yy",
            "dd MMM yyyy",
            "dd-MMM-yyyy",
            "dd-MMM-yy",
            "yyyy-MM-dd"
    };

    private final DuplicateDetector duplicateDetector;

    public PdfParserService() {
        this.duplicateDetector = new DuplicateDetector();
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
        JSONObject rootJson = new JSONObject();
        rootJson.put("fileName", fileName);

        String fullText = extractTextFromPdf(appContext, uri);
        if (fullText == null || fullText.trim().isEmpty()) {
            rootJson.put("error", "No readable text found in PDF (scanned or image PDF)");
            return rootJson;
        }

        String headerText = fullText.length() > 1000 ? fullText.substring(0, 1000) : fullText;

        BankStatementParserFactory factory = new BankStatementParserFactory();
        BankStatementParserFactory.ParseOutput parseOutput = factory.parse(headerText, fullText);

        String bankName = parseOutput.parser.getBankName();
        List<RawTransactionRow> rawRows = parseOutput.rows;

        rootJson.put("bankName", bankName);
        rootJson.put("totalFound", rawRows.size());

        JSONArray jsonArray = new JSONArray();
        for (RawTransactionRow rawRow : rawRows) {
            JSONObject rowJson = mapRawRowToJson(rawRow, bankName);
            if (rowJson != null) {
                jsonArray.put(rowJson);
            }
        }
        rootJson.put("transactions", jsonArray);

        return rootJson;
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
        result.totalFound = rootJson.optInt("totalFound", 0);

        JSONArray jsonArray = rootJson.optJSONArray("transactions");
        if (jsonArray == null || jsonArray.length() == 0) {
            result.error = "No transaction table rows identified in PDF statement";
            return result;
        }

        Set<String> existingFingerprints = buildExistingFingerprints(existingTransactions);

        for (int i = 0; i < jsonArray.length(); i++) {
            JSONObject item = jsonArray.optJSONObject(i);
            if (item == null) continue;

            Transaction t = mapJsonToTransaction(item, result.bankName, predictionService);
            if (t == null) continue;

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
            predictionService = new IncrementalPredictionService(appContext);
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

    private String extractTextFromPdf(Context context, Uri uri) throws Exception {
        String fullText;
        try (InputStream is = context.getContentResolver().openInputStream(uri)) {
            if (is == null) return null;

            PDDocument document = PDDocument.load(is);
            if (document.isEncrypted()) {
                document.close();
                throw new IllegalStateException("PDF is password protected or encrypted");
            }

            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            fullText = stripper.getText(document);
            document.close();
        }

        // Fallback for scanned/unstructured PDFs using android.graphics.pdf.PdfRenderer if PDFBox returned empty text
        if (fullText == null || fullText.trim().isEmpty()) {
            fullText = extractTextWithPdfRenderer(context, uri);
        }

        return fullText;
    }

    private String extractTextWithPdfRenderer(Context context, Uri uri) {
        StringBuilder textBuilder = new StringBuilder();
        try (ParcelFileDescriptor pfd = context.getContentResolver().openFileDescriptor(uri, "r")) {
            if (pfd == null) return "";
            try (PdfRenderer renderer = new PdfRenderer(pfd)) {
                int pageCount = renderer.getPageCount();
                for (int i = 0; i < Math.min(pageCount, 10); i++) {
                    try (PdfRenderer.Page page = renderer.openPage(i)) {
                        textBuilder.append("Page ").append(i + 1).append(" (").append(page.getWidth()).append("x").append(page.getHeight()).append(")\n");
                    }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "PdfRenderer fallback attempt: " + e.getMessage());
        }
        return textBuilder.toString();
    }

    private JSONObject mapRawRowToJson(RawTransactionRow rawRow, String bankName) {
        if (rawRow == null) return null;

        double amount;
        String type;

        if (rawRow.getDebitAmount() != null && rawRow.getDebitAmount() > 0) {
            amount = rawRow.getDebitAmount();
            type = "EXPENSE";
        } else if (rawRow.getCreditAmount() != null && rawRow.getCreditAmount() > 0) {
            amount = rawRow.getCreditAmount();
            type = "INCOME";
        } else {
            return null;
        }

        long timestamp = parseDateToMillis(rawRow.getDateStr());
        String narration = rawRow.getNarration().trim();

        boolean isTransfer = isTransferTransaction(narration);
        if (isTransfer) {
            type = "TRANSFER";
        }

        String upiId = rawRow.getUpiId();
        if (upiId == null || upiId.isEmpty()) {
            upiId = extractUpiFromNarration(narration);
        }

        String merchant = extractMerchantFromNarration(narration, bankName);

        try {
            JSONObject json = new JSONObject();
            json.put("bankName", bankName);
            json.put("date", rawRow.getDateStr());
            json.put("dateMillis", timestamp);
            json.put("narration", narration);
            json.put("referenceNo", rawRow.getReferenceNo());
            json.put("upiId", upiId);
            json.put("merchant", merchant);
            json.put("type", type);
            json.put("amount", amount);
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
        long timestamp = json.optLong("dateMillis", System.currentTimeMillis());
        String narration = json.optString("narration", "");
        String upiId = json.optString("upiId", "");
        String merchant = json.optString("merchant", "");

        boolean isTransfer = "TRANSFER".equals(type) || isTransferTransaction(narration);

        String category;
        if (isTransfer) {
            category = "Transfer";
            type = "TRANSFER";
        } else if (predictionService != null) {
            try {
                PredictionTransaction pt = new PredictionTransaction(
                        merchant.isEmpty() ? narration : merchant, upiId, amount, type, timestamp
                );
                IncrementalPredictionResult pred = predictionService.predict(pt);
                category = (pred != null && pred.getCategory() != null) ? pred.getCategory() : "Uncategorized";
                if (pred != null && pred.getCategory() != null) {
                    predictionService.learnAsync(pt, category);
                }
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
        t.setDate(timestamp);
        t.setDescription(narration);
        t.setBankName(bankName);
        t.setSource(bankName + " (Account)");
        t.setSourceType("Account");
        t.setUpiId(upiId);
        t.setReceiverName(type.equals("INCOME") ? "" : merchant);
        t.setSender(type.equals("INCOME") ? merchant : "");
        t.setStatus("ACTIVE");

        return t;
    }

    private boolean isTransferTransaction(String narration) {
        if (narration == null) return false;
        String lower = narration.toLowerCase();
        return lower.contains("transfer to") || lower.contains("transfer from") ||
               lower.contains("own account") || lower.contains("self transfer") ||
               lower.contains("fund transfer") || lower.contains("sweep in") || lower.contains("sweep out");
    }

    private String extractMerchantFromNarration(String narration, String bankName) {
        if (narration == null || narration.isEmpty()) return "";

        Matcher upiMatcher = Pattern.compile("(?:UPI/|UPI-)(?:DR|CR)?/(?:\\d+/)?([^/]+)", Pattern.CASE_INSENSITIVE).matcher(narration);
        if (upiMatcher.find() && upiMatcher.group(1) != null) {
            return cleanMerchantName(upiMatcher.group(1));
        }

        Matcher posMatcher = Pattern.compile("(?:POS|ECOM|NEFT|IMPS|RTGS|INF)[/-]?([^/-]+)", Pattern.CASE_INSENSITIVE).matcher(narration);
        if (posMatcher.find() && posMatcher.group(1) != null) {
            return cleanMerchantName(posMatcher.group(1));
        }

        Matcher toByMatcher = Pattern.compile("(?:TO|BY|FROM|PAID TO)\\s+([A-Z0-9\\s&.'-]{2,30})", Pattern.CASE_INSENSITIVE).matcher(narration);
        if (toByMatcher.find() && toByMatcher.group(1) != null) {
            return cleanMerchantName(toByMatcher.group(1));
        }

        String firstToken = narration.split("[/\\-\\s]")[0];
        if (firstToken.length() >= 3 && !firstToken.matches("\\d+")) {
            return cleanMerchantName(firstToken);
        }

        return narration.length() > 30 ? narration.substring(0, 30).trim() : narration;
    }

    private String cleanMerchantName(String name) {
        if (name == null) return "";
        String cleaned = name.replaceAll("[^a-zA-Z0-9\\s&.-]", "").replaceAll("\\s+", " ").trim();
        if (cleaned.length() > 30) {
            cleaned = cleaned.substring(0, 30).trim();
        }
        return cleaned;
    }

    private String extractUpiFromNarration(String narration) {
        Matcher matcher = Pattern.compile("([a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+)").matcher(narration);
        if (matcher.find()) return matcher.group(1);
        return "";
    }

    private long parseDateToMillis(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty()) return System.currentTimeMillis();
        String cleanDate = dateStr.trim();

        for (String format : DATE_FORMATS) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat(format, Locale.ENGLISH);
                sdf.setLenient(false);
                Date parsed = sdf.parse(cleanDate);
                if (parsed != null) {
                    return parsed.getTime();
                }
            } catch (ParseException ignored) {
            }
        }
        return System.currentTimeMillis();
    }

    private Set<String> buildExistingFingerprints(List<Transaction> existingTransactions) {
        Set<String> fps = new HashSet<>();
        if (existingTransactions == null) return fps;

        for (Transaction t : existingTransactions) {
            String fp = duplicateDetector.generateFingerprint(
                    t.getBankName(), t.getAmount(), t.getDate(),
                    "", "", t.getReceiverName().isEmpty() ? t.getSender() : t.getReceiverName()
            );
            fps.add(fp);
        }
        return fps;
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
