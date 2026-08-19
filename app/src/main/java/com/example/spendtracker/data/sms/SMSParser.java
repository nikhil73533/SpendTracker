package com.example.spendtracker.data.sms;

import android.content.Context;
import android.util.Log;
import com.example.spendtracker.data.local.entity.RegexPatternEntity;
import com.example.spendtracker.domain.model.Transaction;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.inject.Singleton;
import dagger.hilt.android.qualifiers.ApplicationContext;

@Singleton
public class SMSParser {
    private static final String TAG = "SMSParser";
    private final Context context;

    @Inject
    public SMSParser(@ApplicationContext Context context) {
        this.context = context;
    }

    public Transaction parseSMS(String sender, String body, List<RegexPatternEntity> dbPatterns) {
        if (isOtpOrGeneric(body)) {
            Log.d(TAG, "Message identified as OTP or non-transactional. Skipping.");
            return null;
        }

        // 1. Try JSON configurations from assets
        Transaction transaction = parseFromJsonConfigs(body, sender);
        if (isValid(transaction)) return transaction;

        // 2. Try custom patterns from DB
        for (RegexPatternEntity entity : dbPatterns) {
            try {
                Pattern p = Pattern.compile(entity.pattern, Pattern.CASE_INSENSITIVE);
                Matcher m = p.matcher(body);
                if (m.find()) {
                    Transaction tx = createTransactionFromMatcher(body, sender, m, entity);
                    if (isValid(tx)) return tx;
                }
            } catch (Exception e) {
                Log.e(TAG, "Error parsing with DB pattern: " + entity.pattern, e);
            }
        }

        // 3. Fallback to robust generic parsing
        Transaction genericTx = parseGeneric(sender, body);
        if (isValid(genericTx)) return genericTx;

        return null;
    }

    private boolean isOtpOrGeneric(String body) {
        String lowerBody = body.toLowerCase();
        return lowerBody.contains("otp") || 
               lowerBody.contains("verification code") || 
               lowerBody.contains("one time password") ||
               (lowerBody.contains("login") && !lowerBody.contains("spent")) ||
               lowerBody.contains("your password is");
    }

    private boolean isValid(Transaction tx) {
        if (tx == null) return false;
        // Basic validation: amount must be positive, type must be set
        if (tx.getAmount() <= 0) return false;
        if (tx.getType() == null || tx.getType().isEmpty()) return false;
        // Prevent false positives that look like years or phone numbers but caught by weak regex
        if (tx.getAmount() > 10000000) return false; // Safety threshold for normal users
        return true;
    }

