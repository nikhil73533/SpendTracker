package com.example.spendtracker.data.sms.extraction;

import com.example.spendtracker.data.sms.model.ExtractionResult;
import java.util.regex.Pattern;

/**
 * Extracts the transaction status: SUCCESS, FAILED, PENDING, or REVERSED.
 * Most SMS messages are about successful transactions, so SUCCESS is the default.
 */
public class TransactionStatusExtractor {

    private static final Pattern FAILED_PATTERN = Pattern.compile(
        "(?i)\\b(failed|declined|unsuccessful|rejected|not\\s+processed|could\\s+not)\\b");
    private static final Pattern PENDING_PATTERN = Pattern.compile(
        "(?i)\\b(pending|processing|in\\s+progress|awaiting)\\b");
    private static final Pattern REVERSED_PATTERN = Pattern.compile(
        "(?i)\\b(reversed|reversal|refund(?:ed)?|credited\\s+back|amount\\s+reversed)\\b");

    public ExtractionResult<String> extract(String normalizedMessage, String lowercaseMessage) {
        if (lowercaseMessage == null) return ExtractionResult.of("SUCCESS", 0.50);

        if (FAILED_PATTERN.matcher(lowercaseMessage).find())
            return ExtractionResult.of("FAILED", 0.90);
        if (REVERSED_PATTERN.matcher(lowercaseMessage).find())
            return ExtractionResult.of("REVERSED", 0.85);
        if (PENDING_PATTERN.matcher(lowercaseMessage).find())
            return ExtractionResult.of("PENDING", 0.80);

        return ExtractionResult.of("SUCCESS", 0.90);
    }
}
