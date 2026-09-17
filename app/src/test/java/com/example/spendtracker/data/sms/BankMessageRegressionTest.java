package com.example.spendtracker.data.sms;

import com.example.spendtracker.data.sms.config.*;
import com.example.spendtracker.data.sms.detection.*;
import com.example.spendtracker.data.sms.duplicate.DuplicateDetector;
import com.example.spendtracker.data.sms.extraction.*;
import com.example.spendtracker.data.sms.model.*;
import com.example.spendtracker.data.sms.normalization.*;
import com.example.spendtracker.data.sms.preprocessing.SMSPreprocessor;
import com.example.spendtracker.data.sms.validation.TransactionValidator;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import org.junit.*;
import static org.junit.Assert.*;

/** Synthetic adversarial regressions; not a real-world accuracy benchmark. */
public class BankMessageRegressionTest {
    private SMSParsingService service;
    private final long arrival = ZonedDateTime.of(2026, 9, 17, 22, 0, 0, 0, ZoneId.systemDefault()).toInstant().toEpochMilli();

    @Before public void setup() throws Exception {
        Path dir = Paths.get("src/main/assets/bank_configs");
        if (!Files.isDirectory(dir)) dir = Paths.get("app/src/main/assets/bank_configs");
        List<BankConfig> configs = new ArrayList<>();
        try (java.util.stream.Stream<Path> paths = Files.list(dir)) {
            for (Path path : (Iterable<Path>) paths.filter(p -> p.toString().endsWith(".json"))::iterator) {
                BankConfig config = BankConfigProvider.parseJson(new String(Files.readAllBytes(path), java.nio.charset.StandardCharsets.UTF_8));
                assertNotNull("Invalid production config: " + path, config);
                configs.add(config);
            }
        }
        assertEquals(10, configs.size());
        service = new SMSParsingService(new SMSPreprocessor(), new TransactionDetector(),
                new BankIdentifier(), new AmountExtractor(), new TransactionTypeExtractor(), new MerchantExtractor(),
                new AccountExtractor(), new UpiExtractor(), new DateExtractor(), new SourceTypeExtractor(),
                new TransactionStatusExtractor(), new BankNormalizer(), new MerchantNormalizer(),
                new TransactionValidator(), new DuplicateDetector(), new BankConfigProvider(configs));
    }

    @Test public void rejectsAuthorizationAndNonPostedMessagesEvenWithProductionConfigs() {
        String[] messages = {
            "OTP 123456 for purchase of Rs.500 using your debit card XX1234. Do not share OTP.",
            "123456 is your OTP for Rs 500 spent on HDFC Card XX1234.",
            "Your A/c XX1234 will be debited INR 1500 for UPI mandate on 18-Sep-2026.",
            "INR 500 will be credited to A/c XX1234.",
            "Your A/c will be\n debited INR 500 tomorrow.",
            "On approving the request INR 500 will be debited from A/c XX1234.",
            "RAVI requested money of Rs 500. Approve the request.",
            "Your credit card total amount due is Rs 500. Pay by 20-Sep-26.",
            "Available balance Rs 5,000.00 in your A/c XX1234.",
            "Every Rs 150 spent on your credit card earns reward points.",
            "Rs 500 paid last month. Your payment due this month is Rs 1000.",
            "Your dispute for Rs 500 is not eligible for refund."
        };
        for (String message : messages) assertFalse(message, service.parse("HDFCBK", message, arrival).isSuccess());
    }

    @Test public void choosesDebitNotBalanceOrLimit() {
        for (String message : new String[]{
                "Avl Bal Rs.9000. A/c XX1234 debited Rs.500 at ACME on 17-Sep-2026.",
                "Available limit: INR 9000. Rs.500 spent on HDFC Bank Card x1234 at ACME on 17-Sep-2026.",
                "Rs.500 debited from A/c XX1234. Avl Bal Rs.9000."}) {
            ParseResult r = service.parse("HDFCBK", message, arrival);
            assertTrue(message, r.isSuccess());
            assertEquals(500, r.getTransaction().getAmount(), .001);
        }
    }

    @Test public void currencylessSbiUsesActualAssetAndPreservesReference() {
        ParseResult r = service.parse("AD-SBIUPI-S",
                "Dear UPI user A/C X1234 debited by 1046.0 on date 16Sep26 trf to ACME Refno 123456789012.", arrival);
        assertTrue(r.isSuccess());
        assertEquals("SBI:Account Debit", r.getMatchedPattern());
        assertEquals(1046, r.getTransaction().getAmount(), .001);
        assertEquals("EXPENSE", r.getTransaction().getType());
        assertEquals("123456789012", r.getTransaction().getReferenceNumber());
        assertEquals("Acme", r.getTransaction().getReceiverName());
    }

    @Test public void completedRefundOverridesHistoricalFailureAndCardNoun() {
        for (String text : new String[]{
                "INR 500 credited back to A/c XX1234 for failed UPI transaction on 16-Sep-2026.",
                "Refund of INR 500 credited to your debit card XX1234 on 16-Sep-2026."}) {
            ParseResult r = service.parse("ICICIB", text, arrival);
            assertTrue(text, r.isSuccess());
            assertEquals("INCOME", r.getTransaction().getType());
        }
        ParseResult income = service.parse("ICICIB", "INR 500 credited to A/c XX1234 from RAVI on 16-Sep-2026.", arrival);
        assertTrue(income.isSuccess());
        assertEquals("Ravi", income.getTransaction().getSender());
    }

