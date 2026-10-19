package com.example.prediction.domain.service;

import com.example.prediction.domain.model.PredictionTransaction;
import com.example.prediction.util.MerchantNormalizer;
import java.util.*;
import java.util.regex.Pattern;

/** Bounded, deterministic features. No account numbers, amounts, dates or bank routing tokens. */
public final class CategoryText {
    private static final Pattern GENERIC = Pattern.compile("\\b(?:merchant|payee|payer|receiver|sender|transfer|unavailable|unspecified|not|none|provided|debit|credit|amount|purchase|successful|hdfc|hdfcbk|icici|icicib|sbi|sbibnk|axis|axisbk|kotak|pnb|canara|indusind|idbi|baroda)\\b");
    private static final Pattern NOISE = Pattern.compile("\\b(?:upi|neft|imps|rtgs|dr|cr|inr|rs|txn|transaction|ref|reference|utr|debited|credited|paid|received|sent|spent|payment|account|acct|bank|balance|avl|available|card|ac|to|from|for|by|on|at|via|with|your|you|the|and|of|is|has|been|a|an|pvt|ltd|limited|india|unknown|null|okaxis|okicici|okhdfcbank|ybl|ibl|paytm|phonepe)\\b");
    private CategoryText() {}
    public static String normalize(String value) {
        if (value == null) return "";
        String bounded = value.substring(0, Math.min(2048, value.length())).toLowerCase(Locale.ROOT);
        return bounded.replaceAll("@[a-z0-9.]+", " ")
                .replaceAll("\\b\\w*\\d\\w*\\b", " ")
                .replaceAll("[^\\p{L}\\p{M}]+", " ").trim().replaceAll(" +", " ");
    }
    public static Set<String> tokens(String value) {
        String clean = GENERIC.matcher(NOISE.matcher(normalize(value)).replaceAll(" ")).replaceAll(" ").trim();
        Set<String> result = new LinkedHashSet<>();
        for (String token : clean.split(" +")) {
            if (token.length() >= 3) result.add(token);
            if (result.size() >= 48) break;
        }
        return result;
    }
    /** Category names may intentionally contain numbers; never merge Work 2025 and Work 2026. */
    public static String categoryKey(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{M}\\p{N}]+", " ").trim();
    }
    public static String features(PredictionTransaction tx) {
        // UPI handles supply identity only when a usable payee is missing.
        return String.join(" ", tokens(tx.merchantName + " " + tx.description + " " +
                (tokens(MerchantNormalizer.normalize(tx.merchantName)).isEmpty() ? tx.upiId : "")));
    }
    public static String merchant(PredictionTransaction tx) {
        String name = MerchantNormalizer.normalize(tx.merchantName);
        if (tokens(name).isEmpty()) name = MerchantNormalizer.normalize(tx.upiId);
        return tokens(name).isEmpty() ? "" : name;
    }
    public static String type(String value) {
        return value == null ? "EXPENSE" : value.trim().toUpperCase(Locale.ROOT);
    }
}
