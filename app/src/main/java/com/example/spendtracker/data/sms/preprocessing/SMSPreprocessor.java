package com.example.spendtracker.data.sms.preprocessing;

/**
 * Normalizes raw SMS text without destroying useful information.
 *
 * <p>Produces three representations that downstream components can choose from:
 * <ul>
 *   <li>{@code rawMessage} — original untouched text</li>
 *   <li>{@code normalizedMessage} — whitespace-normalized, trimmed</li>
 *   <li>{@code lowercaseMessage} — lowercase copy of the normalized message for keyword matching</li>
 * </ul>
 *
 * <p>This class is stateless and thread-safe.
 */
public class SMSPreprocessor {

    /**
     * Immutable holder for the three representations of a preprocessed SMS.
     */
    public static class PreprocessedSMS {
        private final String rawMessage;
        private final String normalizedMessage;
        private final String lowercaseMessage;
        private final String senderAddress;
        private final long timestamp;

        public PreprocessedSMS(String rawMessage, String normalizedMessage,
                               String lowercaseMessage, String senderAddress, long timestamp) {
            this.rawMessage = rawMessage;
            this.normalizedMessage = normalizedMessage;
            this.lowercaseMessage = lowercaseMessage;
            this.senderAddress = senderAddress;
            this.timestamp = timestamp;
        }

        public String getRawMessage() { return rawMessage; }
        public String getNormalizedMessage() { return normalizedMessage; }
        public String getLowercaseMessage() { return lowercaseMessage; }
        public String getSenderAddress() { return senderAddress; }
        public long getTimestamp() { return timestamp; }
    }

    /**
     * Preprocesses an incoming SMS.
     *
     * @param sender    SMS sender address (short code or phone number)
     * @param body      Raw SMS body
     * @param timestamp SMS receive timestamp (epoch millis)
     * @return A {@link PreprocessedSMS} with all three text representations
     */
    public PreprocessedSMS preprocess(String sender, String body, long timestamp) {
        if (body == null) body = "";

        String normalized = body.trim();

        // Collapse Windows-style line endings to Unix
        normalized = normalized.replace("\r\n", "\n");

        // Collapse multiple consecutive whitespace (including newlines) into a single space
        // but preserve meaningful newlines by first replacing single newlines with a marker
        normalized = normalized.replaceAll("[ \\t]+", " ");
        normalized = normalized.replaceAll("\\n\\s*\\n+", "\n");

        // Normalize common Unicode currency representations
        normalized = normalized.replace("\u20B9", "₹");   // Ensure consistent rupee symbol

        // Trim again after processing
        normalized = normalized.trim();

        String lowercase = normalized.toLowerCase();
        String normalizedSender = sender != null ? sender.trim().toUpperCase() : "";

        return new PreprocessedSMS(body, normalized, lowercase, normalizedSender, timestamp);
    }
}