    private Transaction parseFromJsonConfigs(String body, String sender) {
        try {
            String[] files = context.getAssets().list("bank_configs");
            if (files == null) return null;

            for (String file : files) {
                String jsonStr = loadJSONFromAsset("bank_configs/" + file);
                if (jsonStr == null) continue;

                JSONObject bankConfig = new JSONObject(jsonStr);
                String bankName = bankConfig.getString("bankName");
                JSONArray patterns = bankConfig.getJSONArray("patterns");

                for (int i = 0; i < patterns.length(); i++) {
                    JSONObject pObj = patterns.getJSONObject(i);
                    String regex = pObj.getString("regex");
                    Pattern pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
                    Matcher matcher = pattern.matcher(body);

                    if (matcher.find()) {
                        double amount = 0;
                        try {
                            String amt = matcher.group(pObj.getInt("amountGroup"));
                            if (amt != null) amount = Double.parseDouble(amt.replaceAll(",", ""));
                        } catch (Exception e) {}

                        String receiver = "";
                        try {
                            receiver = matcher.group(pObj.getInt("receiverGroup"));
                        } catch (Exception e) {}

                        String upiId = "";
                        try {
                            upiId = matcher.group(pObj.getInt("upiGroup"));
                        } catch (Exception e) {}

                        String type = pObj.getString("type");
                        String sourceType = pObj.getString("sourceType");

                        return new Transaction(
                            0, amount, "Other", body, type, System.currentTimeMillis(),
                            bankName + " (" + sourceType + ")", sender,
                            upiId, receiver, bankName, sourceType
                        );
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in parseFromJsonConfigs", e);
        }
        return null;
    }

    private String loadJSONFromAsset(String fileName) {
        try (InputStream is = context.getAssets().open(fileName)) {
            int size = is.available();
            byte[] buffer = new byte[size];
            is.read(buffer);
            return new String(buffer, StandardCharsets.UTF_8);
        } catch (Exception ex) {
            return null;
        }
    }

    private Transaction createTransactionFromMatcher(String body, String sender, Matcher m, RegexPatternEntity entity) {
        double amount = 0;
        try {
            String amountStr = m.group(entity.amountGroup);
            if (amountStr != null) {
                amount = Double.parseDouble(amountStr.replaceAll(",", ""));
            }
        } catch (Exception e) {}

        String type = "EXPENSE";
        try {
            if (entity.typeGroup > 0) {
                String typeStr = m.group(entity.typeGroup);
                if (typeStr != null && (typeStr.toLowerCase().contains("credit") || typeStr.toLowerCase().contains("received"))) {
                    type = "INCOME";
                }
            }
        } catch (Exception e) {}

        return new Transaction(0, amount, "Uncategorized", body, type, System.currentTimeMillis(), "SMS", sender, "", "", "", "");
    }

    private Transaction parseGeneric(String sender, String body) {
        String lowerBody = body.toLowerCase();
        
        boolean isExpense = lowerBody.contains("debited") || lowerBody.contains("spent") || lowerBody.contains("paid") || lowerBody.contains("dr ") || lowerBody.contains("payment of");
        boolean isIncome = lowerBody.contains("credited") || lowerBody.contains("received") || lowerBody.contains("cr ") || lowerBody.contains("added to");

        if (!isExpense && !isIncome) return null;

        String type = isIncome ? "INCOME" : "EXPENSE";

        // Multi-level Amount Extraction
        double amount = extractAmount(body);
        if (amount <= 0) return null;

        String bank = extractBank(lowerBody);
        String sourceType = lowerBody.contains("credit card") ? "Credit Card" : "Account";
        String receiver = extractReceiver(body, bank);
        String upiId = extractUpiId(body);

        return new Transaction(0, amount, "Other", body, type, System.currentTimeMillis(), bank + " (" + sourceType + ")", sender, upiId, receiver, bank, sourceType);
    }

    private double extractAmount(String body) {
        // Try various common amount patterns
        String[] patterns = {
            "(?i)(?:INR|Rs\\.?|Rs|Dr|Cr|Paytm)\\s*([\\d,.]+)",
            "(?i)amount[ed]?\\s*(?:of\\s*)?([\\d,.]+)",
            "(?i)spent\\s*([\\d,.]+)"
        };
        for (String pStr : patterns) {
            Matcher m = Pattern.compile(pStr).matcher(body);
            if (m.find()) {
                try {
                    String val = m.group(1);
                    if (val != null) return Double.parseDouble(val.replaceAll(",", ""));
                } catch (Exception ignored) {}
            }
        }
        return 0;
    }

    private String extractBank(String lowerBody) {
        if (lowerBody.contains("icici")) return "ICICI Bank";
        if (lowerBody.contains("au bank") || lowerBody.contains("au a/c")) return "AU Bank";
        if (lowerBody.contains("hdfc")) return "HDFC Bank";
        if (lowerBody.contains("sbi")) return "SBI";
        if (lowerBody.contains("axis")) return "Axis Bank";
        return "SMS";
    }

    private String extractReceiver(String body, String bank) {
        if ("ICICI Bank".equals(bank)) {
            Pattern p = Pattern.compile("(?i)for\\s+(?!INR|Rs|Rs\\.)(?:UPI-\\d+-)?([^.]+?)(?=\\.|\\s+To|\\s+on)");
            Matcher m = p.matcher(body);
            if (m.find() && m.group(1) != null) return m.group(1).trim();
        } else if ("AU Bank".equals(bank)) {
            String[] parts = body.split("/");
            if (parts.length > 3) return parts[3].trim();
        }
        
        // Generic "at" or "to" extraction
        Pattern genericAt = Pattern.compile("(?i)(?:at|to|into)\\s+([^.]+?)(?=\\s+on|\\s+at|\\.|\\z)");
        Matcher m = genericAt.matcher(body);
        if (m.find() && m.group(1) != null) return m.group(1).trim();
        
        return "Uncategorized";
    }

    private String extractUpiId(String body) {
        Pattern p = Pattern.compile("([\\w.-]+@[\\w.-]+)");
        Matcher m = p.matcher(body);
        return m.find() ? m.group(1) : "";
    }
}
