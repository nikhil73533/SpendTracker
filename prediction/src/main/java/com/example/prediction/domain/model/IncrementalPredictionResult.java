package com.example.prediction.domain.model;

import java.util.Map;

/**
 * Result of a category prediction.
 */
public class IncrementalPredictionResult {

    public enum Source {
        TRANSFER_RULE,      // Forced: transaction is a TRANSFER
        MERCHANT_HISTORY,   // High-confidence merchant memory
        MERCHANT_CONTEXT,   // Merchant + time context
        ONLINE_TREE,        // Hoeffding Tree prediction
        TOKEN_MATCH,        // Token/n-gram match
        GLOBAL_PRIOR,       // Global category distribution fallback
        ENSEMBLE            // Weighted combination of signals
    }

    private final String categoryId;
    private final double confidence;
    private final Source source;
    private final Map<String, Double> alternatives;
    private final boolean needsUserConfirmation;

    public IncrementalPredictionResult(
            String categoryId,
            double confidence,
            Source source,
            Map<String, Double> alternatives,
            boolean needsUserConfirmation) {
        this.categoryId = categoryId;
        this.confidence = confidence;
        this.source = source;
        this.alternatives = alternatives;
        this.needsUserConfirmation = needsUserConfirmation;
    }

    public String getCategoryId()              { return categoryId; }
    public double getConfidence()              { return confidence; }
    public Source getSource()                  { return source; }
    public Map<String, Double> getAlternatives() { return alternatives; }
    public boolean needsUserConfirmation()     { return needsUserConfirmation; }

    /** Convenience: the category display name (same as id in this implementation). */
    public String getCategory() { return categoryId; }
}
