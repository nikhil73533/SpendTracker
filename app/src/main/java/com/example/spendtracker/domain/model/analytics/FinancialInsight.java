package com.example.spendtracker.domain.model.analytics;

/**
 * A generated financial insight with priority level and type classification.
 */
public class FinancialInsight {
    public enum Priority { HIGH, MEDIUM, LOW }
    public enum InsightType {
        SPENDING_INCREASE, SPENDING_DECREASE,
        CATEGORY_SPIKE, CATEGORY_DROP,
        HIGH_FREQUENCY, LOW_FREQUENCY,
        VELOCITY_WARNING, VELOCITY_GOOD,
        CONCENTRATION, SAVINGS,
        UNUSUAL_TRANSACTION, PATTERN,
        GENERAL
    }

    private final String message;
    private final Priority priority;
    private final InsightType type;
    private final double impactValue; // monetary or percentage impact

    public FinancialInsight(String message, Priority priority, InsightType type, double impactValue) {
        this.message = message;
        this.priority = priority;
        this.type = type;
        this.impactValue = impactValue;
    }

    public String getMessage() { return message; }
    public Priority getPriority() { return priority; }
    public InsightType getType() { return type; }
    public double getImpactValue() { return impactValue; }
}
