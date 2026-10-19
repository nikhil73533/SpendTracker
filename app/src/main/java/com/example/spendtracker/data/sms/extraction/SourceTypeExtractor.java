package com.example.spendtracker.data.sms.extraction;

import com.example.spendtracker.data.sms.model.ExtractionResult;
import java.util.regex.Pattern;

/**
 * Determines the source type of a transaction: Account, Credit Card, Wallet, or UPI.
 */
public class SourceTypeExtractor {

    private static final Pattern CC_PATTERN = Pattern.compile(
        "(?i)\\b(credit\\s+card|cc\\s|/cc/)\\b");
    private static final Pattern WALLET_PATTERN = Pattern.compile(
        "(?i)\\b(wallet|paytm\\s+wallet|phonepe\\s+wallet)\\b");
    private static final Pattern UPI_PATTERN = Pattern.compile(
        "(?i)\\bupi\\b");
    private static final Pattern DEBIT_CARD_PATTERN = Pattern.compile(
        "(?i)\\bdebit\\s+card\\b");

    public ExtractionResult<String> extract(String normalizedMessage, String lowercaseMessage) {
        if (lowercaseMessage == null) return ExtractionResult.of("Account", 0.50);

        if (CC_PATTERN.matcher(lowercaseMessage).find())
            return ExtractionResult.of("Credit Card", 0.95);
        if (DEBIT_CARD_PATTERN.matcher(lowercaseMessage).find())
            return ExtractionResult.of("Debit Card", 0.90);
        if (WALLET_PATTERN.matcher(lowercaseMessage).find())
            return ExtractionResult.of("Wallet", 0.85);
        if (UPI_PATTERN.matcher(lowercaseMessage).find())
            return ExtractionResult.of("UPI", 0.85);

        return ExtractionResult.of("Account", 0.60);
    }
}
