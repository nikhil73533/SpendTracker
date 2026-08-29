package com.example.spendtracker.data.sms.normalization;

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
        {"amazon pay",       "Amazon Pay"},
        {"airtel payments",  "Airtel Payments"},
        {"jio payments",     "Jio Payments"},
        {"union bank",       "Union Bank"},
        {"bank of baroda",   "Bank of Baroda"},
        {"yes bank",         "Yes Bank"},
        {"one card",         "OneCard"},
        {"onecard",          "OneCard"},
        {"bajaj finance",    "Bajaj Finance"},
        {"au bank",          "AU Bank"},
        {"au small",         "AU Bank"},
        {"idfc first",       "IDFC First"},
        {"icici",            "ICICI"},
        {"hdfc",             "HDFC"},
        {"axis",             "Axis"},
        {"kotak",            "Kotak"},
        {"canara",           "Canara"},
        {"indusind",         "IndusInd"},
        {"federal",          "Federal"},
        {"paytm",            "Paytm"},
        {"phonepe",          "PhonePe"},
        {"sbi",              "SBI"},
        {"pnb",              "PNB"},
        {"bob",              "Bank of Baroda"},
        {"yesb",             "Yes Bank"},
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
        String lower = raw.trim().toLowerCase();
        for (String[] entry : NORMALIZATION_TABLE) {
            if (lower.contains(entry[0])) return entry[1];
        }
        return raw.trim();
    }
}
