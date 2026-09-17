package com.example.spendtracker.data.sms.extraction;

import com.example.spendtracker.data.sms.model.ExtractionResult;
import java.math.BigDecimal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Selects the action amount, never a balance/limit or an arbitrary reference number. */
public class AmountExtractor {
    private static final String NUMBER = "([0-9][0-9,]*(?:\\.[0-9]+)?)(?![0-9,]|\\.\\d)";
    private static final Pattern CURRENCY = Pattern.compile("(?i)(?:\\bINR|\\bRs\\.?|₹)\\s*[:.]?\\s*" + NUMBER);
    private static final Pattern AFTER_ACTION = Pattern.compile(
            "(?i)\\b(?:debited|credited|deposited|deducted|withdrawn|withdrawal|spent|paid|charged|sent|received|used|"
            + "transferred|refunded|reversed|dr|cr)\\b\\s*(?:(?:by|for|with|of|back|amount)\\s+)*"
            + "(?:(?:INR|Rs\\.?|₹)\\s*[:.]?\\s*)?" + NUMBER);
    private static final Pattern OTHER_FIELD = Pattern.compile(
            "(?i)(?:avl\\.?\\s*(?:bal(?:ance)?|limit)|avail(?:able)?\\s*(?:bal(?:ance)?|limit)|"
            + "(?:closing|opening|current|remaining|ledger)\\s+bal(?:ance)?|\\bbal(?:ance)?|"
            + "(?:credit|available)\\s+limit|(?:total|minimum|amount)\\s+due|outstanding|fee|charges)\\s*[:=.-]?\\s*$");
    private static final Pattern VALID_NUMBER = Pattern.compile(
            "(?:\\d+|\\d{1,3}(?:,\\d{3})+|\\d{1,2}(?:,\\d{2})*,\\d{3})(?:\\.\\d{1,2})?");

    public ExtractionResult<Double> extract(String message) {
        String text = TransactionText.posted(message);
        Double best = null;
        int bestScore = -1;
        boolean conflict = false;
        Matcher contextual = AFTER_ACTION.matcher(text);
        while (contextual.find()) {
            Double value = valid(contextual.group(1));
            if (value == null) continue;
            if (bestScore == 3 && !value.equals(best)) conflict = true;
            else if (bestScore < 3) { best = value; bestScore = 3; }
        }
        Matcher money = CURRENCY.matcher(text);
        while (money.find()) {
            String before = text.substring(Math.max(0, money.start() - 45), money.start());
            if (OTHER_FIELD.matcher(before).find()) continue;
            Double value = valid(money.group(1));
            if (value == null) continue;
            String after = text.substring(money.end(), Math.min(text.length(), money.end() + 55));
            int score = after.matches("(?is)^\\s*(?:(?:has|have|is|was|been)\\s+)*(?:debited|credited|spent|paid|sent|received|withdrawn|deposited|transferred)\\b.*") ? 3 : 1;
            if (score > bestScore) { best = value; bestScore = score; conflict = false; }
            else if (score == bestScore && !value.equals(best)) conflict = true;
        }
        return best == null || conflict ? ExtractionResult.empty() : ExtractionResult.of(best, bestScore == 3 ? .95 : .75);
    }

    private static Double valid(String raw) {
        if (raw == null || !VALID_NUMBER.matcher(raw).matches()) return null;
        double amount = parseAmountString(raw);
        return Double.isFinite(amount) && amount > 0 && amount <= 10_000_000 ? amount : null;
    }
    static double parseAmountString(String raw) {
        return raw == null || raw.isEmpty() ? 0 : new BigDecimal(raw.replace(",", "")).doubleValue();
    }
}
