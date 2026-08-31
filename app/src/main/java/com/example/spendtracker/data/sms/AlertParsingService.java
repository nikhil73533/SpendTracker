package com.example.spendtracker.data.sms;

import com.example.spendtracker.data.local.dao.BillAlertDao;
import com.example.spendtracker.data.local.entity.BillAlertEntity;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Service dedicated to identifying repeating SMS patterns and creating proactive bill alerts.
 * This runs independently of the transaction parsing system.
 */
@Singleton
public class AlertParsingService {
    private final BillAlertDao billAlertDao;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    // Regex to match digits (OTP, amounts, IDs) to create a generic template
    private static final Pattern DIGIT_PATTERN = Pattern.compile("\\d+");
    // Regex to match common amount patterns like Rs. 500 or ₹ 1000
    private static final Pattern AMOUNT_PATTERN = Pattern.compile("(?:Rs\\.?|INR|₹)\\s*([\\d,]+(?:\\.\\d{2})?)", Pattern.CASE_INSENSITIVE);

    @Inject
    public AlertParsingService(BillAlertDao billAlertDao) {
        this.billAlertDao = billAlertDao;
    }

    /**
     * Processes every incoming message to check for repeating patterns.
     */
    public void processMessage(String sender, String body, long timestamp) {
        if (body == null || body.isBlank() || sender == null) return;

        executor.execute(() -> {
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

    /**
     * Generates a template by replacing digits with '#'.
     * This helps group similar messages even if they have different OTPs or dates.
     */
    private String generateTemplate(String body) {
        String normalized = body.replaceAll("\\s+", " ").trim().toLowerCase();
        return DIGIT_PATTERN.matcher(normalized).replaceAll("#");
    }

    /**
     * Attempts to extract a monetary amount from the message.
     */
    private double extractAmount(String body) {
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
