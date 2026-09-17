package com.example.spendtracker.data.sms.detection;

import com.example.spendtracker.data.sms.extraction.AmountExtractor;
import com.example.spendtracker.data.sms.extraction.TransactionText;
import com.example.spendtracker.data.sms.model.DetectionResult;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Requires an actual monetary action; sender category alone cannot establish payment. */
public class TransactionDetector {
    private static final Pattern NON_POSTED = Pattern.compile(
            "(?i)\\b(?:minimum\\s+due|total\\s+amount\\s+due|payment\\s+due|bill\\s+(?:is\\s+)?due|"
            + "not\\s+eligible\\s+for\\s+refund|pre[- ]?approved|apply\\s+now|"
            + "every\\s+(?:Rs\\.?|INR)|earn\\s+.*reward|request\\s+for\\s+money)\\b");
    private static final Pattern MONEY_TOKEN = Pattern.compile("(?i)(?:\\bINR|\\bRs\\.?|₹)\\s*[:.]?\\s*\\d");

    public DetectionResult detect(String normalized, String lowercase) {
        List<String> signals = new ArrayList<>();
        String text = TransactionText.posted(normalized);
        if (TransactionText.isAuthorization(normalized) || NON_POSTED.matcher(text).find()) {
            signals.add("reject:authorization-or-non-posted");
            return new DetectionResult(false, 0, signals);
        }
        boolean action = TransactionText.hasAction(text);
        // Ambiguous financial fields still describe a transaction; extraction will return INVALID.
        boolean amount = new AmountExtractor().extract(text).isPresent() || MONEY_TOKEN.matcher(text).find();
        if (action) signals.add("keyword:completed-action");
        if (amount) signals.add("money:action-amount");
        return new DetectionResult(action && amount, action && amount ? .85 : .1, signals);
    }
}
