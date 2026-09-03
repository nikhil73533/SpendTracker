package com.example.spendtracker.domain.model.analytics;

/**
 * Generic data point for time-series charts.
 * Used for monthly trends, income vs expense, rolling averages, etc.
 */
public class TimeSeriesPoint {
    private final String label;
    private final double value;
    private final double secondaryValue;
    private final double tertiaryValue;

    public TimeSeriesPoint(String label, double value) {
        this(label, value, 0, 0);
    }

    public TimeSeriesPoint(String label, double value, double secondaryValue) {
        this(label, value, secondaryValue, 0);
    }

    public TimeSeriesPoint(String label, double value, double secondaryValue, double tertiaryValue) {
        this.label = label;
        this.value = value;
        this.secondaryValue = secondaryValue;
        this.tertiaryValue = tertiaryValue;
    }

    public String getLabel() { return label; }
    /** Primary value (e.g., expense amount or transaction count). */
    public double getValue() { return value; }
    /** Secondary value (e.g., income amount for income vs expense chart). */
    public double getSecondaryValue() { return secondaryValue; }
    /** Tertiary value (e.g., net balance for income vs expense chart). */
    public double getTertiaryValue() { return tertiaryValue; }
}
