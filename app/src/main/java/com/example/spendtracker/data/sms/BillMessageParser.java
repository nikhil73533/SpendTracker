package com.example.spendtracker.data.sms;

import java.time.*;
import java.time.format.*;
import java.util.Locale;
import java.util.regex.*;

/** Conservative bill extraction: unknown dates/amounts require review, never invented. */
public final class BillMessageParser {
    private static final Pattern BILL = Pattern.compile(
            "(?i)\\b(bill|payment due|amount due|total due|minimum due|emi|premium|renewal|subscription|recharge expires)\\b");
    private static final Pattern REJECT = Pattern.compile(
            "(?i)\\b(otp|one.time password|verification code|payment (?:of .{0,40} )?(?:received|successful)|bill paid|paid successfully|no dues|no amount due|pre.approved|cashback offer|win a|congratulations)\\b");
    private static final String MONEY = "(?:rs\\.?|inr|₹)\\s*([0-9][0-9,]*(?:\\.\\d{1,2})?)(?!\\d|[.,]\\d)";
    private static final Pattern AMOUNT = Pattern.compile(MONEY, Pattern.CASE_INSENSITIVE);
    private static final Pattern TOTAL = Pattern.compile(
            "(?i)(?:total\\s+(?:amount\\s+)?due|total\\s+(?:bill|outstanding)|bill\\s+amount|amount\\s+(?:due|payable))\\s*(?:is|of|:|-)?\\s*(?:(?:rs\\.?|inr|₹)\\s*)?([0-9][0-9,]*(?:\\.\\d{1,2})?)(?!\\d|[.,]\\d)");
    private static final Pattern DUE = Pattern.compile(
            "(?i)(?:due\\s*date\\s*(?:is|:|-)?|due\\s+(?:on|by|in)|due|pay(?:able)?\\s+by|before|expires\\s+on)\\s*[:\\-]?\\s*" +
            "(today|tomorrow|\\d{1,2}\\s+days?|\\d{4}-\\d{1,2}-\\d{1,2}|\\d{1,2}[/.-]\\d{1,2}[/.-](?:\\d{4}|\\d{2})|\\d{1,2}(?:st|nd|rd|th)?[\\s-]+[A-Za-z]{3,9}(?:[\\s,-]+(?:\\d{4}|\\d{2}))?|\\d{1,2}(?:st|nd|rd|th)?)(?!\\w|[/.-]\\d)");
    private static final Pattern TIME = Pattern.compile("(?i)\\b(?:at|by|before)\\s*(\\d{1,2})(?::(\\d{2}))?\\s*(a\\.?m\\.?|p\\.?m\\.?)\\b|\\b(\\d{1,2}):(\\d{2})\\b");
    private static final Pattern BILLER = Pattern.compile("(?i)\\b(?:bill\\s+(?:from|for)|from|for)\\s+([a-z][a-z0-9 .&'\\-]{2,48}?)(?=\\s+(?:is|of|due|amount|payment|on|by)\\b|[,.]|$)");
    private static final Pattern LEADING_BILLER = Pattern.compile("(?i)^\\s*([a-z][a-z .&'\\-]{2,32}?)\\s+(?:bill|invoice|emi|premium)\\b");
    public static final class Result {
        public final boolean isBill;
        public final double amount;
        public final LocalDate dueDate;
        /** Minute after midnight, or -1 when a bill message does not state a time. */
        public final int dueMinuteOfDay;
        /** Best-effort biller name; it is always presented for review before saving. */
        public final String biller;
        Result(boolean isBill, double amount, LocalDate dueDate, int dueMinuteOfDay, String biller) {
            this.isBill = isBill; this.amount = amount; this.dueDate = dueDate; this.dueMinuteOfDay = dueMinuteOfDay;
            this.biller = biller == null ? "" : biller;
        }
    }

    public Result parse(String body, long receivedAt, ZoneId zone) {
        if (body == null || !BILL.matcher(body).find() || REJECT.matcher(body).find())
            return new Result(false, 0, null, -1, "");
        LocalDate reference = Instant.ofEpochMilli(receivedAt).atZone(zone).toLocalDate();
        Matcher due = DUE.matcher(body);
        LocalDate date = due.find() ? parseDate(due.group(1), reference) : null;
        Matcher total = TOTAL.matcher(body);
        double amount = 0;
        if (total.find()) amount = number(total.group(1));
        else {
            Matcher money = AMOUNT.matcher(body);
            if (money.find()) {
                amount = number(money.group(1));
                // Multiple unlabeled amounts (e.g. minimum due and credit limit) are ambiguous.
                if (money.find()) amount = 0;
            }
        }
        return new Result(true, amount, date, parseTime(body), parseBiller(body));
    }

