package com.example.spendtracker.data.sms.duplicate;

/**
 * Fingerprint-based duplicate detection for SMS transactions.
 * 
 * Prevents the same SMS from being processed twice when Android delivers
 * duplicates or the app re-processes messages.
 */
public class DuplicateDetector {

    /**
     * Generates a deterministic fingerprint for a transaction based on its key fields.
     *
     * @param bankName    The normalized bank name
     * @param amount      The transaction amount
     * @param timestamp   The transaction timestamp
     * @param referenceId The reference/transaction ID (may be null)
     * @param accountSuffix The account suffix (may be null)
     * @param merchant    The merchant name (may be null)
     * @return A string fingerprint for deduplication
     */
    public String generateFingerprint(String bankName, double amount, long timestamp,
                                       String referenceId, String accountSuffix, String merchant) {
        // If we have a reference ID, it's the strongest key
        if (referenceId != null && !referenceId.isEmpty()) {
            return "ref:" + referenceId;
        }

        // Build a composite fingerprint
        StringBuilder sb = new StringBuilder();
        sb.append("fp:");
        sb.append(bankName != null ? bankName : "unknown").append('|');
        sb.append(String.format("%.2f", amount)).append('|');

        // Round timestamp to nearest minute to handle slight delivery variations
        long roundedTs = (timestamp / 60000) * 60000;
        sb.append(roundedTs).append('|');

        if (accountSuffix != null && !accountSuffix.isEmpty()) {
            sb.append(accountSuffix);
        }
        sb.append('|');
        if (merchant != null && !merchant.isEmpty()) {
            sb.append(merchant.toLowerCase().trim());
        }

        return sb.toString();
    }

    /**
     * Checks if two fingerprints represent the same transaction.
     */
    public boolean isDuplicate(String fingerprint1, String fingerprint2) {
        if (fingerprint1 == null || fingerprint2 == null) return false;
        return fingerprint1.equals(fingerprint2);
    }
}
