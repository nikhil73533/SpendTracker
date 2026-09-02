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
import com.example.spendtracker.domain.model.Transaction;
import com.example.spendtracker.ui.pdfimport.parser.BankStatementParserFactory;
import com.example.spendtracker.ui.pdfimport.parser.RawTransactionRow;
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.text.PDFTextStripper;

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
     * Parses a single PDF file Uri into SpendTracker transactions.
     */
    public FileImportResult parsePdf(Context context, Uri uri, List<Transaction> existingTransactions) {
        Context appContext = context.getApplicationContext();
        init(appContext);

        String fileName = getFileName(appContext, uri);
        FileImportResult result = new FileImportResult(fileName);

        Set<String> existingFingerprints = buildExistingFingerprints(existingTransactions);

        IncrementalPredictionService predictionService = null;
        try {
            predictionService = new IncrementalPredictionService(appContext);
        } catch (Exception e) {
            Log.w(TAG, "Could not initialize IncrementalPredictionService for category prediction", e);
        }

        try (InputStream is = appContext.getContentResolver().openInputStream(uri)) {
            if (is == null) {
                result.error = "Could not open file URI: " + uri;
                return result;
            }

            PDDocument document = PDDocument.load(is);
            if (document.isEncrypted()) {
                document.close();
                result.error = "PDF is password protected or encrypted";
                return result;
            }

            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            String fullText = stripper.getText(document);

            String headerText = fullText.length() > 1000 ? fullText.substring(0, 1000) : fullText;
            document.close();

            if (fullText.trim().isEmpty()) {
                result.error = "No readable text found in PDF (scanned or image PDF)";
                return result;
            }

            BankStatementParserFactory factory = new BankStatementParserFactory();
            BankStatementParserFactory.ParseOutput parseOutput = factory.parse(headerText, fullText);

            result.bankName = parseOutput.parser.getBankName();
            List<RawTransactionRow> rawRows = parseOutput.rows;

            result.totalFound = rawRows.size();
            if (rawRows.isEmpty()) {
                result.error = "No transaction table rows identified in PDF statement";
                return result;
            }

            for (RawTransactionRow rawRow : rawRows) {
                Transaction t = convertToTransaction(rawRow, result.bankName, predictionService);
                if (t == null) continue;

                String fp = duplicateDetector.generateFingerprint(
                        t.getBankName(), t.getAmount(), t.getDate(),
                        rawRow.getReferenceNo(), "", t.getReceiverName()
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

        } catch (Exception e) {
            Log.e(TAG, "Error processing PDF file: " + fileName, e);
            result.error = "Error parsing PDF: " + (e.getMessage() != null ? e.getMessage() : e.toString());
        }

        return result;
    }

    private Transaction convertToTransaction(RawTransactionRow rawRow, String bankName,
                                             IncrementalPredictionService predictionService) {
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

        // Check if narration indicates a transfer
        boolean isTransfer = isTransferTransaction(narration);
        if (isTransfer) {
            type = "TRANSFER";
        }

        String upiId = rawRow.getUpiId();
        if (upiId.isEmpty()) {
            upiId = extractUpiFromNarration(narration);
        }

        String merchant = extractMerchantFromNarration(narration, bankName);

        String category;
        if (isTransfer) {
            category = "Transfer";
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
