package com.example.spendtracker.data.sms.normalization;

import java.util.Locale;

/**
 * Normalizes raw bank names to canonical short forms.
 * 
 * Consolidates the normalization logic that was previously duplicated in
 * SMSParser.standardizeBankName() and TransactionRepositoryImpl.standardizeBankName().
 */
public class BankNormalizer {

    /**
     * Normalization table: lowercase keyword → canonical short name.
     * Ordered with more-specific patterns first.
     */
    private static final String[][] NORMALIZATION_TABLE = {
        {"state bank",       "SBI"},
        {"amazon pay",       "AMAZON PAY"},
        {"airtel payments",  "AIRTEL PAYMENTS"},
        {"jio payments",     "JIO PAYMENTS"},
        {"union bank",       "UNION BANK"},
        {"bank of baroda",   "BANK OF BARODA"},
        {"yes bank",         "YES BANK"},
        {"one card",         "ONECARD"},
        {"onecard",          "ONECARD"},
        {"bajaj finance",    "BAJAJ FINANCE"},
        {"au bank",          "AU BANK"},
        {"au small",         "AU BANK"},
        {"idfc first",       "IDFC FIRST"},
        {"icici",            "ICICI"},
        {"hdfc",             "HDFC"},
        {"axis",             "AXIS"},
        {"kotak",            "KOTAK"},
        {"canara",           "CANARA"},
        {"indusind",         "INDUSIND"},
        {"federal",          "FEDERAL"},
        {"paytm",            "PAYTM"},
        {"phonepe",          "PHONEPE"},
        {"sbi",              "SBI"},
        {"pnb",              "PNB"},
        {"bob",              "BANK OF BARODA"},
        {"yesb",             "YES BANK"},
        {"idbi",             "IDBI"},
        {"rbl",              "RBL"},
        {"slice",            "Slice"},
        {"navi",             "Navi"},
    };

    /**
     * Normalizes a raw bank name to its canonical short form.
     *
     * @param raw The raw bank name string
     * @return The normalized canonical name, or the trimmed input if no mapping found
     */
    public String normalize(String raw) {
        if (raw == null || raw.trim().isEmpty()) return raw;
        String lower = raw.trim().toLowerCase(Locale.ROOT);
        for (String[] entry : NORMALIZATION_TABLE) {
            if (lower.contains(entry[0])) return entry[1];
        }
        return raw.trim().toUpperCase(Locale.ROOT);
    }
}