    private double number(String value) {
        try {
            double n = Double.parseDouble(value.replace(",", ""));
            return Double.isFinite(n) && n > 0 ? n : 0;
        } catch (NumberFormatException e) { return 0; }
    }

    private LocalDate parseDate(String raw, LocalDate reference) {
        String value = raw.toLowerCase(Locale.ENGLISH).replaceAll("(\\d)(st|nd|rd|th)", "$1").trim();
        try {
            if (value.equals("today")) return reference;
            if (value.equals("tomorrow")) return reference.plusDays(1);
            if (value.matches("\\d+\\s+days?")) return reference.plusDays(Integer.parseInt(value.split("\\s+")[0]));
            if (value.matches("\\d{1,2}")) {
                int day = Integer.parseInt(value);
                LocalDate candidate = reference.withDayOfMonth(day);
                return candidate.isBefore(reference) ? reference.plusMonths(1).withDayOfMonth(day) : candidate;
            }
            if (value.matches("\\d{4}-\\d{1,2}-\\d{1,2}"))
                return LocalDate.parse(value, DateTimeFormatter.ofPattern("uuuu-M-d").withResolverStyle(ResolverStyle.STRICT));
            if (value.matches("\\d{1,2}[/.-]\\d{1,2}[/.-]\\d{2,4}")) {
                String[] parts = value.split("[/.-]");
                int year = Integer.parseInt(parts[2]);
                return LocalDate.of(year < 100 ? 2000 + year : year, Integer.parseInt(parts[1]), Integer.parseInt(parts[0]));
            }
            value = value.replaceAll("[,-]", " ").replaceAll("\\s+", " ");
            value = value.replaceAll("\\bsept\\b", "sep");
            if (value.matches("\\d{1,2} [a-z]+ \\d{2}")) {
                int space = value.lastIndexOf(' ');
                value = value.substring(0, space + 1) + "20" + value.substring(space + 1);
            }
            boolean hasYear = value.matches(".*\\d{4}$");
            String dated = hasYear ? value : value + " " + reference.getYear();
            for (String format : new String[]{"d MMM uuuu", "d MMMM uuuu"}) {
                try {
                    LocalDate candidate = LocalDate.parse(dated, new DateTimeFormatterBuilder()
                            .parseCaseInsensitive().appendPattern(format).toFormatter(Locale.ENGLISH)
                            .withResolverStyle(ResolverStyle.STRICT));
                    if (!hasYear && candidate.isBefore(reference.minusDays(30))) candidate = candidate.plusYears(1);
                    return candidate;
                } catch (DateTimeException ignored) { }
            }
        } catch (DateTimeException | NumberFormatException ignored) { }
        return null;
    }

    private int parseTime(String body) {
        Matcher time = TIME.matcher(body);
        if (!time.find()) return -1;
        try {
            boolean twelveHour = time.group(1) != null;
            int hour = Integer.parseInt(twelveHour ? time.group(1) : time.group(4));
            int minute = Integer.parseInt(twelveHour
                    ? (time.group(2) == null ? "0" : time.group(2)) : time.group(5));
            String meridiem = time.group(3);
            if (minute < 0 || minute > 59) return -1;
            if (meridiem != null) {
                if (hour < 1 || hour > 12) return -1;
                if (meridiem.toLowerCase(Locale.ROOT).startsWith("p") && hour != 12) hour += 12;
                if (meridiem.toLowerCase(Locale.ROOT).startsWith("a") && hour == 12) hour = 0;
            } else if (hour > 23) return -1;
            return hour * 60 + minute;
        } catch (NumberFormatException ignored) { return -1; }
    }

    private String parseBiller(String body) {
        Matcher matcher = BILLER.matcher(body);
        if (!matcher.find()) {
            matcher = LEADING_BILLER.matcher(body);
            if (!matcher.find()) return "";
        }
        String name = matcher.group(1).replaceAll("\\s+", " ").trim();
        return name.length() > 48 ? name.substring(0, 48).trim() : name;
    }
}