    @Test public void pendingAndFailedDoNotProduceTransactions() {
        ParseResult pending = service.parse("ICICIB", "A/c XX1234 debited Rs.500 via UPI; transaction pending.", arrival);
        assertEquals(ParseStatus.PENDING_TRANSACTION, pending.getStatus());
        assertNull(pending.getTransaction());
        ParseResult failed = service.parse("HDFCBK", "Transaction Rs 500 failed. Amount debited will be refunded.", arrival);
        assertEquals(ParseStatus.FAILED_TRANSACTION, failed.getStatus());
        assertNull(failed.getTransaction());
        assertFalse(service.parse("HDFCBK", "INR 500 has not been credited to A/c XX1234.", arrival).isSuccess());
    }

    @Test public void transferRailIsNotOwnership() {
        ParseResult r = service.parse("HDFCBK", "Rs 500 transferred from A/c XX1234 to A/c XX5678.", arrival);
        assertTrue(r.isSuccess());
        assertEquals("EXPENSE", r.getTransaction().getType());
    }

    @Test public void safetyFooterDoesNotRejectPostedPayment() {
        ParseResult r = service.parse("ICICIB",
                "ICICI Bank Acct XX123 debited for Rs 500.00 on 16-Sep-26; ACME STORE credited. UPI:123456789012. Do not share OTP.", arrival);
        assertTrue(r.isSuccess());
        assertEquals("ICICI:Account Debit", r.getMatchedPattern());
        assertEquals("EXPENSE", r.getTransaction().getType());
        assertEquals("Acme Store", r.getTransaction().getReceiverName());
        assertEquals("123456789012", r.getTransaction().getReferenceNumber());
    }

    @Test public void datesAreStrictAndPrintedTimesPreserved() {
        DateExtractor extractor = new DateExtractor();
        for (String text : new String[]{"on 2026-09-15:19:19:33", "on 15 Sep 2026 at 7:19:33 PM",
                "on 15/09/2026 19:19:33"}) {
            DateExtractor.Result result = extractor.extractDetailed(text, arrival);
            LocalDateTime time = Instant.ofEpochMilli(result.timestamp).atZone(ZoneId.systemDefault()).toLocalDateTime();
            assertEquals(text, LocalDate.of(2026, 9, 15), time.toLocalDate());
            assertEquals(text, LocalTime.of(19, 19, 33), time.toLocalTime());
            assertEquals("DATE_TIME", result.precision);
        }
        assertEquals("SMS_RECEIVED", extractor.extractDetailed("on 31-Feb-2026", arrival).precision);
        assertEquals("DATE_ONLY", extractor.extractDetailed("on 15-Sep-26", arrival).precision);
        assertEquals("SMS_RECEIVED", extractor.extractDetailed("No date", arrival).precision);
    }

    @Test public void ambiguousOrMalformedAmountsAreNotSilentlyAccepted() {
        for (String text : new String[]{"Rs 1,2,3 debited", "INR 10.123 debited",
                "INR 500 debited and INR 700 debited", "Rs 0 debited"}) {
            assertFalse(text, service.parse("HDFCBK", text, arrival).isSuccess());
        }
        assertEquals(ParseStatus.INVALID, service.parse("HDFCBK",
                "INR 500 debited and INR 700 debited", arrival).getStatus());
    }

    @Test public void senderSuffixAndFallbackFormatsWork() {
        for (String text : new String[]{
                "Amt Sent Rs.500 From HDFC Bank A/C *1234 To ACME On 16-09 Ref 123456789012 Not You?",
                "Used Rs500 On HDFCBank Card 1234 At ACME by UPI 123456789012 On 16-09",
                "INR 500.00 spent using ICICI Bank Card XX1234 on 16-Sep-26 on ACME. Avl Limit: INR 9,000.00."}) {
            ParseResult r = service.parse("AD-HDFCBK-S", text, arrival);
            assertTrue(text, r.isSuccess());
            assertEquals(500, r.getTransaction().getAmount(), .001);
            assertEquals("EXPENSE", r.getTransaction().getType());
        }
    }

    @Test public void productionBankAssetsAreActuallyExercisedAcrossIssuers() {
        String[][] rows = {
            {"HDFCBK", "Rs.500 debited from A/c XX1234."},
            {"AXISBK", "INR 500 has been debited from your Axis Bank A/c XX1234."},
            {"KOTAKB", "Rs.500 debited from Kotak A/c XX1234."},
            {"IDFC", "Rs.500 debited from IDFC A/c XX1234."},
            {"YESBK", "Rs.500 debited from YES BANK A/c XX1234."},
            {"INDBNK", "Rs.500 debited from IndusInd A/c XX1234."},
            {"PNBSMS", "PNB A/C XX1234 debited by Rs 500."},
            {"AU-BANK", "Dr INR 500 - AU A/c X1234 16-SEP-2026 UPI/DR/123456789012/ANITA/YESB"}
        };
        for (String[] row : rows) {
            ParseResult r = service.parse(row[0], row[1], arrival);
            assertTrue(row[0], r.isSuccess());
            assertNotEquals(row[0], "generic", r.getMatchedPattern());
            assertEquals(row[0], 500, r.getTransaction().getAmount(), .001);
            assertEquals(row[0], "EXPENSE", r.getTransaction().getType());
        }
    }
}
