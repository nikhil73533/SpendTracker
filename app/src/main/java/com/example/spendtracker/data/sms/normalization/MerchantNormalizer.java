package com.example.spendtracker.data.sms.normalization;

import java.util.regex.Pattern;

/**
 * Cleans and normalizes merchant names extracted from SMS messages.
 */
public class MerchantNormalizer {

    private static final Pattern TRAILING_PUNCT = Pattern.compile("[.,;:!\\-]+$");
    private static final Pattern EXTRA_SPACES = Pattern.compile("\\s{2,}");
    private static final Pattern UPI_SUFFIX = Pattern.compile("(?i)\\s*@\\w+$");

    /**
     * Normalizes a raw merchant name.
     *
     * @param raw The raw extracted merchant name
     * @return The cleaned merchant name
     */
    public String normalize(String raw) {
        if (raw == null || raw.trim().isEmpty()) return "";

        String cleaned = raw.trim();

        // Remove trailing punctuation
        cleaned = TRAILING_PUNCT.matcher(cleaned).replaceAll("").trim();

        // Collapse extra spaces
        cleaned = EXTRA_SPACES.matcher(cleaned).replaceAll(" ");

        // Remove UPI VPA suffix if accidentally included
        cleaned = UPI_SUFFIX.matcher(cleaned).replaceAll("").trim();

        // Title case if all uppercase and > 3 chars (likely a shouted name)
        if (cleaned.length() > 3 && cleaned.equals(cleaned.toUpperCase()) && cleaned.matches("[A-Z\\s]+")) {
            cleaned = toTitleCase(cleaned);
        }

        return cleaned;
    }

    private String toTitleCase(String input) {
        StringBuilder sb = new StringBuilder();
        boolean nextUpper = true;
        for (char c : input.toCharArray()) {
            if (Character.isWhitespace(c)) {
                sb.append(c);
                nextUpper = true;
            } else if (nextUpper) {
                sb.append(Character.toUpperCase(c));
                nextUpper = false;
            } else {
                sb.append(Character.toLowerCase(c));
            }
        }
        return sb.toString();
    }
}
