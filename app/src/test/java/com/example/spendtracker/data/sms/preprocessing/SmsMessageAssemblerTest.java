package com.example.spendtracker.data.sms.preprocessing;

import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class SmsMessageAssemblerTest {
    @Test public void assemblesBeforeParsingWithoutInsertingCharacters() {
        List<SmsMessageAssembler.Part> result = SmsMessageAssembler.assemble(Arrays.asList(
                new SmsMessageAssembler.Part("HDFCBK", "INR 1,", 10),
                new SmsMessageAssembler.Part("HDFCBK", "500 debited from ", 11),
                new SmsMessageAssembler.Part("HDFCBK", "A/c XX1234.", 12),
                new SmsMessageAssembler.Part("SBIUPI", "Another message", 20)));
        assertEquals(2, result.size());
        assertEquals("INR 1,500 debited from A/c XX1234.", result.get(0).body);
        assertEquals(10, result.get(0).timestamp);
        assertEquals("Another message", result.get(1).body);
    }
}
