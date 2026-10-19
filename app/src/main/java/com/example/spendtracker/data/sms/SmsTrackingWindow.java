package com.example.spendtracker.data.sms;

import android.content.SharedPreferences;

/** A durable floor prevents first-run recovery and its overlap from importing old SMS. */
public final class SmsTrackingWindow {
    private SmsTrackingWindow() { }

    public static synchronized boolean initialize(SharedPreferences preferences, long now) {
        if (preferences.contains("tracking_started_at")) return true;
        // Preserve the recovery position on upgrades; fresh installs start at consent time.
        long previousScan = preferences.getLong("last_scan", 0);
        long start = previousScan > 0 ? Math.min(previousScan, now) : now;
        return preferences.edit().putLong("tracking_started_at", start)
                .putLong("last_scan", start).commit();
    }

    public static long lowerBound(SharedPreferences preferences) {
        long start = preferences.getLong("tracking_started_at", Long.MAX_VALUE);
        long last = preferences.getLong("last_scan", start);
        return Math.max(start, last > 60000 ? last - 60000 : 0);
    }
}
