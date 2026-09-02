package com.example.spendtracker.data.sms.extraction;

import com.example.spendtracker.data.sms.model.ExtractionResult;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts dates from SMS message body. When only a calendar date is present in the text,
 * merges it with the time component (hour, minute, second) from the SMS message arrival timestamp
 * so that transactions display their actual arrival time rather than defaulting to 12:00 AM.
 */
public class DateExtractor {

    private static final String[] DATE_TIME_FORMATS = {
        "dd-MMM-yyyy HH:mm:ss", "dd-MMM-yy HH:mm:ss",
        "dd-MMM-yyyy HH:mm", "dd-MMM-yy HH:mm",
        "dd/MM/yyyy HH:mm:ss", "dd/MM/yy HH:mm:ss",
        "dd/MM/yyyy HH:mm", "dd/MM/yy HH:mm",
        "yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd HH:mm",
        "dd-MMM-yyyy h:mm a", "dd-MMM-yy h:mm a",
        "dd/MM/yyyy h:mm a", "dd/MM/yy h:mm a",
        "dd-MM-yyyy HH:mm:ss", "dd-MM-yy HH:mm:ss",
        "dd-MM-yyyy HH:mm", "dd-MM-yy HH:mm",
        "dd-MMM-yy", "dd-MMM-yyyy",
        "dd/MM/yy", "dd/MM/yyyy",
        "dd-MM-yy", "dd-MM-yyyy",
        "yyyy-MM-dd",
        "ddMMMyyy", "ddMMMyy",
    };

    private static final Pattern DATE_PATTERN = Pattern.compile(
        "(\\d{1,2}[/-](?:[A-Za-z]{3}|\\d{2})[/-]\\d{2,4}(?:[T\\s]+\\d{1,2}:\\d{2}(?::\\d{2})?(?:\\s*[AP]M)?)?|\\d{4}-\\d{2}-\\d{2}(?:[T\\s]+\\d{1,2}:\\d{2}(?::\\d{2})?)?|\\d{2}[A-Za-z]{3}\\d{2,4})",
        Pattern.CASE_INSENSITIVE
    );

    public ExtractionResult<Long> extract(String normalizedMessage, long smsTimestamp) {
        if (normalizedMessage == null) return ExtractionResult.of(smsTimestamp <= 0 ? System.currentTimeMillis() : smsTimestamp, 0.70);

        long safeSmsTimestamp = smsTimestamp <= 0 ? System.currentTimeMillis() : smsTimestamp;

        Matcher m = DATE_PATTERN.matcher(normalizedMessage);
        if (m.find()) {
            String rawMatch = m.group(1);
            if (rawMatch != null) {
                String dateStr = rawMatch.trim();
                for (String fmt : DATE_TIME_FORMATS) {
                    try {
                        SimpleDateFormat sdf = new SimpleDateFormat(fmt, Locale.ENGLISH);
                        sdf.setLenient(false);
                        Date parsed = sdf.parse(dateStr);
                        if (parsed != null) {
                            boolean includesTime = fmt.contains("H") || fmt.contains("h");
                            long finalTimestamp = includesTime ? parsed.getTime() : mergeDateWithSmsTime(parsed.getTime(), safeSmsTimestamp);
                            return ExtractionResult.of(finalTimestamp, 0.90);
                        }
                    } catch (ParseException ignored) {}
                }
            }
        }

        // Fallback: use SMS timestamp
        return ExtractionResult.of(safeSmsTimestamp, 0.70);
    }

    /**
     * Merges the parsed date's year, month, and day with the hour, minute, second, and millisecond
     * from the SMS arrival timestamp.
     */
    private long mergeDateWithSmsTime(long parsedDateMillis, long smsTimestampMillis) {
        Calendar calParsed = Calendar.getInstance();
        calParsed.setTimeInMillis(parsedDateMillis);

        Calendar calSms = Calendar.getInstance();
        calSms.setTimeInMillis(smsTimestampMillis);

        calParsed.set(Calendar.HOUR_OF_DAY, calSms.get(Calendar.HOUR_OF_DAY));
        calParsed.set(Calendar.MINUTE, calSms.get(Calendar.MINUTE));
        calParsed.set(Calendar.SECOND, calSms.get(Calendar.SECOND));
        calParsed.set(Calendar.MILLISECOND, calSms.get(Calendar.MILLISECOND));

        return calParsed.getTimeInMillis();
    }
}
