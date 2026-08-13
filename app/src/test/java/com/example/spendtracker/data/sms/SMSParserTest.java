package com.example.spendtracker.data.sms;

import static org.junit.Assert.*;
import com.example.spendtracker.domain.model.Transaction;
import java.util.ArrayList;
import org.junit.Test;

public class SMSParserTest {

    @Test
    public void testICICICreditCard() {
        SMSParser parser = new SMSParser();
        String msg = "ICICI Bank Credit Card XX7007 debited for INR 800.00 on 17-Jul-26 for UPI-656428212422-JAIN ENT. To dispute call 18001080/SMS BLOCK 7007 to 9215676766";
        Transaction t = parser.parseSMS("ICICI", msg, new ArrayList<>());
        
        assertNotNull(t);
        assertEquals(800.0, t.getAmount(), 0.001);
        assertEquals("JAIN ENT", t.getDescription());
        assertEquals("EXPENSE", t.getType());
        assertEquals("ICICI Bank (Credit Card)", t.getSource());
    }

    @Test
    public void testICICIAccount() {
        SMSParser parser = new SMSParser();
        String msg = "ICICI Bank Acct XX110 debited for Rs 10.00 on 02-Aug-26; TANISHA KHANDEL credited. UPI:658011591943. Call 18002662 for dispute. SMS BLOCK 110 to 9215676766.";
        Transaction t = parser.parseSMS("ICICI", msg, new ArrayList<>());
        
        assertNotNull(t);
        assertEquals(10.0, t.getAmount(), 0.001);
        assertEquals("TANISHA KHANDEL", t.getDescription());
        assertEquals("EXPENSE", t.getType());
        assertEquals("ICICI Bank (Account)", t.getSource());
    }

    @Test
    public void testAUBank() {
        SMSParser parser = new SMSParser();
        String msg = "Dr INR 148.00 - AU A/c X3698 02-AUG-2026 UPI/DR/687943750944/Aryan medical/YESB Fraud? Call 180012001200/SMS BLOCK UPI to 5676767";
        Transaction t = parser.parseSMS("AU-BANK", msg, new ArrayList<>());
        
        assertNotNull(t);
        assertEquals(148.0, t.getAmount(), 0.001);
        assertEquals("Aryan medical", t.getDescription());
        assertEquals("EXPENSE", t.getType());
        assertEquals("AU Bank (Account)", t.getSource());
    }
}
