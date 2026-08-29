package com.example.spendtracker.data.sms.extraction;

import com.example.spendtracker.data.sms.model.ExtractionResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts UPI VPA and numeric transaction reference IDs.
 * Numeric reference IDs take priority over VPAs for duplicate detection.
 */
public class UpiExtractor {

    private static final Pattern NUMERIC_REF = Pattern.compile(
        "(?i)(?:UPI[:/]?\\s*(?:Ref\\.?\\s*(?:No\\.?)?|No\\.?|Ref\\s+No\\.?)?|" +
        "Ref\\s+No\\.?|Txn\\s*(?:Id|No|Ref)\\.?)[:\\s]?\\s*([0-9]{6,20})");

    private static final Pattern VPA = Pattern.compile(
        "([a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+)");

    private static final Pattern EMAIL_EXCLUDE = Pattern.compile(
        "(?i)@(gmail|yahoo|hotmail|outlook|email|mail|live)\\.com");

    public ExtractionResult<String> extractReferenceId(String msg) {
        if (msg == null) return ExtractionResult.empty();
        Matcher m = NUMERIC_REF.matcher(msg);
        if (m.find() && m.group(1) != null) return ExtractionResult.of(m.group(1), 0.95);
        return ExtractionResult.empty();
    }

    public ExtractionResult<String> extractVpa(String msg) {
        if (msg == null) return ExtractionResult.empty();
        Matcher m = VPA.matcher(msg);
        while (m.find()) {
            String c = m.group(1);
            if (c != null && !EMAIL_EXCLUDE.matcher(c).find()) return ExtractionResult.of(c, 0.85);
        }
        return ExtractionResult.empty();
    }

    public ExtractionResult<String> extractBest(String msg) {
        ExtractionResult<String> ref = extractReferenceId(msg);
        return ref.isPresent() ? ref : extractVpa(msg);
    }
}
