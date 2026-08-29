package com.example.spendtracker.data.sms;

import android.content.Context;

import com.example.spendtracker.data.sms.config.BankConfig;
import com.example.spendtracker.data.sms.config.BankConfigProvider;
import com.example.spendtracker.data.sms.detection.BankIdentifier;

import com.example.spendtracker.data.sms.detection.TransactionDetector;
import com.example.spendtracker.data.sms.duplicate.DuplicateDetector;
import com.example.spendtracker.data.sms.extraction.*;
import com.example.spendtracker.data.sms.model.*;
import com.example.spendtracker.data.sms.normalization.BankNormalizer;
import com.example.spendtracker.data.sms.normalization.MerchantNormalizer;
import com.example.spendtracker.data.sms.preprocessing.SMSPreprocessor;
import com.example.spendtracker.data.sms.validation.TransactionValidator;
import com.example.spendtracker.domain.model.Transaction;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.hilt.android.qualifiers.ApplicationContext;

/**
 * Orchestrates the complete SMS parsing pipeline:
 *
 * <pre>
 * SMS → Preprocess → Transaction Detection → Bank Detection
 *     → Bank-Specific Pattern → Generic Extraction → Normalization
 *     → Validation → Duplicate Detection → ParseResult
 * </pre>
 *
 * <p>This class replaces the monolithic {@code SMSParser} with a modular pipeline
 * where each stage is independently testable and replaceable.
 */
@Singleton
public class SMSParsingService {

    private final SMSPreprocessor preprocessor;

    private final TransactionDetector transactionDetector;
    private final BankIdentifier bankIdentifier;
    private final BankConfigProvider bankConfigProvider;
    private final AmountExtractor amountExtractor;
    private final TransactionTypeExtractor typeExtractor;
    private final MerchantExtractor merchantExtractor;
    private final AccountExtractor accountExtractor;
    private final UpiExtractor upiExtractor;
    private final DateExtractor dateExtractor;
    private final SourceTypeExtractor sourceTypeExtractor;
    private final TransactionStatusExtractor statusExtractor;
    private final BankNormalizer bankNormalizer;
    private final MerchantNormalizer merchantNormalizer;
    private final TransactionValidator validator;
    private final DuplicateDetector duplicateDetector;

    // ── Constructors ─────────────────────────────────────────────────────────

    /** Hilt-injected constructor for production use. */
    @Inject
    public SMSParsingService(@ApplicationContext Context context) {
        this.preprocessor = new SMSPreprocessor();
        this.transactionDetector = new TransactionDetector();
        this.bankIdentifier = new BankIdentifier();
        this.bankConfigProvider = new BankConfigProvider(context);
        this.amountExtractor = new AmountExtractor();
        this.typeExtractor = new TransactionTypeExtractor();
        this.merchantExtractor = new MerchantExtractor();
        this.accountExtractor = new AccountExtractor();
        this.upiExtractor = new UpiExtractor();
        this.dateExtractor = new DateExtractor();
        this.sourceTypeExtractor = new SourceTypeExtractor();
        this.statusExtractor = new TransactionStatusExtractor();
        this.bankNormalizer = new BankNormalizer();
        this.merchantNormalizer = new MerchantNormalizer();
        this.validator = new TransactionValidator();
        this.duplicateDetector = new DuplicateDetector();
    }

    /** No-arg constructor for unit tests (no Android context, no bank JSON configs). */
    public SMSParsingService(SMSPreprocessor preprocessor,
                              TransactionDetector transactionDetector, BankIdentifier bankIdentifier,
                              AmountExtractor amountExtractor, TransactionTypeExtractor typeExtractor,
                              MerchantExtractor merchantExtractor, AccountExtractor accountExtractor,
                              UpiExtractor upiExtractor, DateExtractor dateExtractor,
                              SourceTypeExtractor sourceTypeExtractor, TransactionStatusExtractor statusExtractor,
                              BankNormalizer bankNormalizer, MerchantNormalizer merchantNormalizer,
                              TransactionValidator validator, DuplicateDetector duplicateDetector) {
        this.preprocessor = preprocessor;
        this.transactionDetector = transactionDetector;
        this.bankIdentifier = bankIdentifier;
        this.bankConfigProvider = new BankConfigProvider();
        this.amountExtractor = amountExtractor;
        this.typeExtractor = typeExtractor;
        this.merchantExtractor = merchantExtractor;
        this.accountExtractor = accountExtractor;
        this.upiExtractor = upiExtractor;
        this.dateExtractor = dateExtractor;
        this.sourceTypeExtractor = sourceTypeExtractor;
        this.statusExtractor = statusExtractor;
        this.bankNormalizer = bankNormalizer;
        this.merchantNormalizer = merchantNormalizer;
        this.validator = validator;
        this.duplicateDetector = duplicateDetector;
    }

