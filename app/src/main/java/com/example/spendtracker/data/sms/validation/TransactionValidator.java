package com.example.spendtracker.data.sms.validation;

import com.example.spendtracker.data.sms.model.ParsedTransaction;
import java.util.ArrayList;
import java.util.List;

/**
 * Validates a {@link ParsedTransaction} after extraction and normalization.
 * 
 * Checks required fields, sanity bounds, and cross-field consistency.
 */
public class TransactionValidator {

    private static final double MAX_AMOUNT = 10_000_000;

    public static class ValidationResult {
        private final boolean valid;
        private final List<String> errors;
        private final List<String> warnings;

        public ValidationResult(boolean valid, List<String> errors, List<String> warnings) {
            this.valid = valid;
            this.errors = errors;
            this.warnings = warnings;
        }

        public boolean isValid() { return valid; }
        public List<String> getErrors() { return errors; }
        public List<String> getWarnings() { return warnings; }
    }

    /**
     * Validates the parsed transaction.
     *
     * @param parsed The parsed transaction to validate
     * @return A {@link ValidationResult} with errors and warnings
     */
    public ValidationResult validate(ParsedTransaction parsed) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        // Required: amount
        if (parsed.getAmount() == null || parsed.getAmount() <= 0) {
            errors.add("Amount is missing or non-positive");
        } else if (parsed.getAmount() > MAX_AMOUNT) {
            errors.add("Amount exceeds maximum threshold: " + parsed.getAmount());
        } else if (!Double.isFinite(parsed.getAmount())) {
            errors.add("Amount is not a finite number");
        }

        // Required: transaction type
        if (parsed.getTransactionType() == null || parsed.getTransactionType().isEmpty()) {
            errors.add("Transaction type is missing");
        } else {
            String type = parsed.getTransactionType();
            if (!"EXPENSE".equals(type) && !"INCOME".equals(type) && !"TRANSFER".equals(type)) {
                errors.add("Invalid transaction type: " + type);
            }
        }

        // Consistency: transfer should have from/to accounts ideally
        if ("TRANSFER".equals(parsed.getTransactionType())) {
            if (parsed.getFromAccount() == null && parsed.getToAccount() == null) {
                warnings.add("Transfer detected but no source/destination accounts found");
            }
        }

        // Consistency: transaction status
        String status = parsed.getTransactionStatus();
        if ("FAILED".equals(status)) {
            warnings.add("Transaction appears to have FAILED — should not be stored as expense/income");
        }

        // Warning: low confidence on critical fields
        if (parsed.getAmountConfidence() < 0.70) {
            warnings.add("Low amount confidence: " + String.format("%.2f", parsed.getAmountConfidence()));
        }
        if (parsed.getTransactionTypeConfidence() < 0.70) {
            warnings.add("Low type confidence: " + String.format("%.2f", parsed.getTransactionTypeConfidence()));
        }

        // Warning: no bank identified
        if (parsed.getBankName() == null || parsed.getBankName().isEmpty()) {
            warnings.add("Bank not identified");
        }

        // Warning: no merchant
        if (parsed.getMerchant() == null || parsed.getMerchant().isEmpty()) {
            warnings.add("Merchant not identified");
        }

        // Date sanity: not in the far future (> 30 days from now)
        long thirtyDays = 30L * 24 * 60 * 60 * 1000;
        if (parsed.getEffectiveDate() > System.currentTimeMillis() + thirtyDays) {
            warnings.add("Transaction date is more than 30 days in the future");
        }

        boolean valid = errors.isEmpty();
        return new ValidationResult(valid, errors, warnings);
    }
}
