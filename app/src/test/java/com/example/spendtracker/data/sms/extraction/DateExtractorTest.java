package com.example.spendtracker.data.sms.extraction;

import static org.junit.Assert.*;

import com.example.spendtracker.data.sms.model.ExtractionResult;
import java.util.Calendar;
import org.junit.Before;
import org.junit.Test;

public class DateExtractorTest {

    private DateExtractor extractor;

    @Before
    public void setUp() {
        extractor = new DateExtractor();
    }

    @Test
    public void testDateOnlyInText_mergesWithSmsArrivalTime() {
        // Prepare a specific SMS arrival timestamp: Aug 28, 2026 at 14:35:20
        Calendar smsArrival = Calendar.getInstance();
        smsArrival.set(2026, Calendar.AUGUST, 28, 14, 35, 20);
        smsArrival.set(Calendar.MILLISECOND, 0);
        long smsArrivalMillis = smsArrival.getTimeInMillis();

        String message = "Rs.500.00 debited from A/c XX1234 on 28-Aug-2026. Ref No 123456.";
        ExtractionResult<Long> result = extractor.extract(message, smsArrivalMillis);

        assertTrue("Extraction result should be present", result.isPresent());
        long resultMillis = result.getValue();

        Calendar resultCal = Calendar.getInstance();
        resultCal.setTimeInMillis(resultMillis);

        assertEquals(2026, resultCal.get(Calendar.YEAR));
        assertEquals(Calendar.AUGUST, resultCal.get(Calendar.MONTH));
        assertEquals(28, resultCal.get(Calendar.DAY_OF_MONTH));
        assertEquals(14, resultCal.get(Calendar.HOUR_OF_DAY));
        assertEquals(35, resultCal.get(Calendar.MINUTE));
        assertEquals(20, resultCal.get(Calendar.SECOND));
    }

    @Test
    public void testTwoSmsOnSameDay_haveDifferentTimestampsAndOrdering() {
        Calendar morningArrival = Calendar.getInstance();
        morningArrival.set(2026, Calendar.AUGUST, 28, 9, 15, 0);
        long morningMillis = morningArrival.getTimeInMillis();

        Calendar eveningArrival = Calendar.getInstance();
        eveningArrival.set(2026, Calendar.AUGUST, 28, 18, 45, 0);
        long eveningMillis = eveningArrival.getTimeInMillis();

        String morningMsg = "Rs.200 debited from A/c XX1234 on 28-Aug-2026.";
        String eveningMsg = "Rs.1500 debited from A/c XX1234 on 28-Aug-2026.";

        ExtractionResult<Long> morningResult = extractor.extract(morningMsg, morningMillis);
        ExtractionResult<Long> eveningResult = extractor.extract(eveningMsg, eveningMillis);

        assertTrue("Evening transaction should have a larger (later) timestamp than morning transaction",
            eveningResult.getValue() > morningResult.getValue());
    }

    @Test
    public void testExplicitTimeInText_preservesTextTime() {
        Calendar smsArrival = Calendar.getInstance();
        smsArrival.set(2026, Calendar.AUGUST, 28, 20, 0, 0);
        long smsArrivalMillis = smsArrival.getTimeInMillis();

        String message = "Rs.500.00 debited on 28-Aug-2026 10:30:00.";
        ExtractionResult<Long> result = extractor.extract(message, smsArrivalMillis);

        assertTrue(result.isPresent());
        Calendar resultCal = Calendar.getInstance();
        resultCal.setTimeInMillis(result.getValue());

        assertEquals(2026, resultCal.get(Calendar.YEAR));
        assertEquals(Calendar.AUGUST, resultCal.get(Calendar.MONTH));
        assertEquals(28, resultCal.get(Calendar.DAY_OF_MONTH));
        assertEquals(10, resultCal.get(Calendar.HOUR_OF_DAY));
        assertEquals(30, resultCal.get(Calendar.MINUTE));
    }

    @Test
    public void testNoDateInText_fallsBackToSmsArrivalTimestamp() {
        Calendar smsArrival = Calendar.getInstance();
        smsArrival.set(2026, Calendar.AUGUST, 28, 11, 22, 33);
        long smsArrivalMillis = smsArrival.getTimeInMillis();

        String message = "Rs.500.00 debited from A/c XX1234.";
        ExtractionResult<Long> result = extractor.extract(message, smsArrivalMillis);

        assertTrue(result.isPresent());
        assertEquals(smsArrivalMillis, (long) result.getValue());
    }
}
