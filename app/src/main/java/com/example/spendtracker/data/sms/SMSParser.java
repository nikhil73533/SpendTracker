package com.example.spendtracker.data.sms;

import android.content.Context;
import android.util.Log;
import com.example.spendtracker.data.local.entity.RegexPatternEntity;
import com.example.spendtracker.domain.model.Transaction;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.inject.Singleton;
import dagger.hilt.android.qualifiers.ApplicationContext;

/**
 * SMSParser – parses Indian-bank transaction SMS messages into {@link Transaction} objects.
 *
 * <p>Parsing pipeline:
 * <ol>
 *   <li>OTP / non-transactional filter (fast-reject)</li>
 *   <li>JSON bank-config assets ({@code assets/bank_configs/})</li>
 *   <li>DB-stored custom regex patterns</li>
 *   <li>Generic rule-based fallback (amount + credit/debit keywords)</li>
 * </ol>
 *
 * <p>The no-arg constructor is provided for unit tests that do not have an Android context.
 */
@Singleton
public class SMSParser {
    private static final String TAG = "SMSParser";
    private final Context context;

    // ---- Compiled patterns (static to avoid recompiling per message) ----

    /** OTP-specific keyword patterns. */
    private static final Pattern[] OTP_PATTERNS = {
        Pattern.compile("\\botp\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("one[- ]time[- ]password", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\bverification[- ]code\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\bpasscode\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("your password is", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\bpin\\b.*\\bis\\b", Pattern.CASE_INSENSITIVE),
        Pattern.compile("do not share.*\\d{4,8}", Pattern.CASE_INSENSITIVE),
    };

    /** Advertisement / promotional patterns that should be rejected. */
    private static final Pattern[] PROMO_PATTERNS = {
        // These contain promo keywords AND no financial transaction keywords
        Pattern.compile("(?i)\\b(congratulations|won|lucky|click here|offer|discount|promo)\\b"),
    };

    /** Login / non-financial alert pattern. */
    private static final Pattern LOGIN_ALERT_PATTERN = Pattern.compile(
        "(?i)\\blogin\\b.{0,40}\\b(attempt|detected)\\b");

    /** Debit / expense keywords. */
    private static final Pattern DEBIT_PATTERN = Pattern.compile(
        "(?i)\\b(debited|deducted|dr\\.?|withdrawn|payment\\s+of|spent|paid|purchase|charged|debit)\\b");

    /** Credit / income keywords. */
    private static final Pattern CREDIT_PATTERN = Pattern.compile(
        "(?i)\\b(credited|cr\\.?|received|added\\s+to|deposited|refund|cashback\\s+credited)\\b");

    /**
     * Amount patterns in priority order.
     * Group 1 = numeric amount string (digits, commas, dots).
     */
    private static final Pattern[] AMOUNT_PATTERNS = {
        // INR / Rs / Rs. / ₹ prefix – also handles "INR1,500" (no space)
        Pattern.compile("(?i)(?:INR|Rs\\.?|₹)\\s*([0-9,]+(?:\\.[0-9]{1,2})?)"),
        // "debited Rs 500" / "credited Rs. 1,000.00"
        Pattern.compile("(?i)(?:debited|credited|spent|withdrawn|charged|deducted)\\s+(?:(?:INR|Rs\\.?|₹)\\s*)?([0-9,]+(?:\\.[0-9]{1,2})?)"),
        // "amount of Rs 500" / "amount Rs500"
        Pattern.compile("(?i)amount(?:\\s+of)?\\s+(?:(?:INR|Rs\\.?|₹)\\s*)?([0-9,]+(?:\\.[0-9]{1,2})?)"),
        // "Dr 148.00" at start of string (AU Bank style)
        Pattern.compile("(?im)^Dr\\s+(?:INR\\s*)?([0-9,]+(?:\\.[0-9]{1,2})?)"),
        // "Cr 2500.00" at start of string (AU Bank credit style)
        Pattern.compile("(?im)^Cr\\s+(?:INR\\s*)?([0-9,]+(?:\\.[0-9]{1,2})?)"),
    };

