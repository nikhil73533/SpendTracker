package com.example.spendtracker.data.sms.detection;

import com.example.spendtracker.data.sms.model.DetectionResult;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Multi-signal transaction detector.
 *
 * <p>Uses weighted signals across three categories — transaction keywords,
 * monetary indicators, and transaction context — to produce a confidence
 * score for whether an SMS represents a financial transaction.
 *
 * <p>Also applies negative signals for promotional content, loan offers,
 * and balance-only notifications.
 *
 * <p>This class is stateless and thread-safe.
 */
public class TransactionDetector {

    // ── Positive signal patterns ─────────────────────────────────────────────

    /** Transaction action keywords — strongest indicators. */
    private static final PatternWeight[] TRANSACTION_KEYWORDS = {
        pw("\\b(debited|deducted|withdrawn|charged)\\b",             0.35),
        pw("\\b(credited|deposited|received)\\b",                    0.35),
        pw("\\b(spent|paid|purchase)\\b",                            0.30),
        pw("\\b(transferred|transfer|fund\\s+transfer)\\b",          0.30),
        pw("\\b(refund|reversed|cashback\\s+credited)\\b",           0.25),
        pw("\\bdr\\.?\\b",                                           0.20),
        pw("\\bcr\\.?\\b",                                           0.20),
    };

    /** Monetary amount indicators. */
    private static final PatternWeight[] MONETARY_PATTERNS = {
        pw("(?:INR|Rs\\.?|₹)\\s*[0-9,]+(?:\\.[0-9]{1,2})?",         0.30),
        pw("\\bamount\\b.{0,20}[0-9,]+",                            0.15),
    };

    /** Transaction context indicators. */
    private static final PatternWeight[] CONTEXT_PATTERNS = {
        pw("\\b(a/c|acct|account)\\b",                               0.15),
        pw("\\bupi\\b",                                              0.15),
        pw("\\b(vpa|neft|imps|rtgs)\\b",                             0.15),
        pw("\\b(credit\\s+card|debit\\s+card)\\b",                   0.15),
        pw("\\b(ref\\.?\\s*(?:no\\.?)?|txn\\s*(?:id|no|ref))\\b",    0.10),
        pw("\\b(atm|pos)\\b",                                       0.10),
        pw("\\bavl\\.?\\s*bal",                                      0.10),
    };

    // ── Negative signal patterns ─────────────────────────────────────────────

    /** Promotional / non-transactional patterns. */
    private static final PatternWeight[] NEGATIVE_PATTERNS = {
        pw("\\b(apply\\s+now|click\\s+here|visit|download|subscribe)\\b",   -0.35),
        pw("\\b(congratulations|you\\s+have\\s+won|lucky\\s+winner)\\b",    -0.40),
        pw("\\b(pre[- ]?approved|eligible\\s+for|personal\\s+loan)\\b",     -0.35),
        pw("\\b(offer|discount|promo|coupon|deal|sale)\\b",                 -0.20),
        pw("\\b(upgrade|activate|renew|subscribe)\\b",                      -0.15),
        pw("\\b(emi\\s+option|instant\\s+loan|credit\\s+limit.*increase)\\b", -0.30),
        pw("\\bbalance\\b(?!.*(?:debit|credit|spent|paid|transfer))",       -0.10),
    };

    /**
     * Detects whether the SMS is likely a financial transaction.
     *
     * @param normalizedMessage The normalized SMS body
     * @param lowercaseMessage  The lowercase normalized body
     * @return A {@link DetectionResult} with confidence and matched signals
     */
    public DetectionResult detect(String normalizedMessage, String lowercaseMessage) {
        List<String> signals = new ArrayList<>();
        double score = 0.0;

        // Positive signals: Increase confidence if we find transaction-related terms
        
        // 1. Check for action verbs (debited, credited). 
        // We use lowercaseMessage to ensure case-insensitive matching is fast.
        score += checkPatterns(lowercaseMessage, TRANSACTION_KEYWORDS, signals, "keyword");
        
        // 2. Check for currency symbols and numeric amounts.
        // We use normalizedMessage here because currency formats (like Rs.) might be case-sensitive.
        score += checkPatterns(normalizedMessage, MONETARY_PATTERNS, signals, "money");
        
        // 3. Check for banking context terms (a/c, upi, ref no).
        score += checkPatterns(lowercaseMessage, CONTEXT_PATTERNS, signals, "context");

        // Negative signals: Decrease confidence if we find promotional or non-financial terms
        score += checkPatterns(lowercaseMessage, NEGATIVE_PATTERNS, signals, "negative");

        // Clamp the final score to ensure it stays within a valid percentage range [0.0, 1.0]
        score = Math.max(0.0, Math.min(1.0, score));

        // Evaluate the required minimum signals.
        // A high score alone isn't enough; we MUST have at least one explicit action keyword 
        // (e.g., "debited") to prevent false positives on random texts mentioning money.
        boolean hasKeyword = false;
        boolean hasMoney = false;
        for (String s : signals) {
            if (s.startsWith("keyword:")) hasKeyword = true;
            if (s.startsWith("money:")) hasMoney = true;
        }

        // The message is classified as a transaction if it meets our confidence threshold (0.40)
        // AND contains a clear transaction keyword.
        boolean isTransaction = score >= 0.40 && hasKeyword;
        return new DetectionResult(isTransaction, score, signals);
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private double checkPatterns(String text, PatternWeight[] patterns,
                                 List<String> signals, String prefix) {
        double contribution = 0.0;
        for (PatternWeight pw : patterns) {
            if (pw.pattern.matcher(text).find()) {
                contribution += pw.weight;
                signals.add(prefix + ":" + pw.pattern.pattern());
            }
        }
        return contribution;
    }

    private static PatternWeight pw(String regex, double weight) {
        return new PatternWeight(
                Pattern.compile(regex, Pattern.CASE_INSENSITIVE),
                weight
        );
    }

    private static class PatternWeight {
        final Pattern pattern;
        final double weight;

        PatternWeight(Pattern pattern, double weight) {
            this.pattern = pattern;
            this.weight = weight;
        }
    }
}