    // ── Public API ───────────────────────────────────────────────────────────

    /**
     * Parses an SMS message through the full pipeline.
     *
     * @param sender    SMS sender address
     * @param body      Raw SMS body
     * @param timestamp SMS receive timestamp (epoch millis), or 0 to use current time
     * @return A {@link ParseResult} with status, transaction (if successful), and diagnostics
     */
    public ParseResult parse(String sender, String body, long timestamp) {
        if (body == null || body.isEmpty()) {
            return ParseResult.builder()
                    .status(ParseStatus.NOT_TRANSACTION)
                    .addError("Empty or null SMS body")
                    .build();
        }

        if (timestamp <= 0) timestamp = System.currentTimeMillis();

        // Step 1: Preprocess the incoming raw message.
        // This generates a normalized representation (e.g. standardizing spaces/newlines)
        // and a lowercase version to optimize regex matching in later stages.
        SMSPreprocessor.PreprocessedSMS sms = preprocessor.preprocess(sender, body, timestamp);

        // Step 2: Transaction Detection.
        // We use a multi-signal approach (keywords, monetary values, context) to determine
        // if this SMS is a financial transaction.
        DetectionResult txnResult = transactionDetector.detect(sms.getNormalizedMessage(), sms.getLowercaseMessage());
        if (!txnResult.isDetected()) {
            // Check if the rejection was due to promotional/marketing signals
            // (e.g. "apply now", "loan offer"). If so, categorize it as PROMOTIONAL
            // rather than a generic NOT_TRANSACTION.
            boolean isPromo = txnResult.getMatchedSignals().stream()
                    .anyMatch(s -> s.startsWith("negative:"));
            return ParseResult.builder()
                    .status(isPromo ? ParseStatus.PROMOTIONAL : ParseStatus.NOT_TRANSACTION)
                    .confidence(txnResult.getConfidence())
                    .build();
        }

        // Step 3: Bank Identification.
        // We attempt to identify the originating bank using both the sender ID (e.g. "HDFCBK")
        // and internal message keywords.
        ExtractionResult<String> bankResult = bankIdentifier.identify(
                sms.getSenderAddress(), sms.getLowercaseMessage(), bankNormalizer);

        String bankName = bankResult.isPresent() ? bankResult.getValue() : null;
        ParseResult.Builder resultBuilder = ParseResult.builder()
                .detectedBank(bankName);

        // Step 4: Try bank-specific pattern parsing.
        // If we identified the bank, we look up its specific regex configurations from
        // the JSON assets. These patterns are highly accurate and extract multiple fields at once.
        ParsedTransaction parsed = tryBankSpecificParsing(sms, bankName, resultBuilder);

        // Step 5: If bank-specific parsing failed (or no bank was identified),
        // fallback to generic heuristic extractors. These scan for common currency formats,
        // credit/debit keywords, etc., to salvage the transaction data.
        if (parsed == null) {
            parsed = genericExtract(sms);
            resultBuilder.matchedPattern("generic");
        }

        // Apply the identified bank to the parsed intermediate object.
        if (bankName != null) {
            parsed.setBankName(bankName);
            parsed.setBankConfidence(bankResult.getConfidence());
        }

        // Step 6: Extract additional fields.
        // We run specialized extractors for any fields that weren't captured by the initial
        // bank-specific or generic extraction (e.g., date, merchant, UPI references).
        enrichParsedTransaction(parsed, sms);

        // Step 7: Normalization.
        // Standardize strings (e.g. mapping "HDFC BANK LTD" -> "HDFC", fixing merchant capitalization).
        normalizeParsedTransaction(parsed);

        // Step 8: Validation.
        // Ensure the transaction has the absolute minimum required fields (like a non-zero Amount)
        // and collect any warnings (like a transfer missing account details).
        TransactionValidator.ValidationResult validationResult = validator.validate(parsed);
        for (String w : validationResult.getWarnings()) resultBuilder.addWarning(w);
        for (String e : validationResult.getErrors()) resultBuilder.addError(e);

        if (!validationResult.isValid()) {
            return resultBuilder.status(ParseStatus.INVALID).build();
        }

        // Step 9: Check for failed transactions.
        // If the SMS says "transaction failed", we record it as FAILED_TRANSACTION
        // so it doesn't affect user balances.
        if ("FAILED".equals(parsed.getTransactionStatus())) {
            return resultBuilder
                    .status(ParseStatus.FAILED_TRANSACTION)
                    .confidence(parsed.getOverallConfidence())
                    .build();
        }

        // Step 10: Build domain Transaction.
        // Finally, map our intermediate `ParsedTransaction` to the application's core `Transaction` entity.
        Transaction transaction = buildTransaction(parsed);
        resultBuilder.transaction(transaction)
                .confidence(parsed.getOverallConfidence())
                .status(validationResult.getWarnings().isEmpty()
                        ? ParseStatus.SUCCESS : ParseStatus.PARTIAL_SUCCESS);

        return resultBuilder.build();
    }

