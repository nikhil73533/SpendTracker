package com.example.spendtracker.util;

import java.time.*;

/** Calendar-day scheduling, not fixed 24-hour arithmetic (safe across DST changes). */
public final class BillReminderSchedule {
    private BillReminderSchedule() {}

    /** Next target at 9am, starting two days before due date; late arrivals catch up once today. */
    public static long next(long dueEpochDay, long lastNotified, long now, ZoneId zone) {
        return next(dueEpochDay, -1, lastNotified, now, zone);
    }

    /**
     * Keeps early reminders at 9am, then respects a verified deadline time on the due day.
     * A missing time deliberately retains the established 9am reminder behaviour.
     */
    public static long next(long dueEpochDay, int dueMinuteOfDay, long lastNotified, long now, ZoneId zone) {
        if (dueEpochDay == 0) return 0;
        LocalDate due = LocalDate.ofEpochDay(dueEpochDay);
        LocalDate today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate();
        if (today.isAfter(due)) return 0;
        LocalDate candidate = due.minusDays(2);
        if (candidate.isBefore(today)) candidate = today;
        if (lastNotified > 0) {
            LocalDate last = Instant.ofEpochMilli(lastNotified).atZone(zone).toLocalDate();
            if (!candidate.isAfter(last)) candidate = last.plusDays(1);
        }
        if (candidate.isAfter(due)) return 0;
        LocalTime time = candidate.equals(due) && dueMinuteOfDay >= 0
                ? LocalTime.of(dueMinuteOfDay / 60, dueMinuteOfDay % 60) : LocalTime.of(9, 0);
        return candidate.atTime(time).atZone(zone).toInstant().toEpochMilli();
    }

    public static int progress(long createdAt, long next, long now) {
        if (next == 0) return 0;
        if (next <= createdAt || now >= next) return 100;
        return (int) Math.max(0, Math.min(100, 100.0 * (now - createdAt) / (next - createdAt)));
    }
}
