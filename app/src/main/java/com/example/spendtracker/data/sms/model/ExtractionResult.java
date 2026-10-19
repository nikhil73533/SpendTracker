package com.example.spendtracker.data.sms.model;

/**
 * Result of a single field extraction, pairing the extracted value with a confidence score.
 *
 * @param <T> The type of the extracted value (e.g. {@code Double} for amount, {@code String} for merchant).
 */
public class ExtractionResult<T> {

    private final T value;
    private final double confidence;

    private ExtractionResult(T value, double confidence) {
        this.value = value;
        this.confidence = Math.max(0.0, Math.min(1.0, confidence));
    }

    /** Creates a successful extraction result. */
    public static <T> ExtractionResult<T> of(T value, double confidence) {
        return new ExtractionResult<>(value, confidence);
    }

    /** Creates an empty (failed) extraction result. */
    public static <T> ExtractionResult<T> empty() {
        return new ExtractionResult<>(null, 0.0);
    }

    /** The extracted value, or {@code null} if extraction failed. */
    public T getValue() { return value; }

    /** Confidence in [0.0, 1.0]. */
    public double getConfidence() { return confidence; }

    /** Whether a value was successfully extracted. */
    public boolean isPresent() { return value != null; }

    @Override
    public String toString() {
        return "ExtractionResult{value=" + value
                + ", confidence=" + String.format("%.2f", confidence) + '}';
    }
}
