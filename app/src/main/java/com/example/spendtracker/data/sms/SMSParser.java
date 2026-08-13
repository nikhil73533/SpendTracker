package com.example.spendtracker.data.sms;

import android.content.Context;
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

    private final Context context;

    @Inject
    public SMSParser(@ApplicationContext Context context) {
        this.context = context;
    }

    public Transaction parseSMS(String sender, String body, List<RegexPatternEntity> dbPatterns) {
        // 1. Try JSON configurations from assets
        Transaction transaction = parseFromJsonConfigs(body, sender);
        if (transaction != null) return transaction;

        // 2. Try custom patterns from DB
        for (RegexPatternEntity entity : dbPatterns) {
            Pattern p = Pattern.compile(entity.pattern, Pattern.CASE_INSENSITIVE);
            Matcher m = p.matcher(body);
            if (m.find()) {
                return createTransactionFromMatcher(body, sender, m, entity);
            }
        }

        // 3. Fallback to robust generic parsing
        return parseGeneric(sender, body);
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
            e.printStackTrace();
        }
        return null;
    }

    private String loadJSONFromAsset(String fileName) {
        String json;
        try {
            InputStream is = context.getAssets().open(fileName);
            int size = is.available();
            byte[] buffer = new byte[size];
            is.read(buffer);
            is.close();
            json = new String(buffer, StandardCharsets.UTF_8);
        } catch (Exception ex) {
            ex.printStackTrace();
            return null;
        }
        return json;
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
        
        boolean isExpense = lowerBody.contains("debited") || lowerBody.contains("spent") || lowerBody.contains("paid") || lowerBody.contains("dr ");
        boolean isIncome = lowerBody.contains("credited") || lowerBody.contains("received") || lowerBody.contains("cr ");

        if (!isExpense && !isIncome) return null;

        String type = isIncome ? "INCOME" : "EXPENSE";

        Pattern amountPattern = Pattern.compile("(?i)(?:INR|Rs\\.?|Rs|Dr|Cr)\\s*([\\d,.]+)");
        Matcher amountMatcher = amountPattern.matcher(body);
        double amount = 0;
        if (amountMatcher.find()) {
            String val = amountMatcher.group(1);
            if (val != null) {
                try {
                    amount = Double.parseDouble(val.replaceAll(",", ""));
                } catch (NumberFormatException e) {}
            }
        }

        String bank = "SMS";
        if (lowerBody.contains("icici")) bank = "ICICI Bank";
        else if (lowerBody.contains("au bank") || lowerBody.contains("au a/c") || lowerBody.contains(" au ")) bank = "AU Bank";

        String sourceType = "Account";
        if (lowerBody.contains("credit card")) {
            sourceType = "Credit Card";
        }
        
        String receiver = "Uncategorized";
        String upiId = "";
        
        if (lowerBody.contains("icici")) {
            Pattern iciciReceiver = Pattern.compile("(?i)for\\s+(?!INR|Rs|Rs\\.)(?:UPI-\\d+-)?([^.]+?)(?=\\.|\\s+To|\\s+on)");
            Matcher m = iciciReceiver.matcher(body);
            if (m.find() && m.group(1) != null) {
                receiver = m.group(1).trim();
            }
        } else if (lowerBody.contains("au ")) {
            String[] parts = body.split("/");
            if (parts.length > 3) {
                receiver = parts[3].trim();
            }
        }

        return new Transaction(0, amount, "Other", body, type, System.currentTimeMillis(), bank + " (" + sourceType + ")", sender, upiId, receiver, bank, sourceType);
    }
}
