package com.example.spendtracker.data.sms;

import android.content.Context;
import com.example.spendtracker.data.local.dao.BillAlertDao;
import com.example.spendtracker.data.local.entity.BillAlertEntity;
import com.example.spendtracker.util.AppNotifications;
import com.example.spendtracker.util.BillReminderSchedule;
import dagger.hilt.android.qualifiers.ApplicationContext;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.*;

/** Persists bills before the SMS receiver finishes; mutations must run off the UI thread. */
@Singleton
public class AlertParsingService {
    private final BillAlertDao dao;
    private final Context context;
    private final Runnable scheduleCheck;
    private final BillMessageParser parser = new BillMessageParser();

    @Inject public AlertParsingService(BillAlertDao dao, @ApplicationContext Context context) {
        this(dao, context, () -> BillReminderWorker.checkNow(context));
    }

    AlertParsingService(BillAlertDao dao, Context context, Runnable scheduleCheck) {
        this.dao = dao;
        this.context = context;
        this.scheduleCheck = scheduleCheck;
    }

    public synchronized void processMessage(String sender, String body, long timestamp) {
        if (sender == null || body == null || body.trim().isEmpty()) return;
        BillMessageParser.Result parsed = parser.parse(body, timestamp, ZoneId.systemDefault());
        if (!parsed.isBill) return;
        saveDetected(sender, body, timestamp, parsed.amount, parsed.dueDate, parsed.dueMinuteOfDay);
    }

    private void saveDetected(String sender, String body, long timestamp, double amount, LocalDate due, int dueMinuteOfDay) {
        // Preserve account/reference digits so bills for two cards never collapse together.
        // Exact-message deduplication is intentionally conservative; unrelated reminders are not merged.
        String template = body.replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);
        long day = due == null ? 0 : due.toEpochDay();
        BillAlertEntity existing = dao.findBill(sender, template, day);
        if (existing != null) {
            // Never reopen paid/dismissed bills when the same reminder arrives again.
            existing.occurrenceCount++;
            existing.lastSeen = timestamp;
            existing.lastMessage = body;
            if (amount > 0) existing.amount = amount;
            if (dueMinuteOfDay >= 0) existing.dueMinuteOfDay = dueMinuteOfDay;
            dao.update(existing);
        } else {
            BillAlertEntity alert = new BillAlertEntity(sender, template, body, 1, timestamp, amount);
            alert.createdAt = System.currentTimeMillis();
            alert.dueEpochDay = day;
            alert.dueMinuteOfDay = dueMinuteOfDay;
            dao.insert(alert);
        }
        scheduleCheck.run();
    }

    public synchronized void saveReviewed(int id, String sender, String body, double amount, LocalDate due, int dueMinuteOfDay) {
        if (sender == null || sender.trim().isEmpty() || due == null || !Double.isFinite(amount) || amount <= 0)
            throw new IllegalArgumentException("Name, positive amount and due date are required");
        if (id == 0) {
            saveDetected(sender.trim(), body, System.currentTimeMillis(), amount, due, dueMinuteOfDay);
            return;
        }
        BillAlertEntity alert = dao.getById(id);
        if (alert == null || alert.isResolved) return;
        if (alert.dueEpochDay != due.toEpochDay()) alert.lastNotifiedAt = 0;
        alert.sender = sender.trim();
        alert.amount = amount;
        alert.dueEpochDay = due.toEpochDay();
        alert.dueMinuteOfDay = dueMinuteOfDay;
        alert.lastMessage = body;
        if (alert.createdAt == 0) alert.createdAt = System.currentTimeMillis();
        dao.update(alert);
        AppNotifications.cancelBill(context, id);
        scheduleCheck.run();
    }

    public synchronized void resolve(int id, boolean delete) {
        if (delete) dao.delete(id); else dao.resolveAlert(id);
        AppNotifications.cancelBill(context, id);
    }

    public synchronized void notifyDueBills() {
        long now = System.currentTimeMillis();
        ZoneId zone = ZoneId.systemDefault();
        for (BillAlertEntity bill : dao.getActiveAlertsSync()) {
            if (bill.amount <= 0 || bill.dueEpochDay == 0) continue;
            long next = BillReminderSchedule.next(bill.dueEpochDay, bill.dueMinuteOfDay, bill.lastNotifiedAt, now, zone);
            if (next == 0 || next > now) continue;
            String date = LocalDate.ofEpochDay(bill.dueEpochDay).format(DateTimeFormatter.ofPattern("d MMM uuuu"));
            String body = String.format(Locale.getDefault(), "₹%.2f due %s. Mark paid in Bill Alerts to stop reminders.", bill.amount, date);
            if (AppNotifications.post(context, AppNotifications.BILLS, bill.id, "Bill due: " + bill.sender, body)) {
                bill.lastNotifiedAt = now;
                dao.update(bill);
            }
        }
    }

    // Retained preferences for compatibility. Keywords alone no longer fire unsolicited alerts.
    public void addCustomKeyword(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) return;
        Set<String> keywords = getCustomKeywords();
        keywords.add(keyword.trim().toLowerCase(Locale.ROOT));
        context.getSharedPreferences("alert_keywords", Context.MODE_PRIVATE).edit()
                .putStringSet("custom_keywords", keywords).apply();
    }
    public void removeCustomKeyword(String keyword) {
        if (keyword == null) return;
        Set<String> keywords = getCustomKeywords();
        keywords.remove(keyword.trim().toLowerCase(Locale.ROOT));
        context.getSharedPreferences("alert_keywords", Context.MODE_PRIVATE).edit()
                .putStringSet("custom_keywords", keywords).apply();
    }
    public Set<String> getCustomKeywords() {
        Set<String> stored = context.getSharedPreferences("alert_keywords", Context.MODE_PRIVATE)
                .getStringSet("custom_keywords", null);
        return stored == null ? new HashSet<>() : new HashSet<>(stored);
    }
    String generateTemplate(String body) {
        return body.replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT).replaceAll("\\d+", "#");
    }
    double extractAmount(String body) {
        Matcher match = Pattern.compile("(?:Rs\\.?|INR|₹)\\s*([\\d,]+(?:\\.\\d{2})?)", Pattern.CASE_INSENSITIVE).matcher(body);
        try { return match.find() ? Double.parseDouble(match.group(1).replace(",", "")) : 0; }
        catch (NumberFormatException ignored) { return 0; }
    }
}
