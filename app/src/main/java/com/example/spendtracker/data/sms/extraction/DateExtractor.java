package com.example.spendtracker.data.sms.extraction;

import com.example.spendtracker.data.sms.model.ExtractionResult;
import java.time.*;
import java.time.format.*;
import java.time.temporal.ChronoField;
import java.util.Locale;
import java.util.regex.*;

/** Strict full-token dates; arrival time is provenance, never a fabricated printed time. */
public class DateExtractor {
    private static final String DATE = "(?:\\d{4}-\\d{1,2}-\\d{1,2}|"
            + "\\d{1,2}[-/.](?:\\d{1,2}|[A-Za-z]{3,9})[-/.](?:\\d{4}|\\d{2})|"
            + "\\d{1,2}\\s+[A-Za-z]{3,9},?\\s+(?:\\d{4}|\\d{2})|\\d{1,2}[A-Za-z]{3}(?:\\d{4}|\\d{2}))";
    private static final Pattern DATE_TIME = Pattern.compile("(?i)(?<![\\w/.-])(" + DATE + ")(?!\\d)"
            + "(?:(?:\\s+at\\s+|[T:\\s]+)(\\d{1,2}:\\d{2}(?::\\d{2})?\\s*(?:[AP]M)?))?");
    private static final Pattern YEARLESS = Pattern.compile("(?i)\\bon\\s+(\\d{1,2})[-/](\\d{1,2})(?![-/\\d])");
    private static final String[] FORMATS = {"uuuu-M-d", "d-M-uuuu", "d-MMM-uuuu", "d-MMMM-uuuu",
            "d MMM uuuu", "d MMMM uuuu", "dMMMuuuu"};

    public static final class Result {
        public final long timestamp;
        public final String precision;
        Result(long timestamp, String precision) { this.timestamp = timestamp; this.precision = precision; }
    }

    public Result extractDetailed(String message, long smsTimestamp) {
        long arrival = smsTimestamp > 0 ? smsTimestamp : System.currentTimeMillis();
        String text = TransactionText.core(message);
        ZoneId zone = ZoneId.systemDefault();
        Matcher m = DATE_TIME.matcher(text);
        while (m.find()) {
            LocalDate date = parseDate(m.group(1));
            if (date == null) continue;
            String rawTime = m.group(2);
            if (rawTime != null) {
                LocalTime time = parseTime(rawTime);
                if (time != null) return new Result(date.atTime(time).atZone(zone).toInstant().toEpochMilli(), "DATE_TIME");
                // Malformed printed time must not be presented as successfully parsed.
            }
            // Preserve arrival ordering for date-only SMS, but mark the time as not printed.
            return new Result(date.atTime(Instant.ofEpochMilli(arrival).atZone(zone).toLocalTime())
                    .atZone(zone).toInstant().toEpochMilli(), "DATE_ONLY");
        }
        Matcher shortDate = YEARLESS.matcher(text);
        if (shortDate.find()) {
            try {
                LocalDate received = Instant.ofEpochMilli(arrival).atZone(zone).toLocalDate();
                LocalDate date = LocalDate.of(received.getYear(), Integer.parseInt(shortDate.group(2)), Integer.parseInt(shortDate.group(1)));
                if (date.isAfter(received.plusMonths(6))) date = date.minusYears(1);
                return new Result(date.atStartOfDay(zone).toInstant().toEpochMilli(), "DATE_ONLY");
            } catch (DateTimeException ignored) { }
        }
        return new Result(arrival, "SMS_RECEIVED");
    }

    public ExtractionResult<Long> extract(String message, long smsTimestamp) {
        Result result = extractDetailed(message, smsTimestamp);
        return ExtractionResult.of(result.timestamp, "SMS_RECEIVED".equals(result.precision) ? .60 : .90);
    }

    private LocalDate parseDate(String raw) {
        String normalized = raw.replace('/', '-').replace('.', '-').replace(",", "").replaceAll("\\s+", " ");
        for (String format : FORMATS) {
            for (boolean shortYear : new boolean[]{false, true}) {
                if (format.startsWith("uuuu") && shortYear) continue;
                try {
                    DateTimeFormatterBuilder builder = new DateTimeFormatterBuilder().parseCaseInsensitive();
                    if (shortYear) {
                        builder.appendPattern(format.substring(0, format.length() - 4))
                                .appendValueReduced(ChronoField.YEAR, 2, 2, 2000);
                    } else builder.appendPattern(format);
                    return LocalDate.parse(normalized, builder.toFormatter(Locale.ENGLISH).withResolverStyle(ResolverStyle.STRICT));
                } catch (DateTimeException ignored) { }
            }
        }
        return null;
    }

    private LocalTime parseTime(String raw) {
        String text = raw.trim().replaceAll("(?i)(\\d)([AP]M)$", "$1 $2");
        for (String format : new String[]{"H:mm:ss", "H:mm", "h:mm:ss a", "h:mm a"}) {
            try {
                return LocalTime.parse(text, new DateTimeFormatterBuilder().parseCaseInsensitive()
                        .appendPattern(format).toFormatter(Locale.ENGLISH).withResolverStyle(ResolverStyle.STRICT));
            } catch (DateTimeException ignored) { }
        }
        return null;
    }
}
