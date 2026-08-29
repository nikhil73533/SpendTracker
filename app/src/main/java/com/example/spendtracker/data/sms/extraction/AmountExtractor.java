package com.example.spendtracker.data.sms.extraction;

import com.example.spendtracker.data.sms.model.ExtractionResult;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts monetary amounts from SMS messages.
 *
 * <p>Supports all common Indian banking formats:
 * <ul>
 *   <li>{@code ₹500}, {@code ₹ 500}</li>
 *   <li>{@code Rs 500}, {@code Rs.500}, {@code Rs. 500}</li>
 *   <li>{@code INR 500}, {@code INR1,250.50}</li>
 *   <li>{@code Amount: Rs 500}</li>
 *   <li>{@code Dr 148.00} / {@code Cr 2500.00} (AU Bank style)</li>
 * </ul>
 *
 * <p>Handles Indian-style comma grouping (e.g. {@code 1,25,000.50}).
 *
 * <p>This class is stateless and thread-safe.
 */
public class AmountExtractor {

    /** Maximum reasonable transaction amount (10 crore). */
    private static final double MAX_AMOUNT = 10_000_000;

    /**
     * Amount patterns in priority order.
     * Group 1 = numeric amount string (digits, commas, dots).
     */
    private static final Pattern[] AMOUNT_PATTERNS = {
        // Currency prefix: INR / Rs / Rs. / ₹ — handles "INR1,500" (no space)
        Pattern.compile("(?i)(?:INR|Rs\\.?|₹)\\s*([0-9,]+(?:\\.[0-9]{1,2})?)"),

        // Keyword + amount: "debited Rs 500" / "credited Rs. 1,000.00"
        Pattern.compile("(?i)(?:debited|credited|spent|withdrawn|charged|deducted|transferred)\\s+" +
                         "(?:(?:INR|Rs\\.?|₹)\\s*)?([0-9,]+(?:\\.[0-9]{1,2})?)"),

        // "amount of Rs 500" / "amount Rs500"
        Pattern.compile("(?i)amount(?:\\s+of)?\\s+(?:(?:INR|Rs\\.?|₹)\\s*)?([0-9,]+(?:\\.[0-9]{1,2})?)"),

        // "Dr 148.00" at start of line (AU Bank style)
        Pattern.compile("(?im)^Dr\\s+(?:INR\\s*)?([0-9,]+(?:\\.[0-9]{1,2})?)"),

        // "Cr 2500.00" at start of line (AU Bank style)
        Pattern.compile("(?im)^Cr\\s+(?:INR\\s*)?([0-9,]+(?:\\.[0-9]{1,2})?)"),
    };

    /**
     * Extracts the first valid amount from the message.
     *
     * @param normalizedMessage The normalized SMS body
     * @return An {@link ExtractionResult} containing the amount and confidence
     */
    public ExtractionResult<Double> extract(String normalizedMessage) {
        if (normalizedMessage == null || normalizedMessage.isEmpty()) {
            return ExtractionResult.empty();
        }

        // Iterate through all known amount regex patterns in priority order.
        // Priority ensures that highly specific patterns (e.g. "debited Rs 500")
        // match before generic loose patterns.
        for (int i = 0; i < AMOUNT_PATTERNS.length; i++) {
            Matcher m = AMOUNT_PATTERNS[i].matcher(normalizedMessage);
            
            // If we find a match, extract the first capturing group which contains the numeric part
            if (m.find()) {
                String raw = m.group(1);
                if (raw == null) continue;

                try {
                    // Remove commas and parse the string to a double (e.g. "1,25,000.50" -> 125000.50)
                    double amount = parseAmountString(raw);
                    
                    // Validate that the parsed amount is within reasonable bounds
                    // This prevents capturing random strings of numbers (like phone numbers) as amounts.
                    if (amount > 0 && amount < MAX_AMOUNT) {
                        // Assign higher confidence scores to earlier (more specific) patterns.
                        // Pattern 0 (explicit currency prefix without context) is highly confident,
                        // Pattern 1 & 2 (contextual keywords) are very confident,
                        // Patterns 3 & 4 (generic Dr/Cr) are moderately confident.
                        double confidence = i == 0 ? 0.99 : (i <= 2 ? 0.95 : 0.85);
                        return ExtractionResult.of(amount, confidence);
                    }
                } catch (NumberFormatException ignored) {
                    // Ignore parse errors and continue to the next pattern if available
                }
            }
        }

        return ExtractionResult.empty();
    }

    /**
     * Parses a raw amount string (with commas) into a double.
     * Handles Indian-style grouping: "1,25,000.50" → 125000.50
     */
    static double parseAmountString(String raw) {
        if (raw == null || raw.isEmpty()) return 0;
        return Double.parseDouble(raw.replace(",", ""));
    }
}
