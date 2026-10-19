package com.example.spendtracker.data.sms.extraction;

import org.junit.Test;
import static org.junit.Assert.*;

public class CounterpartyExtractorTest {
    private final CounterpartyExtractor parser = new CounterpartyExtractor();
    @Test public void recoversPrintedHeadingWithCorroboratedOcrChannelError() {
        CounterpartyExtractor.Result result = parser.extract(
                "Ambe Pujan UPVAmbe Pujan/person0 @ybl/Paid via C/YES BANK");
        assertEquals("Ambe Pujan", result.name);
        assertEquals("person0@ybl", result.handle);
        assertEquals("STATEMENT_NAME", result.source);
        result = parser.extract("DEVRAJ KAH UP/DEVRAJ KAH/perso n3 @y bl/Paid via C/YES BANK");
        assertEquals("DEVRAJ KAH", result.name);
        assertEquals("person3@ybl", result.handle);
        assertEquals("", parser.extract("OTHER NAME UPVAmbe Pujan/person0@ybl/Paid via C/YES BANK").name);
    }
    @Test public void extractsPrintedHeadingsAndAchCounterparties() {
        assertEquals("RUCHI KANU", parser.extract("RUCHI KANU UPI/person@ybl/Paid via C/State Bank").name);
        assertEquals("JIO FINANCIAL SERV", parser.extract("NACH trxn ACH/JIO FINANCIAL SERV/261092782").name);
        assertEquals("ANITA", parser.extract("Online payment UPI/DR/123456789012/ANITA/YESB").name);
    }
    @Test public void recognizesBankNarrationFamilies() {
        String[][] fixtures = {
            {"UPI/123456789012/UPI/RAVI KUMAR/State Bank Of India", "RAVI KUMAR"},
            {"MMT/IMPS/123456789012/SHINE HAIR/AXIS BANK LTD", "SHINE HAIR"},
            {"VPS/ACT/202609161234/123456789012/HYDERABAD", "ACT"},
            {"UPI/DR/123456789012/ANITA/YESB", "ANITA"},
            {"UPI/CR/123456789012/ANITA/C/CNRB/001234", "ANITA"},
            {"NEFT/MB/AXMB123456789012/RAVI", "RAVI"},
            {"NEFT*ICIC0000393*CMS3461768763*PHYSICSWALLAH", "PHYSICSWALLAH"},
            {"UPI-SWIGGY-123456789012-SWIGGY@OKAXIS", "SWIGGY"},
            {"UPI/DR/123456789012/श्री मेडिकल/YESB", "श्री मेडिकल"},
            {"UPI/DR/123456789012/BANK STREET CAFE/YESB", "BANK STREET CAFE"},
            {"POS/ANITA'S VERY LONG MERCHANT NAME PRIVATE LIMITED/123456", "ANITA'S VERY LONG MERCHANT NAME PRIVATE LIMITED"},
            {"UPI/DR/123456789012/ANITA\n KUMAR/YESB", "ANITA KUMAR"}
        };
        for (String[] row : fixtures) assertEquals(row[0], row[1], parser.extract(row[0]).name);
    }
    @Test public void handleDoesNotBecomeInventedPersonalName() {
        CounterpartyExtractor.Result result = parser.extract("UPI/123456789012/UPI/ravi@okaxis/State Bank Of India");
        assertEquals("", result.name);
        assertEquals("ravi@okaxis", result.displayName());
        assertEquals("VPA_FALLBACK", result.source);
    }
    @Test public void missingCounterpartyDoesNotReturnChannelOrReference() {
        for (String text : new String[]{"UPI/123456789012/UPI", "MMT/IMPS/123456789012",
                "NEFT/MB/AXMB123456789012", "ATM CASH WITHDRAWAL", "OPENING BALANCE", "123456789012"}) {
            assertEquals(text, "", parser.extract(text).displayName());
        }
    }
}