    /**
     * UPI reference / transaction ID patterns.
     * Tries numeric ref first, then UPI VPA (email-like address).
     */
    private static final Pattern UPI_NUMERIC_REF_PATTERN = Pattern.compile(
        "(?i)(?:UPI[:/]?\\s*(?:Ref\\.?\\s*(?:No\\.?)?|No\\.?|Ref\\s+No\\.?)?|Ref\\s+No\\.?|Txn\\s*(?:Id|No|Ref)\\.?)[:\\s]?\\s*([0-9]{6,20})");

    private static final Pattern VPA_PATTERN = Pattern.compile(
        "([a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+)");

    /** Bank-name detection map (lowercase keyword → display name). Ordered so longer/more-specific keywords come first. */
    private static final String[][] BANK_KEYWORDS = {
        {"au bank",          "AU Bank"},
        {"au a/c",           "AU Bank"},
        {"au small",         "AU Bank"},
        {"amazon pay",       "Amazon Pay"},
        {"airtel payments",  "Airtel Payments Bank"},
        {"jio payments",     "Jio Payments Bank"},
        {"union bank",       "Union Bank"},
        {"bank of baroda",   "Bank of Baroda"},
        {"yes bank",         "Yes Bank"},
        {"one card",         "OneCard"},
        {"bajaj finance",    "Bajaj Finance"},
        {"indusind",         "IndusInd Bank"},
        {"federal",          "Federal Bank"},
        {"icici",            "ICICI Bank"},
        {"hdfc",             "HDFC Bank"},
        {"axis",             "Axis Bank"},
        {"kotak",            "Kotak Bank"},
        {"canara",           "Canara Bank"},
        {"paytm",            "Paytm"},
        {"phonepe",          "PhonePe"},
        {"gpay",             "Google Pay"},
        {"mobikwik",         "MobiKwik"},
        {"yesb",             "Yes Bank"},
        {"pnb",              "PNB"},
        {"bob",              "Bank of Baroda"},
        {"idbi",             "IDBI Bank"},
        {"rbl",              "RBL Bank"},
        {"sbi",              "SBI"},
        {"slice",            "Slice"},
        {"navi",             "Navi"},
        {"airtel",           "Airtel Payments Bank"},
    };

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    /** Hilt-injected constructor (production use). */
    @Inject
    public SMSParser(@ApplicationContext Context context) {
        this.context = context;
    }

