package com.example.spendtracker.ui.pdfimport;

import com.example.spendtracker.ui.pdfimport.parser.GenericStatementParser;
import com.example.spendtracker.ui.pdfimport.parser.RawTransactionRow;
import com.example.spendtracker.ui.pdfimport.parser.StatementExtractionQuality;
import com.example.spendtracker.ui.pdfimport.parser.StatementFields;
import org.json.JSONObject;
import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import static org.junit.Assert.*;

public class StatementExtractionQualityTest {
    private static final String HEADER = "DATE\tNARRATION\tDEBIT\tCREDIT\tBALANCE\n";
    private List<RawTransactionRow> parse(String text) { return new GenericStatementParser().parse(text); }

    @Test public void retriesMissingDescriptionsEvenWhenAmountsWereParsed() {
        String text = HEADER + "2026-08-26\t230.00 199184.73\t230\t\t199184.73\n";
        assertTrue(StatementExtractionQuality.needsRetry(text, parse(text)));
    }

    @Test public void prefersRecoveredNamesWhenBothPassesHaveSameRowCount() throws Exception {
        String broken = HEADER + "2026-08-26\t230.00 199184.73\t230\t\t199184.73\n";
        String good = HEADER + "2026-08-26\tAmbe Pujan UPI/person@ybl/Paid via C/YES BANK\t230\t\t199184.73\n";
        JSONObject result = new PdfParserService().parsePageTexts(Collections.singletonList(broken),
                Collections.singletonMap(1, good), true);
        assertEquals("OCR", result.getString("extractionMethod"));
        assertEquals("Ambe Pujan", result.getJSONArray("transactions").getJSONObject(0).getString("merchant"));
        assertFalse(StatementExtractionQuality.prefer(good, parse(good), good, parse(good)));
    }

    @Test public void auditsRunningBalancesWithoutChangingPrintedAmounts() throws Exception {
        String text = HEADER + "2026-08-26\tSHOP\t230\t\t199184.73\n"
                + "2026-08-27\tREFUND\t\t60\t199190.73\n";
        assertEquals(1, StatementExtractionQuality.balanceMismatches(parse(text)));
        JSONObject result = new PdfParserService().parsePageTexts(Collections.singletonList(text), Collections.emptyMap(), false);
        assertTrue(result.has("warning"));
        assertEquals(60, result.getJSONArray("transactions").getJSONObject(1).getDouble("amount"), .001);
    }

    @Test public void supportsDescendingAndSameDayBalanceAudits() {
        String first = "2026-08-26\tSHOP\t230\t\t199184.73\n";
        String second = "2026-08-27\tREFUND\t\t6\t199190.73\n";
        for (String text : Arrays.asList(HEADER + first + second, HEADER + second + first,
                HEADER + second.replace("27", "26") + first))
            assertEquals(0, StatementExtractionQuality.balanceMismatches(parse(text)));
    }

    @Test public void reportsDatedCandidatesThatWereRejectedInsteadOfHidingMissingRows() throws Exception {
        String text = HEADER + "2026-08-26\tSHOP\t230\t\t199184.73\n"
                + "2026-08-27\tREFUND\t\t???\t199190.73\n";
        JSONObject result = new PdfParserService().parsePageTexts(Collections.singletonList(text), Collections.emptyMap(), false);
        assertEquals(2, result.getInt("totalFound"));
        assertEquals(1, result.getJSONArray("transactions").length());
        assertTrue(result.has("warning"));
        assertEquals(2, StatementFields.countDatedRows("1 26.08.2026 SHOP 230.00\n2 27.08.2026 REFUND 6.00\n"));
    }

    @Test public void preservesSeparateTimeColumnsAndDoesNotInventMissingTimes() throws Exception {
        String text = "DATE\tTIME\tNARRATION\tAMOUNT\tTYPE\n"
                + "26.08.2026\t11:06:10 PM\tSHOP\t230\tDR\n"
                + "27.08.2026\t\tREFUND\t6\tCR\n";
        JSONObject result = new PdfParserService().parsePageTexts(Collections.singletonList(text), Collections.emptyMap(), false);
        JSONObject first = result.getJSONArray("transactions").getJSONObject(0);
        assertEquals("11:06:10 PM", first.getString("time"));
        assertEquals("DATE_TIME", first.getString("timestampPrecision"));
        assertEquals("DATE_ONLY", result.getJSONArray("transactions").getJSONObject(1).getString("timestampPrecision"));
    }
}
