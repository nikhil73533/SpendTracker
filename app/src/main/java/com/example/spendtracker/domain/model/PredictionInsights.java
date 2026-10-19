package com.example.spendtracker.domain.model;

import java.util.List;

/** One vote per active transaction with a recorded original model output.
 * Only explicit final labels are evaluated. The latest user label is compared against the
 * original prediction; automatic assignment, confidence and import approval are not review.
 * Retained/corrected use the evaluated subset as denominator (and therefore sum to 100%).
 */
public final class PredictionInsights {
    public int predicted, evaluated, retained;
    public static PredictionInsights from(List<Transaction> transactions) {
        PredictionInsights result = new PredictionInsights();
        if (transactions == null) return result;
        for (Transaction tx : transactions) {
            Outcome outcome = outcome(tx);
            if (outcome == null) continue;
            result.predicted++;
            if (outcome == Outcome.UNREVIEWED) continue;
            result.evaluated++;
            if (outcome == Outcome.RETAINED) result.retained++;
        }
        return result;
    }
    public enum Outcome { RETAINED, CORRECTED, UNREVIEWED }
    public static Outcome outcome(Transaction tx) {
        if (!"ACTIVE".equals(tx.getStatus()) || !known(tx.getPredictedCategory())) return null;
        if (!known(tx.getPredictionFinalCategory())) return Outcome.UNREVIEWED;
        return tx.getPredictedCategory().trim().equalsIgnoreCase(tx.getPredictionFinalCategory().trim())
                ? Outcome.RETAINED : Outcome.CORRECTED;
    }
    private static boolean known(String label) {
        return label != null && !label.trim().isEmpty() && !label.trim().equalsIgnoreCase("PENDING")
                && !label.trim().equalsIgnoreCase("Uncategorized");
    }
    public double accuracy() { return evaluated == 0 ? Double.NaN : retained * 100d / evaluated; }
    public double correctedPercent() { return evaluated == 0 ? Double.NaN : (evaluated - retained) * 100d / evaluated; }
}
