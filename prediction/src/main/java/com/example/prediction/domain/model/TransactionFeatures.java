package com.example.prediction.domain.model;

import java.util.Collections;
import java.util.Map;

/**
 * Immutable feature vector for a transaction.
 * Does NOT include: transaction ID, UPI reference numbers, or any unique identifiers.
 * Those are for deduplication only, not prediction.
 */
public final class TransactionFeatures {

    // ── Amount features ──────────────────────────────────────────────────────
    public final double amount;
    public final double logAmount;     // log1p(amount)

    // ── Temporal features ────────────────────────────────────────────────────
    public final int hour;             // 0-23
    public final int dayOfWeek;        // 1=Sunday … 7=Saturday
    public final boolean weekend;
    public final int timeBucket;       // 0=night,1=morning,2=afternoon,3=evening

    // ── Merchant features ────────────────────────────────────────────────────
    public final String merchantKey;   // normalized merchant key
    public final String bankName;      // canonical bank name

    // ── Transaction dimension ────────────────────────────────────────────────
    public final String transactionSource;    // Account, Credit Card, UPI, Wallet, Manual
    public final String transactionDirection; // INCOME, EXPENSE, TRANSFER

    // ── Merchant memory features ─────────────────────────────────────────────
    public final int merchantTransactionCount;   // how many times this merchant seen
    public final int merchantCategoryCount;      // distinct categories for this merchant
    public final Map<String, Double> merchantCategoryProbabilities;    // category → P(cat|merchant)
    public final Map<String, Double> merchantTimeCategoryProbabilities; // timeBucket+category
    public final Map<String, Double> globalCategoryProbabilities;       // global prior

    public TransactionFeatures(
            double amount,
            double logAmount,
            int hour,
            int dayOfWeek,
            boolean weekend,
            int timeBucket,
            String merchantKey,
            String bankName,
            String transactionSource,
            String transactionDirection,
            int merchantTransactionCount,
            int merchantCategoryCount,
            Map<String, Double> merchantCategoryProbabilities,
            Map<String, Double> merchantTimeCategoryProbabilities,
            Map<String, Double> globalCategoryProbabilities) {
        this.amount = amount;
        this.logAmount = logAmount;
        this.hour = hour;
        this.dayOfWeek = dayOfWeek;
        this.weekend = weekend;
        this.timeBucket = timeBucket;
        this.merchantKey = merchantKey;
        this.bankName = bankName;
        this.transactionSource = transactionSource;
        this.transactionDirection = transactionDirection;
        this.merchantTransactionCount = merchantTransactionCount;
        this.merchantCategoryCount = merchantCategoryCount;
        this.merchantCategoryProbabilities = Collections.unmodifiableMap(merchantCategoryProbabilities);
        this.merchantTimeCategoryProbabilities = Collections.unmodifiableMap(merchantTimeCategoryProbabilities);
        this.globalCategoryProbabilities = Collections.unmodifiableMap(globalCategoryProbabilities);
    }

    /** @return the time bucket (0-3) for the given hour */
    public static int timeBucketFor(int hour) {
        if (hour < 6)  return 0; // night
        if (hour < 12) return 1; // morning
        if (hour < 18) return 2; // afternoon
        return 3;                 // evening
    }
}
