package com.example.spendtracker.util;

import android.content.Context;
import android.content.SharedPreferences;

/** User-configured bank/app limits. NPCI does not publish one universal daily or monthly UPI limit. */
public final class UpiLimitPreferences {
    private static final String FILE = "upi_limit_preferences";
    private static final String DAILY = "daily_limit";
    private static final String MONTHLY = "monthly_limit";

    private UpiLimitPreferences() {}

    public static double dailyLimit(Context context) { return value(context, DAILY); }
    public static double monthlyLimit(Context context) { return value(context, MONTHLY); }

    public static void save(Context context, double daily, double monthly) {
        prefs(context).edit().putFloat(DAILY, safe(daily)).putFloat(MONTHLY, safe(monthly)).apply();
    }

    private static double value(Context context, String key) { return Math.max(0, prefs(context).getFloat(key, 0)); }
    private static float safe(double value) {
        return Double.isFinite(value) && value > 0 && value <= Float.MAX_VALUE ? (float) value : 0f;
    }
    private static SharedPreferences prefs(Context context) { return context.getSharedPreferences(FILE, Context.MODE_PRIVATE); }
}
