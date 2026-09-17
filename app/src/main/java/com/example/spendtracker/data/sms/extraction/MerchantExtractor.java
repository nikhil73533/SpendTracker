package com.example.spendtracker.data.sms.extraction;

import com.example.spendtracker.data.sms.model.ExtractionResult;
import java.util.regex.*;

/** Structured narration first, then bounded party clauses (never an account/product label). */
public class MerchantExtractor {
    private static final Pattern[] NAMES = {
        Pattern.compile("(?i)\\bfor\\s+UPI-\\d+-([^.;]+)"),
        Pattern.compile("(?i)(?:^|;)\\s*([^;]+?)\\s+credited\\b"),
        Pattern.compile("(?i)\\b(?:paid\\s+to|trf\\s+to|to|from|by|at|fvg:?|towards)\\s+(.+?)(?=\\s+(?:on|via|using|upi|ref(?:no)?|rrn|utr|avl|from|to|by|at|not\\s+you)\\b|[;]|$)"),
        Pattern.compile("(?i)\\bon\\s+\\d{1,2}-[A-Z]{3}-\\d{2,4}\\s+on\\s+(.+?)(?=\\.\\s|$)")
    };
    private static final Pattern ACCOUNT = Pattern.compile(
            "(?i)\\b(?:a/c|acct|account|card|credited|debited|your|INR|Rs)\\b|^\\d");
    public ExtractionResult<String> extract(String message, String bankName) {
        String text = TransactionText.core(message);
        // Only structured narrations are eligible here, not the entire SMS as a plain name.
        if (Pattern.compile("(?i)\\b(?:UPI|IMPS|NEFT|RTGS)[/*-]").matcher(text).find()) {
            CounterpartyExtractor.Result structured = new CounterpartyExtractor().extract(text);
            if (!structured.name.isEmpty()) return ExtractionResult.of(structured.name.replaceAll("[.]+$", ""), structured.confidence);
        }
        for (Pattern pattern : NAMES) {
            Matcher m = pattern.matcher(text);
            while (m.find()) {
                String name = CounterpartyExtractor.clean(m.group(1)).replaceAll("[.]+$", "");
                if (name.length() < 2 || name.length() > 120 || ACCOUNT.matcher(name).find() || name.contains("@")) continue;
                if (!Pattern.compile("\\p{L}").matcher(name).find()) continue;
                if (bankName != null && name.replaceAll("(?i)\\s+bank(?:\\s+ltd\\.?)?$", "")
                        .equalsIgnoreCase(bankName.replaceAll("(?i)\\s+bank$", ""))) continue;
                return ExtractionResult.of(name, .85);
            }
        }
        return ExtractionResult.empty();
    }
}