    /**
     * Backward-compatible method matching the old SMSParser.parseSMS() signature.
     * Returns a Transaction directly (null if parsing failed).
     */
    public Transaction parseSMS(String sender, String body) {
        ParseResult result = parse(sender, body, System.currentTimeMillis());
        return result.isSuccess() ? result.getTransaction() : null;
    }

    // ── Bank-specific parsing ────────────────────────────────────────────────

    private ParsedTransaction tryBankSpecificParsing(SMSPreprocessor.PreprocessedSMS sms,
                                                      String bankName,
                                                      ParseResult.Builder resultBuilder) {
        if (bankName == null) return null;

        for (BankConfig config : bankConfigProvider.getAllConfigs()) {
            String configBank = bankNormalizer.normalize(config.getBankName());
            if (!bankName.equalsIgnoreCase(configBank)) continue;

            for (BankConfig.PatternConfig pc : config.getPatterns()) {
                try {
                    Pattern pattern = Pattern.compile(pc.getRegex(), Pattern.CASE_INSENSITIVE);
                    Matcher m = pattern.matcher(sms.getNormalizedMessage());
                    if (!m.find()) continue;

                    ParsedTransaction parsed = new ParsedTransaction();
                    parsed.setSmsTimestamp(sms.getTimestamp());
                    parsed.setSenderAddress(sms.getSenderAddress());
                    parsed.setRawMessage(sms.getRawMessage());

                    // Extract amount
                    double amount = safeGroupDouble(m, pc.getAmountGroup());
                    if (amount <= 0) continue;
                    parsed.setAmount(amount);
                    parsed.setAmountConfidence(0.95);

                    // Extract type from config
                    parsed.setTransactionType(pc.getType());
                    parsed.setTransactionTypeConfidence(0.95);

                    // Extract receiver/merchant
                    String receiver = safeGroupString(m, pc.getReceiverGroup());
                    if (!receiver.isEmpty()) {
                        parsed.setMerchant(receiver);
                        parsed.setMerchantConfidence(0.90);
                    }

                    // Extract UPI ID
                    String upi = safeGroupString(m, pc.getUpiGroup());
                    if (!upi.isEmpty()) parsed.setUpiId(upi);

                    // Extract account
                    String account = safeGroupString(m, pc.getAccountGroup());
                    if (!account.isEmpty()) parsed.setAccountSuffix(account);

                    // Source type from config
                    parsed.setSourceType(pc.getSourceType());
                    parsed.setSourceTypeConfidence(0.95);

                    resultBuilder.matchedPattern(config.getBankName() + ":" + pc.getName());
                    return parsed;

                } catch (Exception ignored) {}
            }
        }
        return null;
    }

    // ── Generic extraction ───────────────────────────────────────────────────

    private ParsedTransaction genericExtract(SMSPreprocessor.PreprocessedSMS sms) {
        ParsedTransaction parsed = new ParsedTransaction();
        parsed.setSmsTimestamp(sms.getTimestamp());
        parsed.setSenderAddress(sms.getSenderAddress());
        parsed.setRawMessage(sms.getRawMessage());

        // Amount
        ExtractionResult<Double> amountResult = amountExtractor.extract(sms.getNormalizedMessage());
        if (amountResult.isPresent()) {
            parsed.setAmount(amountResult.getValue());
            parsed.setAmountConfidence(amountResult.getConfidence());
        }

        // Transaction type
        ExtractionResult<String> typeResult = typeExtractor.extract(
                sms.getNormalizedMessage(), sms.getLowercaseMessage());
        if (typeResult.isPresent()) {
            parsed.setTransactionType(typeResult.getValue());
            parsed.setTransactionTypeConfidence(typeResult.getConfidence());
        }

        return parsed;
    }

    // ── Enrichment (runs for both bank-specific and generic) ─────────────────

