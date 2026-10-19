package com.example.spendtracker.domain.model.analytics;

import java.util.Calendar;

/** Adjacent half-open windows; whole calendar months/years retain calendar boundaries. */
public final class ComparisonWindow {
    public final long start;
    public final long end;

    private ComparisonWindow(long start, long end) { this.start = start; this.end = end; }

    public static ComparisonWindow previous(long start, long end, AnalyticsGranularity granularity) {
        if (end <= start) throw new IllegalArgumentException("Empty analytics window");
        Calendar first = Calendar.getInstance();
        first.setTimeInMillis(start);
        Calendar next = (Calendar) first.clone();
        boolean midnight = first.get(Calendar.HOUR_OF_DAY) == 0 && first.get(Calendar.MINUTE) == 0
                && first.get(Calendar.SECOND) == 0 && first.get(Calendar.MILLISECOND) == 0;
        int field = granularity == AnalyticsGranularity.YEAR ? Calendar.YEAR : Calendar.MONTH;
        boolean aligned = midnight && first.get(Calendar.DAY_OF_MONTH) == 1
                && (granularity == AnalyticsGranularity.MONTH
                || (granularity == AnalyticsGranularity.YEAR && first.get(Calendar.MONTH) == Calendar.JANUARY));
        next.add(field, 1);
        if (aligned && end == next.getTimeInMillis()) {
            first.add(field, -1);
            return new ComparisonWindow(first.getTimeInMillis(), start);
        }
        // Custom/partial ranges compare equal elapsed time immediately before this window.
        return new ComparisonWindow(Math.subtractExact(start, Math.subtractExact(end, start)), start);
    }
}
