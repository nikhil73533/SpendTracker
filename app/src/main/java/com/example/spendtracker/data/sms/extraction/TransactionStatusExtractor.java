package com.example.spendtracker.data.sms.extraction;

import com.example.spendtracker.data.sms.model.ExtractionResult;
import java.util.regex.Pattern;

public class TransactionStatusExtractor {
    private static final Pattern FAILED = Pattern.compile("(?i)\\b(failed|declined|unsuccessful|rejected|could\\s+not|not\\s+(?:been\\s+)?(?:processed|debited|credited|refunded|reversed|received))\\b");
    private static final Pattern PENDING = Pattern.compile("(?i)\\b(pending|processing|in\\s+progress|awaiting)\\b");
    private static final Pattern REVERSED = Pattern.compile(
            "(?i)\\b(?:refunded|reversed|credited\\s+back)\\b|\\brefund\\b.{0,60}\\bcredited\\b|\\bcredited\\b.{0,40}\\bas\\s+(?:a\\s+)?refund\\b");
    private static final Pattern NEGATED = Pattern.compile("(?i)\\b(?:not|no)\\s+(?:been\\s+)?(?:refunded|reversed|credited)");

    public ExtractionResult<String> extract(String normalized, String lowercase) {
        String text = TransactionText.posted(normalized);
        if (REVERSED.matcher(text).find() && !NEGATED.matcher(text).find())
            return ExtractionResult.of("REVERSED", .95);
        if (FAILED.matcher(text).find()) return ExtractionResult.of("FAILED", .90);
        if (PENDING.matcher(text).find()) return ExtractionResult.of("PENDING", .85);
        return ExtractionResult.of("SUCCESS", .80);
    }
}
