package com.example.spendtracker.ui.pdfimport;

import android.content.Context;
import android.net.Uri;
import android.util.Log;
import com.example.spendtracker.domain.model.Transaction;
import com.tom_roush.pdfbox.pdmodel.PDDocument;
import com.tom_roush.pdfbox.text.PDFTextStripper;
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader;
import java.io.InputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PdfParserService {

    private static final String TAG = "PdfParserService";
    private boolean isInitialized = false;

    // A basic pattern for "DD/MM/YYYY description amount"
    // e.g., "12/08/2023 Amazon Purchase 1500.50"
    private static final Pattern BASIC_PATTERN = Pattern.compile("^(\\d{2}/\\d{2}/\\d{4})\\s+(.+?)\\s+([\\d,]+\\.\\d{2})$", Pattern.MULTILINE);
    private static final SimpleDateFormat SDF = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());

    public void init(Context context) {
        if (!isInitialized) {
            PDFBoxResourceLoader.init(context);
            isInitialized = true;
        }
    }

    public static class ImportResult {
        public int totalFound = 0;
        public int successfullyParsed = 0;
        public List<Transaction> transactions = new ArrayList<>();
        public String error = null;
    }

    public ImportResult parsePdf(Context context, Uri uri) {
        init(context.getApplicationContext());
        ImportResult result = new ImportResult();
        
        try (InputStream is = context.getContentResolver().openInputStream(uri)) {
            if (is == null) {
                result.error = "Could not open file";
                return result;
            }

            PDDocument document = PDDocument.load(is);
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document);
            document.close();

            Matcher matcher = BASIC_PATTERN.matcher(text);
            while (matcher.find()) {
                result.totalFound++;
                try {
                    String dateStr = matcher.group(1);
                    String description = matcher.group(2).trim();
                    String amountStr = matcher.group(3).replace(",", "");

                    Date date = SDF.parse(dateStr);
                    double amount = Double.parseDouble(amountStr);

                    Transaction t = new Transaction();
                    t.setDate(date != null ? date.getTime() : System.currentTimeMillis());
                    t.setAmount(amount);
                    t.setReceiverName(description);
                    t.setCategory("Uncategorized");
                    t.setType("EXPENSE"); // Default to expense for this simple parser
                    t.setSource("PDF Import");
                    t.setStatus("ACTIVE");
                    
                    result.transactions.add(t);
                    result.successfullyParsed++;
                } catch (ParseException | NumberFormatException e) {
                    Log.w(TAG, "Failed to parse line: " + matcher.group(0), e);
                }
            }

            if (result.totalFound == 0) {
                result.error = "No transactions found matching the expected format.";
            }

        } catch (Exception e) {
            Log.e(TAG, "Error parsing PDF", e);
            result.error = "Error parsing PDF: " + e.getMessage();
        }

        return result;
    }
}
