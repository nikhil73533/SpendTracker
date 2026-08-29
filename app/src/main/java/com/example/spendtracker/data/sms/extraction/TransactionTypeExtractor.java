package com.example.spendtracker.data.sms.extraction;

import com.example.spendtracker.data.sms.model.ExtractionResult;

import java.util.regex.Pattern;

/**
 * Determines the transaction type (EXPENSE, INCOME, or TRANSFER) from SMS keywords.
 *
 * <p>Transfer detection takes priority since transfers must not be classified as
 * expense or income. Debit wins when both debit and credit keywords are present
 * (common in "ICICI Acct debited … merchant credited" patterns).
 *
 * <p>This class is stateless and thread-safe.
 */
public class TransactionTypeExtractor {

    // ── Transfer indicators ──────────────────────────────────────────────────

    /** Strong transfer indicators (explicit transfer language). */
    private static final Pattern TRANSFER_STRONG = Pattern.compile(
            "(?i)\\b(fund\\s+transfer|self\\s+transfer|transferred\\s+(?:from|to)|" +
            "a/c\\s+\\w+\\s+to\\s+a/c|from\\s+(?:savings|current)\\s+.*?to\\s+(?:savings|current)|" +
            "neft\\s+transfer|imps\\s+transfer|rtgs\\s+transfer)\\b");

    /** Weaker transfer indicator: just the word "transferred". */
    private static final Pattern TRANSFER_KEYWORD = Pattern.compile(
            "(?i)\\btransferred\\b");

    /** Dual-account pattern: "from A/c XX1234 to A/c XX5678". */
    private static final Pattern DUAL_ACCOUNT = Pattern.compile(
            "(?i)(?:from|a/c)\\s+\\w*\\d{3,}\\s+(?:to)\\s+(?:a/c\\s+)?\\w*\\d{3,}");

    // ── Debit indicators ─────────────────────────────────────────────────────

    private static final Pattern DEBIT_PATTERN = Pattern.compile(
            "(?i)\\b(debited|deducted|dr\\.?|withdrawn|payment\\s+of|spent|paid|purchase|charged|debit)\\b");

    // ── Credit indicators ────────────────────────────────────────────────────

    private static final Pattern CREDIT_PATTERN = Pattern.compile(
            "(?i)\\b(credited|cr\\.?|received|added\\s+to|deposited|refund|cashback\\s+credited|salary\\s+credited)\\b");

    /**
     * Extracts the transaction type from the SMS body.
     *
     * @param normalizedMessage The normalized SMS body
     * @param lowercaseMessage  The lowercase normalized body
     * @return An {@link ExtractionResult} containing the type string and confidence
     */
    public ExtractionResult<String> extract(String normalizedMessage, String lowercaseMessage) {
        if (normalizedMessage == null || normalizedMessage.isEmpty()) {
            return ExtractionResult.empty();
        }

        // 1. Check for TRANSFER first (highest priority)
        // Transfers are unique because they involve moving money between user accounts.
        // We must ensure they aren't misclassified as standard EXPENSE or INCOME.
        boolean strongTransfer = TRANSFER_STRONG.matcher(normalizedMessage).find();
        boolean weakTransfer = TRANSFER_KEYWORD.matcher(lowercaseMessage).find();
        boolean dualAccount = DUAL_ACCOUNT.matcher(normalizedMessage).find();

        if (strongTransfer) {
            // Explicit transfer language (e.g. "fund transfer") found
            return ExtractionResult.of("TRANSFER", 0.95);
        }
        if (weakTransfer && dualAccount) {
            // "transferred" keyword combined with "from A/c X to A/c Y" found
            return ExtractionResult.of("TRANSFER", 0.90);
        }

        // 2. Check debit / credit patterns.
        // We search for explicitly defined words like "debited", "credited", "spent", etc.
        boolean isDebit = DEBIT_PATTERN.matcher(normalizedMessage).find();
        boolean isCredit = CREDIT_PATTERN.matcher(normalizedMessage).find();

        if (isDebit && isCredit) {
            // Conflict resolution: Both keywords present in the same message.
            // Example: "ICICI Acct debited for Rs 100; Amazon credited."
            // In Indian banking SMS, this almost always represents an OUTGOING payment (EXPENSE).
            return ExtractionResult.of("EXPENSE", 0.90);
        } else if (isDebit) {
            // Clear outgoing transaction
            return ExtractionResult.of("EXPENSE", 0.95);
        } else if (isCredit) {
            // Clear incoming transaction
            return ExtractionResult.of("INCOME", 0.95);
        }

        // 3. Weak transfer (just "transferred" without dual account)
        if (weakTransfer) {
            return ExtractionResult.of("TRANSFER", 0.70);
        }

        return ExtractionResult.empty();
    }
}
