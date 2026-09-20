package com.example.spendtracker.data.sms;

import org.junit.Test;
import java.time.*;
import static org.junit.Assert.*;

public class BillMessageParserTest {
    private final ZoneId zone = ZoneId.of("Asia/Kolkata");
    private final long received = LocalDate.of(2026, 9, 18).atTime(12, 0).atZone(zone).toInstant().toEpochMilli();
    private BillMessageParser.Result parse(String body) { return new BillMessageParser().parse(body, received, zone); }

    @Test public void extractsIndianCardBillTotalRatherThanMinimum() {
        BillMessageParser.Result bill = parse("HDFC card XX1234 bill generated. Minimum due INR 500. Total amount due Rs. 12,345.67. Due date: 25/09/2026.");
        assertTrue(bill.isBill);
        assertEquals(12345.67, bill.amount, .001);
        assertEquals(LocalDate.of(2026, 9, 25), bill.dueDate);
    }
    @Test public void supportsDueOnNamedDate() {
        assertEquals(LocalDate.of(2026, 9, 21), parse("Electricity bill ₹950 due on 21 September 2026").dueDate);
    }
    @Test public void supportsShortYear() {
        assertEquals(LocalDate.of(2026, 10, 2), parse("EMI INR 2,500 due on 02-10-26").dueDate);
    }
    @Test public void supportsIsoDate() {
        assertEquals(LocalDate.of(2026, 10, 2), parse("Premium Rs.500 due on 2026-10-02").dueDate);
    }
    @Test public void supportsTomorrow() {
        assertEquals(LocalDate.of(2026, 9, 19), parse("Bill Rs.500 due tomorrow").dueDate);
    }
    @Test public void supportsRelativeDays() {
        assertEquals(LocalDate.of(2026, 9, 21), parse("Subscription INR 500 due in 3 days").dueDate);
    }
    @Test public void supportsOrdinalAndMonthRollover() {
        assertEquals(LocalDate.of(2026, 10, 5), parse("Your bill Rs.500 due on 5th").dueDate);
    }
    @Test public void supportsNamedDateAcrossYearBoundary() {
        assertEquals(LocalDate.of(2027, 1, 5), parse("Your bill Rs.500 due on 5 Jan").dueDate);
    }
    @Test public void supportsPayBy() {
        assertEquals(LocalDate.of(2026, 9, 20), parse("Airtel bill INR 599, pay by 20 Sep 2026").dueDate);
    }
    @Test public void refusesInvalidDate() {
        assertNull(parse("Bill Rs.500 due on 31/02/2026").dueDate);
    }
    @Test public void doesNotUseStatementDateAsDueDate() {
        assertNull(parse("Your bill dated 18/09/2026 is INR 499").dueDate);
    }
    @Test public void leavesConflictingAmountsForReview() {
        assertEquals(0, parse("Bill Rs.500, credit limit Rs.50,000 due tomorrow").amount, 0);
    }
    @Test public void rejectsOtpAndSuccessfulPaymentAndMarketing() {
        for (String body : new String[]{"OTP 123456 for bill payment Rs.500",
                "Payment of INR 500 received for your bill", "Bill paid successfully Rs.500",
                "Congratulations! Win a subscription Rs.500", "Your card has no amount due"}) {
            assertFalse(body, parse(body).isBill);
        }
    }
    @Test public void rejectsOrdinaryTransaction() {
        assertFalse(parse("Your A/c XX1234 debited INR 500 at STORE on 18/09/2026").isBill);
    }
    @Test public void noCurrencyStillParsesLabeledTotal() {
        assertEquals(1599.50, parse("Bill amount: 1,599.50 due tomorrow").amount, .001);
    }
    @Test public void absentAmountRequiresReview() {
        assertEquals(0, parse("Your premium is due on 21 September 2026").amount, 0);
    }
    @Test public void supportsNamedMonthTwoDigitYearWithoutGuessingCurrentYear() {
        assertEquals(LocalDate.of(2027, 9, 25), parse("Bill Rs.500 due on 25-Sept-27").dueDate);
    }
    @Test public void partialNumericDateIsNotMistakenForDayOfMonth() {
        assertNull(parse("Bill Rs.500 due on 05/10").dueDate);
        assertNull(parse("Bill Rs.500 due on 25/09/202X").dueDate);
    }
    @Test public void amountSentencePunctuationDoesNotHideAmount() {
        assertEquals(500, parse("Your bill is Rs.500. Due tomorrow.").amount, 0);
    }
    @Test public void extractsTwelveAndTwentyFourHourDueTimesWithoutInventingOne() {
        assertEquals(18 * 60 + 30, parse("Electricity bill ₹950 due on 21 September 2026 by 6:30 PM").dueMinuteOfDay);
        assertEquals(9 * 60 + 5, parse("EMI INR 500 due tomorrow at 09:05").dueMinuteOfDay);
        assertEquals(-1, parse("Bill Rs.500 due tomorrow").dueMinuteOfDay);
    }
    @Test public void suggestsBillerForReviewWhenMessageNamesIt() {
        assertEquals("Airtel", parse("Bill from Airtel of ₹599 due tomorrow").biller);
    }
    @Test public void malformedDecimalIsNotSilentlyTruncated() {
        assertEquals(0, parse("Bill amount Rs.500.999 due tomorrow").amount, 0);
    }
}
