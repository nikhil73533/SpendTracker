package com.example.spendtracker.data.sms.detection;

import com.example.spendtracker.data.sms.model.ExtractionResult;
import com.example.spendtracker.data.sms.normalization.BankNormalizer;

import java.util.regex.Pattern;

/**
 * Identifies the bank / financial institution from both the SMS sender ID
 * and the message body.
 *
 * <p>Sender-based identification is preferred (higher confidence) because sender
 * short codes are registered by banks and are more reliable than body text.
 *
 * <p>This class is stateless and thread-safe.
 */
public class BankIdentifier {

    /**
     * Sender-ID → bank name mapping.
     * Ordered with more-specific patterns first to avoid false matches.
     * The first matching pattern wins.
     */
    private static final String[][] SENDER_PATTERNS = {
        // Format: {sender substring (uppercase), bank display name}
        {"HDFCBK",     "HDFC Bank"},
        {"HDFC",       "HDFC Bank"},
        {"ICICIB",     "ICICI Bank"},
        {"ICICI",      "ICICI Bank"},
        {"AXISBK",     "Axis Bank"},
        {"AXIS",       "Axis Bank"},
        {"KOTAKB",     "Kotak Bank"},
        {"KOTAK",      "Kotak Bank"},
        {"SBIUPI",     "SBI"},
        {"SBIIN",      "SBI"},
        {"SBI",        "SBI"},
        {"PNBSMS",     "PNB"},
        {"PUNJNB",     "PNB"},
        {"PNB",        "PNB"},
        {"BOBIN",      "Bank of Baroda"},
        {"BARODA",     "Bank of Baroda"},
        {"BOB",        "Bank of Baroda"},
        {"YESBK",      "Yes Bank"},
        {"YESBNK",     "Yes Bank"},
        {"CANBNK",     "Canara Bank"},
        {"CANARA",     "Canara Bank"},
        {"IDBIB",      "IDBI Bank"},
        {"INDBNK",     "IndusInd Bank"},
        {"INDUS",      "IndusInd Bank"},
        {"FEDBK",      "Federal Bank"},
        {"RBLBNK",     "RBL Bank"},
        {"AU-BANK",    "AU Bank"},
        {"AUBANK",     "AU Bank"},
        {"BAJFIN",     "Bajaj Finance"},
        {"PAYTMB",     "Paytm Payments Bank"},
        {"PAYTM",      "Paytm"},
        {"PPBNK",      "PhonePe"},
        {"PHONEPE",    "PhonePe"},
        {"AMZPAY",     "Amazon Pay"},
        {"AIRTEL",     "Airtel Payments Bank"},
        {"JIOFIN",     "Jio Payments Bank"},
        {"ONECARD",    "OneCard"},
        {"SLICE",      "Slice"},
        {"IDFC",       "IDFC First Bank"},
    };

    /**
     * Body-text bank keyword → bank name mapping.
     * Used as fallback when sender-based identification fails.
     * Ordered longest-first to avoid partial matches.
     */
    private static final String[][] BODY_KEYWORDS = {
        {"au bank",          "AU Bank"},
        {"au a/c",           "AU Bank"},
        {"au small",         "AU Bank"},
        {"amazon pay",       "Amazon Pay"},
        {"airtel payments",  "Airtel Payments Bank"},
        {"jio payments",     "Jio Payments Bank"},
        {"union bank",       "Union Bank"},
        {"bank of baroda",   "Bank of Baroda"},
        {"yes bank",         "Yes Bank"},
        {"one card",         "OneCard"},
        {"onecard",          "OneCard"},
        {"bajaj finance",    "Bajaj Finance"},
        {"indusind",         "IndusInd Bank"},
        {"federal",          "Federal Bank"},
        {"idfc first",       "IDFC First Bank"},
        {"icici",            "ICICI Bank"},
        {"hdfc",             "HDFC Bank"},
        {"axis",             "Axis Bank"},
        {"kotak",            "Kotak Bank"},
        {"canara",           "Canara Bank"},
        {"paytm",            "Paytm"},
        {"phonepe",          "PhonePe"},
        {"gpay",             "Google Pay"},
        {"mobikwik",         "MobiKwik"},
        {"yesb",             "Yes Bank"},
        {"pnb",              "PNB"},
        {"bob",              "Bank of Baroda"},
        {"idbi",             "IDBI Bank"},
        {"rbl",              "RBL Bank"},
        {"sbi",              "SBI"},
        {"slice",            "Slice"},
        {"navi",             "Navi"},
    };

    /**
     * Identifies the bank from the sender ID and message body.
     *
     * @param senderAddress  The SMS sender address (already uppercased by preprocessor)
     * @param lowercaseBody  The lowercase message body
     * @param normalizer     The bank name normalizer for canonical name mapping
     * @return An {@link ExtractionResult} with the bank name and confidence
     */
    public ExtractionResult<String> identify(String senderAddress, String lowercaseBody,
                                              BankNormalizer normalizer) {
        // 1. Try sender-based identification (high confidence - 0.95)
        // Bank sender IDs are officially registered (e.g., AD-HDFCBK), 
        // making them the most reliable source for bank identification.
        if (senderAddress != null && !senderAddress.isEmpty()) {
            for (String[] entry : SENDER_PATTERNS) {
                if (senderAddress.contains(entry[0])) {
                    // Convert internal matched name to canonical standardized name
                    String normalized = normalizer.normalize(entry[1]);
                    return ExtractionResult.of(normalized, 0.95);
                }
            }
        }

        // 2. Fall back to body-text identification (lower confidence - 0.80)
        // If the sender ID was generic (e.g., a phone number), we scan the message
        // body for bank names (e.g., "HDFC Bank").
        // This is less reliable due to potential spoofing or merchant names matching bank names.
        if (lowercaseBody != null) {
            for (String[] entry : BODY_KEYWORDS) {
                if (lowercaseBody.contains(entry[0])) {
                    // Convert internal matched name to canonical standardized name
                    String normalized = normalizer.normalize(entry[1]);
                    return ExtractionResult.of(normalized, 0.80);
                }
            }
        }

        return ExtractionResult.empty();
    }
}