    /**
     * No-arg constructor for unit tests where an Android {@link Context} is unavailable.
     * JSON-config asset parsing is disabled; only generic rule-based parsing runs.
     */
    public SMSParser() {
        this.context = null;
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Attempts to parse {@code body} into a {@link Transaction}.
     *
     * @param sender    SMS sender string (short code or phone number)
     * @param body      Raw SMS body text
     * @param dbPatterns Custom regex patterns stored in the database
     * @return A populated {@link Transaction} or {@code null} if the message is not transactional
     */
    public Transaction parseSMS(String sender, String body, List<RegexPatternEntity> dbPatterns) {
        if (body == null || body.isEmpty()) return null;

        if (isOtpOrNonTransactional(body)) {
            safeLog("Message identified as OTP or non-transactional. Skipping.");
            return null;
        }

        // 1. JSON bank configs (assets) – requires Android context
        if (context != null) {
            Transaction t = parseFromJsonConfigs(body, sender);
            if (isValid(t)) return t;
        }

        // 2. DB custom patterns
        if (dbPatterns != null) {
            for (RegexPatternEntity entity : dbPatterns) {
                try {
                    Matcher m = Pattern.compile(entity.pattern, Pattern.CASE_INSENSITIVE).matcher(body);
                    if (m.find()) {
                        Transaction t = createTransactionFromMatcher(body, sender, m, entity);
                        if (isValid(t)) return t;
                    }
                } catch (Exception e) {
                    safeLogError("DB pattern error: " + entity.pattern, e);
                }
            }
        }

        // 3. Generic rule-based fallback
        Transaction generic = parseGeneric(sender, body);
        if (isValid(generic)) return generic;

        return null;
    }

    // -------------------------------------------------------------------------
    // OTP / non-transactional guard
    // -------------------------------------------------------------------------

    /**
     * Returns {@code true} when the message should be rejected without further parsing.
     *
     * <p>Rejection conditions (in order):
     * <ol>
     *   <li>Explicit OTP keyword patterns</li>
     *   <li>Login-alert patterns without financial keywords</li>
     *   <li>Promotional/marketing patterns without financial keywords</li>
     *   <li>No credit or debit keyword present</li>
     * </ol>
     */
    private boolean isOtpOrNonTransactional(String body) {
        // OTP signals → always reject
        for (Pattern p : OTP_PATTERNS) {
            if (p.matcher(body).find()) return true;
        }

        boolean hasDebit  = DEBIT_PATTERN.matcher(body).find();
        boolean hasCredit = CREDIT_PATTERN.matcher(body).find();

        // Login alerts without any financial transaction signal
        if (LOGIN_ALERT_PATTERN.matcher(body).find() && !hasDebit && !hasCredit) return true;

        // Pure marketing messages without any financial transaction signal
        for (Pattern p : PROMO_PATTERNS) {
            if (p.matcher(body).find() && !hasDebit && !hasCredit) return true;
        }

        // If neither debit nor credit keyword present → not a transaction
        return !hasDebit && !hasCredit;
    }

    // -------------------------------------------------------------------------
    // Step 1 – JSON bank config parsing
    // -------------------------------------------------------------------------

    private Transaction parseFromJsonConfigs(String body, String sender) {
        try {
            String[] files = context.getAssets().list("bank_configs");
            if (files == null) return null;

            for (String file : files) {
                String jsonStr = loadAsset("bank_configs/" + file);
                if (jsonStr == null) continue;

                JSONObject bankConfig = new JSONObject(jsonStr);
                String bankName = standardizeBankName(bankConfig.getString("bankName"));
                JSONArray patterns = bankConfig.getJSONArray("patterns");

                for (int i = 0; i < patterns.length(); i++) {
                    JSONObject pObj = patterns.getJSONObject(i);
                    Matcher m = Pattern.compile(pObj.getString("regex"), Pattern.CASE_INSENSITIVE).matcher(body);
                    if (!m.find()) continue;

                    double amount = safeGroupDouble(m, pObj, "amountGroup");
                    String receiver = safeGroupString(m, pObj, "receiverGroup");
                    String upiId = safeGroupString(m, pObj, "upiGroup");
                    String type = pObj.optString("type", "EXPENSE");
                    String sourceType = pObj.optString("sourceType", "Account");

                    if (amount <= 0) continue;

                    return new Transaction(
                        0, amount, "Other", null, cleanText(body), type, System.currentTimeMillis(),
                        bankName + " (" + sourceType + ")",
                        sender, upiId, cleanText(receiver), bankName, sourceType
                    );
                }
            }
        } catch (Exception e) {
            safeLogError("parseFromJsonConfigs error", e);
        }
        return null;
    }

    private String loadAsset(String fileName) {
        if (context == null) return null;
        try (InputStream is = context.getAssets().open(fileName)) {
            byte[] buf = new byte[is.available()];
            is.read(buf);
            return new String(buf, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    private double safeGroupDouble(Matcher m, JSONObject pObj, String key) {
        try {
            int g = pObj.getInt(key);
            if (g <= 0) return 0;
            String v = m.group(g);
            return v != null ? Double.parseDouble(v.replaceAll(",", "")) : 0;
        } catch (Exception ignored) { return 0; }
    }

    private String safeGroupString(Matcher m, JSONObject pObj, String key) {
        try {
            int g = pObj.getInt(key);
            if (g <= 0) return "";
            String v = m.group(g);
            return v != null ? v.trim() : "";
        } catch (Exception ignored) { return ""; }
    }

    // -------------------------------------------------------------------------
    // Step 2 – DB pattern parsing
    // -------------------------------------------------------------------------

    private Transaction createTransactionFromMatcher(String body, String sender, Matcher m, RegexPatternEntity entity) {
        double amount = 0;
        try {
            String v = m.group(entity.amountGroup);
            if (v != null) amount = Double.parseDouble(v.replaceAll(",", ""));
        } catch (Exception ignored) {}

        String type = "EXPENSE";
        try {
            if (entity.typeGroup > 0) {
                String typeStr = m.group(entity.typeGroup);
                if (typeStr != null) {
                    String lower = typeStr.toLowerCase();
                    if (lower.contains("credit") || lower.contains("received") || lower.contains("cr")) {
                        type = "INCOME";
                    }
                }
            }
        } catch (Exception ignored) {}

        return new Transaction(0, amount, "Uncategorized", null, cleanText(body), type,
            System.currentTimeMillis(), "SMS", sender, "", "", "", "SMS");
    }

    // -------------------------------------------------------------------------
    // Step 3 – Generic rule-based parsing
    // -------------------------------------------------------------------------

    private Transaction parseGeneric(String sender, String body) {
        boolean isDebit  = DEBIT_PATTERN.matcher(body).find();
        boolean isCredit = CREDIT_PATTERN.matcher(body).find();

        // When both match, debit wins (e.g. "debited … TANISHA KHANDEL credited")
        String type = isDebit ? "EXPENSE" : (isCredit ? "INCOME" : null);
        if (type == null) return null;

        double amount = extractAmount(body);
        if (amount <= 0) return null;

        String bankName   = standardizeBankName(extractBank(body.toLowerCase()));
        String sourceType = detectSourceType(body);
        String receiver   = extractReceiver(body, bankName);
        String upiId      = extractUpiOrRef(body);

        String source = bankName + " (" + sourceType + ")";
        return new Transaction(0, amount, "Other", null, cleanText(body), type,
            System.currentTimeMillis(), source, sender, upiId, cleanText(receiver), bankName, sourceType);
    }

    // ---- Helper extractors --------------------------------------------------

    /** Extracts the best-matching amount from the message. */
    double extractAmount(String body) {
        for (Pattern p : AMOUNT_PATTERNS) {
            Matcher m = p.matcher(body);
            if (m.find()) {
                try {
                    String val = m.group(1);
                    if (val == null) continue;
                    double amount = Double.parseDouble(val.replaceAll(",", ""));
                    // Sanity: reject values that look like years or phone-number segments
                    if (amount > 0 && amount < 10_000_000) return amount;
                } catch (NumberFormatException ignored) {}
            }
        }
        return 0;
    }

    /** Identifies the bank name from message content (lowercase body). */
    String extractBank(String lowerBody) {
        for (String[] entry : BANK_KEYWORDS) {
            if (lowerBody.contains(entry[0])) return entry[1];
        }
        return "Bank";
    }

    /**
     * Normalizes a raw bank name to a canonical short form.
     * E.g. "ICICI Bank" → "ICICI", "State Bank of India" → "SBI".
     */
    static String standardizeBankName(String raw) {
        if (raw == null || raw.trim().isEmpty()) return raw;
        String lower = raw.trim().toLowerCase();
        // Order matters: more specific first
        if (lower.contains("state bank"))     return "SBI";
        if (lower.contains("icici"))           return "ICICI";
        if (lower.contains("hdfc"))            return "HDFC";
        if (lower.contains("axis"))            return "Axis";
        if (lower.contains("kotak"))           return "Kotak";
        if (lower.contains("sbi"))             return "SBI";
        if (lower.contains("pnb"))             return "PNB";
        if (lower.contains("union bank"))      return "Union Bank";
        if (lower.contains("bank of baroda"))  return "Bank of Baroda";
        if (lower.contains("bob"))             return "Bank of Baroda";
        if (lower.contains("yes bank"))        return "Yes Bank";
        if (lower.contains("yesb"))            return "Yes Bank";
        if (lower.contains("canara"))          return "Canara";
        if (lower.contains("idbi"))            return "IDBI";
        if (lower.contains("indusind"))        return "IndusInd";
        if (lower.contains("federal"))         return "Federal";
        if (lower.contains("rbl"))             return "RBL";
        if (lower.contains("au bank"))         return "AU Bank";
        if (lower.contains("au small"))        return "AU Bank";
        if (lower.contains("bajaj"))           return "Bajaj Finance";
        if (lower.contains("amazon pay"))      return "Amazon Pay";
        if (lower.contains("airtel payments")) return "Airtel Payments";
        if (lower.contains("jio payments"))    return "Jio Payments";
        if (lower.contains("paytm"))           return "Paytm";
        if (lower.contains("phonepe"))         return "PhonePe";
        if (lower.contains("one card") || lower.contains("onecard")) return "OneCard";
        if (lower.contains("slice"))           return "Slice";
        if (lower.contains("navi"))            return "Navi";
        // Already a short token — return as-is
        return raw.trim();
    }

    /** Determines whether the source is a Credit Card, Wallet, UPI, or Account. */
    private String detectSourceType(String body) {
        String lower = body.toLowerCase();
        if (lower.contains("credit card") || lower.contains("cc ") || lower.contains("/cc/")) return "Credit Card";
        if (lower.contains("wallet") || lower.contains("paytm") || lower.contains("phonepe")) return "Wallet";
        if (lower.contains("upi")) return "UPI";
        return "Account";
    }

    /**
     * Attempts to extract the receiver / merchant name.
     * Tries multiple named-group strategies before falling back to generic patterns.
     */
    String extractReceiver(String body, String bankName) {
        // AU Bank: UPI/DR/<ref>/<receiver>/<bank>
        Matcher auMatcher = Pattern.compile("UPI/(?:DR|CR)/(\\d+)/([^/]+)/", Pattern.CASE_INSENSITIVE).matcher(body);
        if (auMatcher.find() && auMatcher.group(2) != null) return auMatcher.group(2).trim();

        // ICICI style: "for UPI-<ref>-<receiver>."
        Matcher iciciUpi = Pattern.compile("for\\s+UPI-\\d+-([^.]+)", Pattern.CASE_INSENSITIVE).matcher(body);
        if (iciciUpi.find() && iciciUpi.group(1) != null) return iciciUpi.group(1).trim();

        // "sender credited" style: extract name before "credited"
        Matcher creditorMatcher = Pattern.compile("([A-Z][A-Za-z\\s.]{2,30})\\s+credited", Pattern.CASE_INSENSITIVE).matcher(body);
        if (creditorMatcher.find() && creditorMatcher.group(1) != null) {
            String candidate = creditorMatcher.group(1).trim();
            // Avoid capturing bank keywords like "ICICI Bank credited"
            if (!candidate.toLowerCase().contains("bank") && !candidate.toLowerCase().contains("a/c")) {
                return candidate;
            }
        }

        // Generic: "to/by/from/at <Name>"
        Matcher byMatcher = Pattern.compile(
            "(?i)(?:to|by|from|at)\\s+([A-Z][A-Za-z\\s&'.,-]{2,40}?)(?=\\s*(?:on|via|using|a/c|upi|ref|\\d|\\.|,|$))",
            Pattern.CASE_INSENSITIVE).matcher(body);
        if (byMatcher.find() && byMatcher.group(1) != null) {
            String candidate = byMatcher.group(1).trim();
            if (!candidate.isEmpty() && !candidate.equalsIgnoreCase("INR") &&
                !candidate.equalsIgnoreCase("Rs") && !candidate.equalsIgnoreCase("your")) {
                return candidate;
            }
        }

        return "";
    }

    /** Extracts UPI VPA or numeric reference/transaction ID. Numeric ref takes priority. */
    String extractUpiOrRef(String body) {
        // Try numeric reference first
        Matcher numMatcher = UPI_NUMERIC_REF_PATTERN.matcher(body);
        if (numMatcher.find() && numMatcher.group(1) != null) return numMatcher.group(1);

        // Fall back to VPA
        Matcher vpaMatcher = VPA_PATTERN.matcher(body);
        if (vpaMatcher.find()) return vpaMatcher.group(1);

        return "";
    }

    // -------------------------------------------------------------------------
    // Validation
    // -------------------------------------------------------------------------

    private boolean isValid(Transaction tx) {
        if (tx == null) return false;
        if (tx.getAmount() <= 0 || tx.getAmount() > 10_000_000) return false;
        if (tx.getType() == null || tx.getType().isEmpty()) return false;
        return true;
    }

    private String cleanText(String text) {
        if (text == null) return "";
        // Remove emojis (Regex for characters outside the basic multilingual plane or specific emoji blocks)
        return text.replaceAll("[\\uD83C-\\uDBFF\\uDC00-\\uDFFF]", "").trim();
    }

    // -------------------------------------------------------------------------
    // Logging helpers – safe for unit tests (no Android.util.Log dependency)
    // -------------------------------------------------------------------------

    private void safeLog(String message) {
        if (context != null) {
            Log.d(TAG, message);
        }
    }

    private void safeLogError(String message, Throwable t) {
        if (context != null) {
            Log.e(TAG, message, t);
        }
    }
}
