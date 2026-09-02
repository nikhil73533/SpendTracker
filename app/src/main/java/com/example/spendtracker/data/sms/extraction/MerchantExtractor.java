package com.example.spendtracker.data.sms.extraction;

import com.example.spendtracker.data.sms.model.ExtractionResult;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts the merchant / receiver name from SMS messages.
 *
 * <p>Uses a priority chain of strategies:
 * <ol>
 *   <li>AU Bank UPI/DR format: {@code UPI/DR/<ref>/<merchant>/<bank>}</li>
 *   <li>ICICI UPI format: {@code for UPI-<ref>-<merchant>}</li>
 *   <li>"credited" pattern: {@code <Name> credited} (sender name in credit messages)</li>
 *   <li>Generic "to/by/from/at" pattern</li>
 *   <li>"paid to" / "payment to" pattern</li>
 * </ol>
 *
 * <p>This class is stateless and thread-safe.
 */
public class MerchantExtractor {

    // ── Bank-specific patterns ───────────────────────────────────────────────

    /** AU Bank: UPI/DR/<ref>/<merchant>/<bank> */
    private static final Pattern AU_BANK_PATTERN = Pattern.compile(
            "UPI/(?:DR|CR)/(\\d+)/([^/]+)/", Pattern.CASE_INSENSITIVE);

    /** ICICI: "for UPI-<ref>-<merchant>." */
    private static final Pattern ICICI_UPI_PATTERN = Pattern.compile(
            "for\\s+UPI-\\d+-([^.]+)", Pattern.CASE_INSENSITIVE);

    // ── Generic patterns ─────────────────────────────────────────────────────

    /** "<Name> credited" — extract sender of money (the payer, who is the "merchant" for income). */
    private static final Pattern CREDITOR_PATTERN = Pattern.compile(
            "([A-Z][A-Za-z\\s.]{2,30})\\s+credited", Pattern.CASE_INSENSITIVE);

    /** "to/by/from/at <Name>" followed by a delimiter. */
    private static final Pattern GENERIC_TO_BY_PATTERN = Pattern.compile(
            "(?i)(?:to|by|from|at)\\s+([A-Z][A-Za-z\\s&'.,-]{2,40}?)" +
            "(?=\\s*(?:on|via|using|a/c|upi|ref|\\d|\\.|,|$))");

    /** "paid to <Name>" / "payment to <Name>". */
    private static final Pattern PAID_TO_PATTERN = Pattern.compile(
            "(?i)(?:paid|payment)\\s+(?:to|for)\\s+([A-Z][A-Za-z\\s&'.,-]{2,40}?)" +
            "(?=\\s*(?:on|via|using|a/c|upi|ref|\\.|,|Rs|INR|₹|$))");

    /** Words to reject as false-positive merchant names. */
    private static final Pattern REJECT_NAMES = Pattern.compile(
            "(?i)^(INR|Rs\\.?|your|bank|a/c|account|the|and|for|with|from)$");

    /**
     * Extracts the merchant/receiver name from the SMS body.
     *
     * @param normalizedMessage The normalized SMS body
     * @param bankName          The identified bank name (for context)
     * @return An {@link ExtractionResult} containing the merchant name and confidence
     */
    public ExtractionResult<String> extract(String normalizedMessage, String bankName) {
        if (normalizedMessage == null || normalizedMessage.isEmpty()) {
            return ExtractionResult.empty();
        }

        // Strategy 1: AU Bank UPI format (UPI/DR/<ref>/<merchant>/<bank>)
        // This is a highly structured string, so it yields high confidence (0.95).
        Matcher auMatcher = AU_BANK_PATTERN.matcher(normalizedMessage);
        if (auMatcher.find() && auMatcher.group(2) != null) {
            String merchant = cleanMerchant(auMatcher.group(2));
            if (isValidMerchant(merchant, bankName)) {
                return ExtractionResult.of(merchant, 0.95);
            }
        }

        // Strategy 2: ICICI UPI format
        Matcher iciciMatcher = ICICI_UPI_PATTERN.matcher(normalizedMessage);
        if (iciciMatcher.find() && iciciMatcher.group(1) != null) {
            String merchant = cleanMerchant(iciciMatcher.group(1));
            if (isValidMerchant(merchant, bankName)) {
                return ExtractionResult.of(merchant, 0.92);
            }
        }

        // Strategy 3: "paid to" / "payment to"
        Matcher paidMatcher = PAID_TO_PATTERN.matcher(normalizedMessage);
        if (paidMatcher.find() && paidMatcher.group(1) != null) {
            String merchant = cleanMerchant(paidMatcher.group(1));
            if (isValidMerchant(merchant, bankName)) {
                return ExtractionResult.of(merchant, 0.85);
            }
        }

        // Strategy 4: "<Name> credited" format.
        // In credit SMS messages, the sender is usually placed immediately before the word "credited".
        // Example: "JOHN DOE credited." -> extracts "JOHN DOE" with 0.80 confidence.
        Matcher creditorMatcher = CREDITOR_PATTERN.matcher(normalizedMessage);
        if (creditorMatcher.find() && creditorMatcher.group(1) != null) {
            String candidate = cleanMerchant(creditorMatcher.group(1));
            if (isValidMerchant(candidate, bankName)) {
                return ExtractionResult.of(candidate, 0.80);
            }
        }

        // Strategy 5: Generic "to/by/from/at"
        Matcher genericMatcher = GENERIC_TO_BY_PATTERN.matcher(normalizedMessage);
        if (genericMatcher.find() && genericMatcher.group(1) != null) {
            String candidate = cleanMerchant(genericMatcher.group(1));
            if (isValidMerchant(candidate, bankName)) {
                return ExtractionResult.of(candidate, 0.70);
            }
        }

        return ExtractionResult.empty();
    }

    /** Trims and cleans a raw merchant name. */
    private String cleanMerchant(String raw) {
        if (raw == null) return "";
        String cleaned = raw.trim();
        // Remove trailing punctuation
        cleaned = cleaned.replaceAll("[.,;:!]+$", "").trim();
        return cleaned;
    }

    /** Validates that a candidate merchant name is not garbage. */
    private boolean isValidMerchant(String merchant, String bankName) {
        if (merchant == null || merchant.isEmpty() || merchant.length() < 2) return false;
        if (REJECT_NAMES.matcher(merchant).matches()) return false;
        // Avoid capturing bank name itself or generic account keywords as merchant
        if (bankName != null && merchant.toLowerCase().contains(bankName.toLowerCase())) return false;
        if (merchant.toLowerCase().contains("bank")) return false;
        if (merchant.toLowerCase().contains("a/c") || merchant.toLowerCase().contains("account")) return false;
        return true;
    }
}
