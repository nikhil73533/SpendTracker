package com.example.prediction.util;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Normalizes raw merchant/payee strings into a stable, lowercase, deduplicated key.
 *
 * <p>Rules:
 * <ol>
 *   <li>Lowercase</li>
 *   <li>Strip UPI suffixes (@okicici, @ybl, etc.)</li>
 *   <li>Strip numeric tokens (transaction IDs, phone numbers, etc.)</li>
 *   <li>Strip noise words (pvt, ltd, co, india, payment, …)</li>
 *   <li>Collapse whitespace</li>
 *   <li>Trim to max 40 chars</li>
 * </ol>
 */
public final class MerchantNormalizer {

    // UPI VPA suffix pattern: anything starting with @ followed by provider code
    private static final Pattern UPI_SUFFIX = Pattern.compile("@[a-z0-9.]+");

    // Digit-only tokens (IDs, phone numbers)
    private static final Pattern DIGIT_TOKEN = Pattern.compile("\\b\\d{4,}\\b");

    // Short numeric codes like "A/C" last 4 digits
    private static final Pattern SHORT_CODE = Pattern.compile("\\bx+\\d{2,4}\\b", Pattern.CASE_INSENSITIVE);

    // Common noise words that add no merchant identity
    private static final Pattern NOISE = Pattern.compile(
        "\\b(pvt|ltd|co|inc|corp|india|payment|pay|wallet|fund|enterprises|services|service|shop|store|mart|" +
        "upi|ref|txn|via|through|using|on|to|from|by|at|for|and|or|of|the|a|an)\\b",
        Pattern.CASE_INSENSITIVE);

    // Multiple spaces → single space
    private static final Pattern MULTI_SPACE = Pattern.compile("\\s{2,}");

    private MerchantNormalizer() {}

    /**
     * Normalizes a raw merchant/payee/sender string to a stable lookup key.
     * Returns "unknown" for null/blank input.
     */
    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) return "unknown";

        String s = raw.toLowerCase(Locale.ROOT);

        // Remove UPI @ suffix
        s = UPI_SUFFIX.matcher(s).replaceAll("");

        // Remove long digit tokens (ref numbers, phone numbers)
        s = DIGIT_TOKEN.matcher(s).replaceAll("");

        // Remove short account masks (xxxx1234)
        s = SHORT_CODE.matcher(s).replaceAll("");

        // Remove noise words
        s = NOISE.matcher(s).replaceAll(" ");

        // Normalize punctuation and symbols to space
        s = s.replaceAll("[^a-z0-9 ]", " ");

        // Collapse whitespace
        s = MULTI_SPACE.matcher(s).replaceAll(" ").strip();

        if (s.isEmpty()) return "unknown";

        // Limit to 40 chars for key stability
        return s.length() > 40 ? s.substring(0, 40).strip() : s;
    }

    /**
     * Generates a short 2-word prefix key for token-based statistics.
     * e.g. "swiggy food platform" → "swiggy food"
     */
    public static String tokenKey(String merchantKey) {
        if (merchantKey == null || merchantKey.isBlank()) return "unknown";
        String[] parts = merchantKey.trim().split(" ", 3);
        return parts.length >= 2 ? parts[0] + " " + parts[1] : parts[0];
    }
}
