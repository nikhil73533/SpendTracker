package com.example.spendtracker.data.sms.extraction;

import com.example.spendtracker.data.sms.model.ExtractionResult;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts masked account / card numbers from SMS messages.
 *
 * <p>Handles common Indian banking formats:
 * <ul>
 *   <li>{@code A/c XX1234}, {@code Acct XX1234}, {@code Account XX1234}</li>
 *   <li>{@code A/C X3698}</li>
 *   <li>{@code Card ending 1234}, {@code Credit Card ****1234}</li>
 *   <li>{@code XXXX1234}</li>
 * </ul>
 *
 * <p>For transfers, also extracts the source and destination accounts.
 *
 * <p>This class is stateless and thread-safe.
 */
public class AccountExtractor {

    /** Primary account pattern: A/c/Acct/Account + optional mask + digits. */
    private static final Pattern ACCOUNT_PATTERN = Pattern.compile(
            "(?i)(?:a/c|acct|account)\\s+(?:(?:no\\.?|number)\\s+)?(?:XX*|\\*+)?\\s*([0-9]{3,})");

    /** Card pattern: Card/Credit Card + optional mask + digits. */
    private static final Pattern CARD_PATTERN = Pattern.compile(
            "(?i)(?:credit\\s+card|debit\\s+card|card)\\s+(?:ending|xx*|\\*+)?\\s*([0-9]{3,})");

    /** Standalone masked pattern: XX1234 or XXXX1234 without preceding keyword. */
    private static final Pattern MASKED_PATTERN = Pattern.compile(
            "\\bX{1,4}(\\d{3,6})\\b");

    /**
     * Transfer dual-account pattern: "from A/c XX1234 to A/c XX5678".
     * Group 1 = source account, Group 2 = destination account.
     */
    private static final Pattern TRANSFER_ACCOUNT_PATTERN = Pattern.compile(
            "(?i)(?:from)\\s+(?:(?:savings|current)\\s+)?(?:a/c|acct|account)\\s+(?:XX*|\\*+)?\\s*([0-9]{3,})" +
            "\\s+(?:to)\\s+(?:(?:savings|current)\\s+)?(?:a/c|acct|account)\\s+(?:XX*|\\*+)?\\s*([0-9]{3,})");

    /**
     * Extracts the primary account/card suffix.
     *
     * @param normalizedMessage The normalized SMS body
     * @return An {@link ExtractionResult} with the account suffix
     */
    public ExtractionResult<String> extract(String normalizedMessage) {
        if (normalizedMessage == null || normalizedMessage.isEmpty()) {
            return ExtractionResult.empty();
        }

        // Try explicit account pattern first
        Matcher m = ACCOUNT_PATTERN.matcher(normalizedMessage);
        if (m.find()) {
            return ExtractionResult.of(m.group(1), 0.95);
        }

        // Try card pattern
        m = CARD_PATTERN.matcher(normalizedMessage);
        if (m.find()) {
            return ExtractionResult.of(m.group(1), 0.95);
        }

        // Try standalone masked pattern
        m = MASKED_PATTERN.matcher(normalizedMessage);
        if (m.find()) {
            return ExtractionResult.of(m.group(1), 0.80);
        }

        return ExtractionResult.empty();
    }

    /**
     * Extracts source and destination accounts for transfer transactions.
     *
     * @param normalizedMessage The normalized SMS body
     * @return A two-element array [fromAccount, toAccount], or {@code null} if not found
     */
    public String[] extractTransferAccounts(String normalizedMessage) {
        if (normalizedMessage == null) return null;

        Matcher m = TRANSFER_ACCOUNT_PATTERN.matcher(normalizedMessage);
        if (m.find()) {
            return new String[]{m.group(1), m.group(2)};
        }
        return null;
    }
}