    private void enrichParsedTransaction(ParsedTransaction parsed, SMSPreprocessor.PreprocessedSMS sms) {
        // Merchant (if not already set by bank-specific)
        if (parsed.getMerchant() == null || parsed.getMerchant().isEmpty()) {
            ExtractionResult<String> merchantResult = merchantExtractor.extract(
                    sms.getNormalizedMessage(), parsed.getBankName());
            if (merchantResult.isPresent()) {
                parsed.setMerchant(merchantResult.getValue());
                parsed.setMerchantConfidence(merchantResult.getConfidence());
            }
        }

        // Account suffix (if not already set)
        if (parsed.getAccountSuffix() == null) {
            ExtractionResult<String> accountResult = accountExtractor.extract(sms.getNormalizedMessage());
            if (accountResult.isPresent()) {
                parsed.setAccountSuffix(accountResult.getValue());
            }
        }

        // Transfer accounts
        if ("TRANSFER".equals(parsed.getTransactionType())) {
            String[] transferAccounts = accountExtractor.extractTransferAccounts(sms.getNormalizedMessage());
            if (transferAccounts != null) {
                parsed.setFromAccount(transferAccounts[0]);
                parsed.setToAccount(transferAccounts[1]);
            }
        }

        // UPI / Reference ID
        if (parsed.getUpiId() == null || parsed.getUpiId().isEmpty()) {
            ExtractionResult<String> refResult = upiExtractor.extractReferenceId(sms.getNormalizedMessage());
            if (refResult.isPresent()) parsed.setReferenceId(refResult.getValue());

            ExtractionResult<String> vpaResult = upiExtractor.extractVpa(sms.getNormalizedMessage());
            if (vpaResult.isPresent()) parsed.setUpiId(vpaResult.getValue());
        } else {
            // If UPI ID was set by bank config, also try to get a reference ID
            ExtractionResult<String> refResult = upiExtractor.extractReferenceId(sms.getNormalizedMessage());
            if (refResult.isPresent()) parsed.setReferenceId(refResult.getValue());
        }

        // Source type (if not already set)
        if (parsed.getSourceType() == null) {
            ExtractionResult<String> sourceResult = sourceTypeExtractor.extract(
                    sms.getNormalizedMessage(), sms.getLowercaseMessage());
            if (sourceResult.isPresent()) {
                parsed.setSourceType(sourceResult.getValue());
                parsed.setSourceTypeConfidence(sourceResult.getConfidence());
            }
        }

        // Transaction status
        ExtractionResult<String> statusResult = statusExtractor.extract(
                sms.getNormalizedMessage(), sms.getLowercaseMessage());
        if (statusResult.isPresent()) {
            parsed.setTransactionStatus(statusResult.getValue());
            parsed.setTransactionStatusConfidence(statusResult.getConfidence());
        }

        // Date
        ExtractionResult<Long> dateResult = dateExtractor.extract(
                sms.getNormalizedMessage(), sms.getTimestamp());
        if (dateResult.isPresent()) {
            parsed.setParsedDate(dateResult.getValue());
        }
    }

    // ── Normalization ────────────────────────────────────────────────────────

    private void normalizeParsedTransaction(ParsedTransaction parsed) {
        if (parsed.getBankName() != null) {
            parsed.setBankName(bankNormalizer.normalize(parsed.getBankName()));
        }
        if (parsed.getMerchant() != null) {
            parsed.setMerchant(merchantNormalizer.normalize(parsed.getMerchant()));
        }
    }

    // ── Build domain Transaction ─────────────────────────────────────────────

    private Transaction buildTransaction(ParsedTransaction parsed) {
        String bankName = parsed.getBankName() != null ? parsed.getBankName() : "Unknown";
        String sourceType = parsed.getSourceType() != null ? parsed.getSourceType() : "Account";
        String source = bankName + " (" + sourceType + ")";
        String upiId = parsed.getUpiId() != null ? parsed.getUpiId() : "";
        if (upiId.isEmpty() && parsed.getReferenceId() != null) {
            upiId = parsed.getReferenceId();
        }
        String merchant = parsed.getMerchant() != null ? parsed.getMerchant() : "";
        String type = parsed.getTransactionType() != null ? parsed.getTransactionType() : "EXPENSE";

        // For transfers, set category to "Transfer"
        String category = "TRANSFER".equals(type) ? "Transfer" : "Other";

        return new Transaction(
                0,
                parsed.getAmount() != null ? parsed.getAmount() : 0,
                category,
                parsed.getRawMessage(),
                type,
                parsed.getEffectiveDate(),
                source,
                parsed.getSenderAddress(),
                upiId,
                merchant,
                bankName,
                sourceType,
                parsed.getFromAccount(),
                parsed.getToAccount(),
                parsed.getFees()
        );
    }

    // ── Regex helpers ────────────────────────────────────────────────────────

    private double safeGroupDouble(Matcher m, int group) {
        if (group <= 0) return 0;
        try {
            String v = m.group(group);
            return v != null ? Double.parseDouble(v.replace(",", "")) : 0;
        } catch (Exception e) { return 0; }
    }

    private String safeGroupString(Matcher m, int group) {
        if (group <= 0) return "";
        try {
            String v = m.group(group);
            return v != null ? v.trim() : "";
        } catch (Exception e) { return ""; }
    }
}
