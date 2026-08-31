package com.example.spendtracker.data.sms;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import com.example.spendtracker.R;
import com.example.spendtracker.data.local.dao.BillAlertDao;
import com.example.spendtracker.data.local.entity.BillAlertEntity;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.hilt.android.qualifiers.ApplicationContext;

/**
 * Service dedicated to identifying repeating SMS patterns and creating proactive bill alerts.
 * This runs independently of the transaction parsing system.
 *
 * <p>Also supports user-defined custom alert keywords stored in SharedPreferences
 * (avoiding any database schema changes). When a matching keyword is detected in an
 * incoming message, a system notification is fired proactively.
 */
@Singleton
public class AlertParsingService {
    private static final String TAG = "AlertParsingService";
    private static final String PREFS_NAME = "alert_keywords";
    private static final String KEY_KEYWORDS = "custom_keywords";
    private static final String CHANNEL_ID = "bill_alert_channel";

    private final BillAlertDao billAlertDao;
    private final Context context;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    // Regex to match digits (OTP, amounts, IDs) to create a generic template
    private static final Pattern DIGIT_PATTERN = Pattern.compile("\\d+");
    // Regex to match common amount patterns like Rs. 500 or ₹ 1000
    private static final Pattern AMOUNT_PATTERN = Pattern.compile("(?:Rs\\.?|INR|₹)\\s*([\\d,]+(?:\\.\\d{2})?)", Pattern.CASE_INSENSITIVE);

    @Inject
    public AlertParsingService(BillAlertDao billAlertDao, @ApplicationContext Context context) {
        this.billAlertDao = billAlertDao;
        this.context = context;
        createNotificationChannel();
    }

    /**
     * Processes every incoming message to check for repeating patterns
     * and user-defined custom alert keywords.
     */
    public void processMessage(String sender, String body, long timestamp) {
        if (body == null || body.isBlank() || sender == null) return;

        executor.execute(() -> {
            // 1. Custom keyword matching — fires notification immediately
            checkCustomKeywords(sender, body);

            // 2. Repeating pattern detection — stores in bill_alerts table
            String template = generateTemplate(body);
            double amount = extractAmount(body);

            BillAlertEntity existing = billAlertDao.findByTemplate(template, sender);
            if (existing != null) {
                existing.occurrenceCount++;
                existing.lastSeen = timestamp;
                existing.lastMessage = body;
                if (amount > 0) existing.amount = amount;
                billAlertDao.update(existing);
            } else {
                BillAlertEntity newAlert = new BillAlertEntity(
                        sender,
                        template,
                        body,
                        1,
                        timestamp,
                        amount
                );
                billAlertDao.insert(newAlert);
            }
        });
    }

    // ── Custom Alert Keyword Management ──────────────────────────────────────

    /**
     * Adds a user-defined alert keyword. When any incoming message contains this
     * keyword (case-insensitive), a notification is triggered.
     */
    public void addCustomKeyword(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) return;
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        Set<String> keywords = new HashSet<>(getCustomKeywords());
        keywords.add(keyword.trim().toLowerCase());
        prefs.edit().putStringSet(KEY_KEYWORDS, keywords).apply();
    }

    /**
     * Removes a user-defined alert keyword.
     */
    public void removeCustomKeyword(String keyword) {
        if (keyword == null) return;
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        Set<String> keywords = new HashSet<>(getCustomKeywords());
        keywords.remove(keyword.trim().toLowerCase());
        prefs.edit().putStringSet(KEY_KEYWORDS, keywords).apply();
    }

    /**
     * Returns all user-defined alert keywords.
     */
    public Set<String> getCustomKeywords() {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        Set<String> stored = prefs.getStringSet(KEY_KEYWORDS, null);
        return stored != null ? new HashSet<>(stored) : new HashSet<>();
    }

    /**
     * Checks the incoming message body against all user-defined alert keywords.
     * If a match is found, fires a system notification.
     */
    private void checkCustomKeywords(String sender, String body) {
        Set<String> keywords = getCustomKeywords();
        if (keywords.isEmpty()) return;

        String bodyLower = body.toLowerCase();
        for (String keyword : keywords) {
            if (bodyLower.contains(keyword)) {
                fireAlertNotification(sender, keyword, body);
                break; // One notification per message
            }
        }
    }

    /**
     * Fires a system notification for a matched custom alert keyword.
     */
    private void fireAlertNotification(String sender, String keyword, String body) {
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle("Bill Alert: \"" + keyword + "\" detected")
                .setContentText("From " + sender + ": " + body.substring(0, Math.min(body.length(), 80)))
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true);

        nm.notify((int) System.currentTimeMillis(), builder.build());
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Bill Alerts",
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Alerts for recurring bills and custom keyword matches");
            NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) nm.createNotificationChannel(channel);
        }
    }

    // ── Template & Extraction Logic ─────────────────────────────────────────

    /**
     * Generates a template by replacing digits with '#'.
     * This helps group similar messages even if they have different OTPs or dates.
     */
    String generateTemplate(String body) {
        String normalized = body.replaceAll("\\s+", " ").trim().toLowerCase();
        return DIGIT_PATTERN.matcher(normalized).replaceAll("#");
    }

    /**
     * Attempts to extract a monetary amount from the message.
     */
    double extractAmount(String body) {
        Matcher matcher = AMOUNT_PATTERN.matcher(body);
        if (matcher.find()) {
            try {
                String group = matcher.group(1);
                if (group != null) {
                    String amountStr = group.replace(",", "");
                    return Double.parseDouble(amountStr);
                }
            } catch (Exception ignored) {}
        }
        return 0.0;
    }
}

