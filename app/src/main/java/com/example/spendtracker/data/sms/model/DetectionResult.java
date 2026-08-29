package com.example.spendtracker.data.sms.model;

import java.util.Collections;
import java.util.List;

/**
 * Result of a detection stage (transaction detection, bank identification).
 *
 * <p>Captures a boolean decision, a confidence score, and the list of signals that
 * contributed to the decision. This enables debugging and transparent scoring.
 */
public class DetectionResult {

    private final boolean detected;
    private final double confidence;
    private final List<String> matchedSignals;

    public DetectionResult(boolean detected, double confidence, List<String> matchedSignals) {
        this.detected = detected;
        this.confidence = Math.max(0.0, Math.min(1.0, confidence));
        this.matchedSignals = matchedSignals != null
                ? Collections.unmodifiableList(matchedSignals)
                : Collections.emptyList();
    }

    /** Whether the detection condition was met (e.g. "is transaction"). */
    public boolean isDetected() { return detected; }

    /** Confidence score in [0.0, 1.0]. */
    public double getConfidence() { return confidence; }

    /** Human-readable list of signals that contributed to the decision. */
    public List<String> getMatchedSignals() { return matchedSignals; }

    @Override
    public String toString() {
        return "DetectionResult{detected=" + detected
                + ", confidence=" + String.format("%.2f", confidence)
                + ", signals=" + matchedSignals + '}';
    }
}
