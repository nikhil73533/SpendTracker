package com.example.spendtracker.data.sms.extraction;

import com.example.spendtracker.data.sms.model.ExtractionResult;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts dates from SMS message body. Falls back to SMS timestamp when
 * no date is found in the message text.
 */
public class DateExtractor {

    private static final String[] DATE_FORMATS = {
        "dd-MMM-yy", "dd-MMM-yyyy",
        "dd/MM/yy", "dd/MM/yyyy",
        "dd-MM-yy", "dd-MM-yyyy",
        "yyyy-MM-dd",
        "ddMMMyyy", "ddMMMyy",
    };

    private static final Pattern DATE_PATTERN = Pattern.compile(
        "(\\d{1,2}[/-](?:[A-Za-z]{3}|\\d{2})[/-]\\d{2,4}|\\d{4}-\\d{2}-\\d{2}|\\d{2}[A-Za-z]{3}\\d{2,4})");

    public ExtractionResult<Long> extract(String normalizedMessage, long smsTimestamp) {
        if (normalizedMessage == null) return ExtractionResult.of(smsTimestamp, 0.70);

        Matcher m = DATE_PATTERN.matcher(normalizedMessage);
        if (m.find()) {
            String dateStr = m.group(1);
            for (String fmt : DATE_FORMATS) {
                try {
                    SimpleDateFormat sdf = new SimpleDateFormat(fmt, Locale.ENGLISH);
                    sdf.setLenient(false);
                    Date parsed = sdf.parse(dateStr);
                    if (parsed != null) {
                        return ExtractionResult.of(parsed.getTime(), 0.90);
                    }
                } catch (ParseException ignored) {}
            }
        }

        // Fallback: use SMS timestamp
        return ExtractionResult.of(smsTimestamp, 0.70);
    }
}
