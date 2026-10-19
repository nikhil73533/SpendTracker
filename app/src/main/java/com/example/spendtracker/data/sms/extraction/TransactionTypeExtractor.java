package com.example.spendtracker.data.sms.extraction;

import com.example.spendtracker.data.sms.model.ExtractionResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Direction belongs to the account action, not card-product nouns or a transfer rail. */
public class TransactionTypeExtractor {
    private static final Pattern OWN_TRANSFER = Pattern.compile(
            "(?i)\\b(?:self[- ]transfer|transfer(?:red)?\\s+between\\s+(?:your|my|own)\\s+accounts)\\b"
            + "|\\bfrom\\s+(?:your|my|own)\\b.{0,70}\\bto\\s+(?:your|my|own)\\b");
    private static final Pattern DIRECTION = Pattern.compile(
            "(?i)\\b(debited|deducted|withdrawn|withdrawal|spent|paid|charged|sent|used|dr|"
            + "credited|deposited|received|refunded|reversed|cr)\\b");
    private static final Pattern INCOME = Pattern.compile("(?i)credited|deposited|received|refunded|reversed|cr");

    public String extractDirection(String message) {
        String text = TransactionText.posted(message);
        Matcher action = DIRECTION.matcher(text);
        return action.find() && INCOME.matcher(action.group(1)).matches() ? "CREDIT" : "DEBIT";
    }

    public ExtractionResult<String> extract(String normalized, String lowercase) {
        String text = TransactionText.posted(normalized);
        if (text.isEmpty()) return ExtractionResult.empty();
        if (OWN_TRANSFER.matcher(text).find()) return ExtractionResult.of("TRANSFER", .95);
        if ("REVERSED".equals(new TransactionStatusExtractor().extract(text, text).getValue()))
            return ExtractionResult.of("INCOME", .95);
        Matcher action = DIRECTION.matcher(text);
        if (action.find()) return ExtractionResult.of(INCOME.matcher(action.group(1)).matches() ? "INCOME" : "EXPENSE", .95);
        if (text.matches("(?is).*\\b(?:transferred|fund\\s+transfer)\\b.*\\bfrom\\s+(?:(?:your|my)\\s+)?(?:a/c|acct|account)\\b.*"))
            return ExtractionResult.of("EXPENSE", .85);
        return ExtractionResult.empty();
    }
}
