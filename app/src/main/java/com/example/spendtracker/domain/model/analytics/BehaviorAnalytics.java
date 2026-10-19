package com.example.spendtracker.domain.model.analytics;

import java.util.List;
import java.util.Map;

/**
 * Behavioral spending analytics: day/night, time-of-day, weekday/weekend, day-of-week.
 * Each segment contains count, total, and average.
 */
public class BehaviorAnalytics {

    /** A single segment within a behavioral breakdown (e.g., "Day", "Night", "Monday"). */
    public static class Segment {
        private final String label;
        private final int transactionCount;
        private final double totalAmount;
        private final double averageAmount;

        public Segment(String label, int transactionCount, double totalAmount) {
            this.label = label;
            this.transactionCount = transactionCount;
            this.totalAmount = totalAmount;
            this.averageAmount = transactionCount > 0 ? totalAmount / transactionCount : 0;
        }

        public String getLabel() { return label; }
        public int getTransactionCount() { return transactionCount; }
        public double getTotalAmount() { return totalAmount; }
        public double getAverageAmount() { return averageAmount; }
    }

    private final String analyticsType; // "DAY_NIGHT", "TIME_OF_DAY", "WEEKDAY_WEEKEND", "DAY_OF_WEEK"
    private final List<Segment> segments;
    private final String insight; // e.g., "Weekend spending is 31% higher per day"

    public BehaviorAnalytics(String analyticsType, List<Segment> segments, String insight) {
        this.analyticsType = analyticsType;
        this.segments = segments;
        this.insight = insight;
    }

    public String getAnalyticsType() { return analyticsType; }
    public List<Segment> getSegments() { return segments; }
    public String getInsight() { return insight; }

    /** Returns the segment with the highest total spending. */
    public Segment getHighestSpendingSegment() {
        if (segments == null || segments.isEmpty()) return null;
        Segment highest = segments.get(0);
        for (Segment s : segments) {
            if (s.totalAmount > highest.totalAmount) highest = s;
        }
        return highest;
    }
}
