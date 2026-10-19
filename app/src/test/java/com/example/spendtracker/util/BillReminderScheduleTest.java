package com.example.spendtracker.util;

import org.junit.Test;
import java.time.*;
import static org.junit.Assert.*;

public class BillReminderScheduleTest {
    private final ZoneId zone = ZoneId.of("Asia/Kolkata");
    private long at(String time) { return LocalDateTime.parse(time).atZone(zone).toInstant().toEpochMilli(); }
    private final long due = LocalDate.of(2026, 9, 25).toEpochDay();
    @Test public void startsTwoDaysBeforeAtNine() {
        assertEquals(at("2026-09-23T09:00"), BillReminderSchedule.next(due, 0, at("2026-09-18T12:00"), zone));
    }
    @Test public void lateArrivalCatchesUpTodayNotEachMissedDay() {
        assertEquals(at("2026-09-24T09:00"), BillReminderSchedule.next(due, 0, at("2026-09-24T17:00"), zone));
    }
    @Test public void alreadyNotifiedTodaySchedulesTomorrow() {
        assertEquals(at("2026-09-25T09:00"), BillReminderSchedule.next(due, at("2026-09-24T10:00"), at("2026-09-24T11:00"), zone));
    }
    @Test public void stopsAfterDueDateReminder() {
        assertEquals(0, BillReminderSchedule.next(due, at("2026-09-25T09:01"), at("2026-09-25T12:00"), zone));
    }
    @Test public void neverNotifiesOverdueBills() {
        assertEquals(0, BillReminderSchedule.next(due, 0, at("2026-09-26T00:01"), zone));
    }
    @Test public void missingDateDoesNotSchedule() {
        assertEquals(0, BillReminderSchedule.next(0, 0, at("2026-09-18T12:00"), zone));
    }
    @Test public void progressClampedAndNoDivideByZero() {
        assertEquals(0, BillReminderSchedule.progress(100, 200, 50));
        assertEquals(50, BillReminderSchedule.progress(100, 200, 150));
        assertEquals(100, BillReminderSchedule.progress(100, 200, 250));
        assertEquals(100, BillReminderSchedule.progress(100, 100, 100));
        assertEquals(0, BillReminderSchedule.progress(100, 0, 250));
    }
    @Test public void daylightSavingUsesCalendarDays() {
        ZoneId ny = ZoneId.of("America/New_York");
        LocalDate due = LocalDate.of(2026, 11, 2);
        long now = LocalDate.of(2026, 10, 30).atStartOfDay(ny).toInstant().toEpochMilli();
        long next = BillReminderSchedule.next(due.toEpochDay(), 0, now, ny);
        assertEquals(due.minusDays(2).atTime(9, 0).atZone(ny).toInstant().toEpochMilli(), next);
    }
}
