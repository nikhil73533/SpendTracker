package com.example.spendtracker.data.sms.model;

import com.example.spendtracker.domain.model.Transaction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Final result of the SMS parsing pipeline.
 *
 * <p>Contains the parse status, the built {@link Transaction} (if successful),
 * overall confidence, and diagnostic information for debugging.
 */
public class ParseResult {

    private final ParseStatus status;
    private final Transaction transaction;
    private final double confidence;
    private final String detectedBank;
    private final String matchedPattern;
    private final List<String> warnings;
    private final List<String> errors;

    private ParseResult(Builder builder) {
        this.status = builder.status;
        this.transaction = builder.transaction;
        this.confidence = builder.confidence;
        this.detectedBank = builder.detectedBank;
        this.matchedPattern = builder.matchedPattern;
        this.warnings = Collections.unmodifiableList(builder.warnings);
        this.errors = Collections.unmodifiableList(builder.errors);
    }

    public ParseStatus getStatus() { return status; }
    public Transaction getTransaction() { return transaction; }
    public double getConfidence() { return confidence; }
    public String getDetectedBank() { return detectedBank; }
    public String getMatchedPattern() { return matchedPattern; }
    public List<String> getWarnings() { return warnings; }
    public List<String> getErrors() { return errors; }

    /** Whether a usable transaction was produced. */
    public boolean isSuccess() {
        return status == ParseStatus.SUCCESS || status == ParseStatus.PARTIAL_SUCCESS;
    }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private ParseStatus status = ParseStatus.UNKNOWN_FORMAT;
        private Transaction transaction;
        private double confidence;
        private String detectedBank;
        private String matchedPattern;
        private final List<String> warnings = new ArrayList<>();
        private final List<String> errors = new ArrayList<>();

        public Builder status(ParseStatus status) { this.status = status; return this; }
        public Builder transaction(Transaction t) { this.transaction = t; return this; }
        public Builder confidence(double c) { this.confidence = c; return this; }
        public Builder detectedBank(String bank) { this.detectedBank = bank; return this; }
        public Builder matchedPattern(String pattern) { this.matchedPattern = pattern; return this; }
        public Builder addWarning(String w) { this.warnings.add(w); return this; }
        public Builder addError(String e) { this.errors.add(e); return this; }

        public ParseResult build() { return new ParseResult(this); }
    }

    @Override
    public String toString() {
        return "ParseResult{status=" + status
                + ", confidence=" + String.format("%.2f", confidence)
                + ", bank=" + detectedBank
                + ", pattern=" + matchedPattern
                + ", warnings=" + warnings
                + ", errors=" + errors + '}';
    }
}
