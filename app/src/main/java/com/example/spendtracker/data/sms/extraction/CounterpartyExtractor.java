package com.example.spendtracker.data.sms.extraction;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Token roles for bank narrations. A payment handle is not promoted to a personal name. */
public final class CounterpartyExtractor {
    private static final Pattern CHANNEL = Pattern.compile("(?i)\\b(UPI|MMT/IMPS|IMPS|NEFT|RTGS|VPS|IPS|POS|ECOM|INF|NACH|ACH)[/*-]");
    private static final Pattern HANDLE = Pattern.compile("(?i)[\\p{L}\\p{N}._%+-]+@[\\p{L}\\p{N}.-]+");
    private static final Pattern SKIP = Pattern.compile(
            "(?i)(?:UPI|DR|CR|MMT|IMPS|NEFT|RTGS|VPS|IPS|POS|ECOM|INF|MB|P2A|P2M|P2P|"
            + "TRF|TRANSFER|PAYMENT|REF|REFNO|RRN|UTR|C|B|TO|FROM|BY|NA|NIL|"
            + "OPENING BALANCE|CLOSING BALANCE|SALARY|CASH|ATM|DEBIT|CREDIT|WITHDRAWAL)");
    private static final Pattern ROUTING = Pattern.compile("(?i)(?:[A-Z]{4}0[A-Z0-9]{6}|[A-Z]{2,8}\\d{5,})");
    private static final Pattern BANK = Pattern.compile(
            "(?i)(?:(?:STATE BANK OF INDIA|STATE BANK OF|AXIS|ICICI|HDFC|YES|SBI|CNRB|YESB|UTIB|"
            + "KOTAK|BANK OF INDIA|ANDHRA|UNION|CANARA)(?:\\s+BANK)?(?:\\s+LTD\\.?)?)");

    public static final class Result {
        public final String name;
        public final String handle;
        public final double confidence;
        public final String source;
        Result(String name, String handle, double confidence, String source) {
            this.name = name; this.handle = handle; this.confidence = confidence; this.source = source;
        }
        public String displayName() { return name.isEmpty() ? handle : name; }
    }

    public Result extract(String narration) {
        String text = narration == null ? "" : narration.replaceAll("\\s+", " ").trim();
        // A small-font I/ can merge into V or lose I. Repair only when the printed heading is
        // repeated verbatim after that marker and the payload contains a UPI handle.
        if (text.contains("@")) {
            text = text.replaceFirst("(?i)^([\\p{L} .&'-]{2,100}?)\\s+UP(?:V|[Il1|]?\\s*/)(?=\\1\\s*/)", "$1 UPI/");
            // Spaces inside a slash-delimited handle are OCR word breaks, not name spaces.
            Matcher chunks = Pattern.compile("/([^/]*@[^/]*)(?=/|$)").matcher(text);
            StringBuffer normalized = new StringBuffer();
            while (chunks.find()) {
                String compact = chunks.group(1).replaceAll("\\s+", "");
                chunks.appendReplacement(normalized, Matcher.quoteReplacement(
                        HANDLE.matcher(compact).matches() ? "/" + compact : chunks.group()));
            }
            chunks.appendTail(normalized);
            text = normalized.toString();
        }
        Matcher channel = CHANNEL.matcher(text);
        if (channel.find()) {
            // Statements often print a display name above the machine narration. Keep that
            // explicit name even when the UPI payload contains only a handle or opaque ID.
            String heading = clean(text.substring(0, channel.start()));
            if (isName(heading) && heading.matches("[\\p{L} .&'-]+")
                    && !heading.matches("(?i).*\\b(?:UPI|NACH|ACH|TRXN?|TXN|TRANSFER|PAYMENT|DEBIT|CREDIT|PAID)\\b.*")
                    && !BANK.matcher(heading).matches()) {
                Matcher vpa = HANDLE.matcher(text.substring(channel.end()));
                return new Result(heading, vpa.find() ? vpa.group() : "", .95, "STATEMENT_NAME");
            }
            String tail = text.substring(channel.end());
            String delimiter = text.charAt(channel.end() - 1) == '*' ? "\\*" :
                    text.charAt(channel.end() - 1) == '/' ? "/" : "-";
            String handle = "";
            for (String token : tail.split(delimiter)) {
                String candidate = clean(token);
                Matcher vpa = HANDLE.matcher(candidate);
                if (vpa.find()) {
                    handle = vpa.group().replaceAll("[.;]+$", "");
                    return new Result("", handle, .60, "VPA_FALLBACK");
                }
                if (isName(candidate) && !BANK.matcher(candidate).matches())
                    return new Result(candidate, handle, .90, "NARRATION_TOKEN");
                // After a handle the remaining tokens usually identify the intermediary bank.
                if (!handle.isEmpty()) break;
            }
            if (!handle.isEmpty()) return new Result("", handle, .60, "VPA_FALLBACK");
            return new Result("", "", 0, "UNKNOWN");
        }
        Matcher labeled = Pattern.compile("(?i)\\b(?:paid\\s+to|to|by|from|fvg)\\s*:?\\s+(.+?)(?=\\s+(?:on|ref|rrn|utr|via)\\b|$)").matcher(text);
        if (labeled.find() && isName(clean(labeled.group(1))))
            return new Result(clean(labeled.group(1)), "", .80, "LABELED_NAME");
        // Plain merchant cells are useful; don't call opaque bank codes or whole sentences names.
        if (!text.matches(".*[/\\-*:@].*") && text.length() <= 100 && isName(text)
                && !text.toUpperCase(Locale.ROOT).matches(".*\\b(?:BALANCE|WITHDRAWN|TRANSFER|CHARGES|INTEREST|CASH|ATM)\\b.*"))
            return new Result(clean(text), "", .65, "PLAIN_NARRATION");
        return new Result("", "", 0, "UNKNOWN");
    }

    public static String clean(String text) {
        return text == null ? "" : text.replaceAll("\\s+", " ").replaceAll("^[\\s:;]+|[\\s;:,]+$", "").trim();
    }
    private static boolean isName(String candidate) {
        return candidate.length() >= 2 && candidate.length() <= 120
                && Pattern.compile("[\\p{L}]").matcher(candidate).find()
                && !SKIP.matcher(candidate).matches() && !ROUTING.matcher(candidate).matches()
                && candidate.replaceAll("\\D", "").length() < 5
                && !candidate.matches("(?i).*\\b(?:a/c|acct|account|refno|payment from|payment to)\\b.*");
    }
}
