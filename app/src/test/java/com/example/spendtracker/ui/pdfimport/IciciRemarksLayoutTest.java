package com.example.spendtracker.ui.pdfimport;

import com.example.spendtracker.ui.pdfimport.ocr.OcrDocument;
import com.example.spendtracker.ui.pdfimport.ocr.OcrLine;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import static org.junit.Assert.*;

/** Geometry transcribed from the reported layout; identifiers are synthetic. */
public class IciciRemarksLayoutTest {
    private final List<OcrLine> words = new ArrayList<>();
    private void word(String text, int x, int y, int width) {
        words.add(new OcrLine(1, text, x, y, x + width, y + 9));
    }

    @Test public void keepsEveryTransactionAndItsNameWithCenteredRemarksHeader() throws Exception {
        word("ICICI Bank", 300, 180, 100);
        word("S No.", 53, 391, 25);
        word("Transaction", 94, 386, 62);
        word("Date", 110, 398, 24);
        word("Cheque Number", 164, 392, 72);
        word("Transaction Remarks", 300, 392, 100);
        word("Withdrawal", 465, 386, 61);
        word("Amount (INR)", 466, 398, 61);
        word("Deposit", 548, 386, 39);
        word("Amount (INR)", 539, 398, 61);
        word("Balance", 617, 386, 38);
        word("(INR)", 624, 398, 25);
        String[] names = {"Ambe Pujan", "JIO FINANCIAL SERV", "Radhika ge", "DEVRAJ KAH",
                "PAWAN MEEN", "Shri Vrind", "RUCHI KANU", "", "YOGESH KUM", "VINAY KHAN"};
        double[] amounts = {230, 6, 276, 580, 500, 160, 420, 2100, 1500, 4000};
        double[] balances = {199184.73, 199190.73, 198914.73, 198334.73, 197834.73,
                197674.73, 197254.73, 195154.73, 193654.73, 189654.73};
        String[] dates = {"26.08.2026", "26.08.2026", "27.08.2026", "27.08.2026",
                "28.08.2026", "28.08.2026", "28.08.2026", "28.08.2026", "28.08.2026", "29.08.2026"};
        for (int i = 0; i < names.length; i++) {
            int y = 424 + i * 65;
            word(Integer.toString(i + 1), 61, y, 9);
            word(dates[i], 96, y, 55);
            word(i == 1 ? "NACH trxn" : i == 7 ? "ATM trxn" : names[i], 241, y - 5, 88);
            word(i == 1 ? "ACH/JIO FINANCIAL SERV/261092782" : i == 7
                    ? "NFS/CASH WDL/624012012802/JAIPUR"
                    : "UPI/" + names[i] + "/person" + i + "@ybl/Paid via C/YES", 241, y + 10, 211);
            word("BANK", 241, y + 22, 30);
            word("L/660406298332/YCD4cd23860b0384680bc1e618", 241, y + 34, 214);
            word(String.format(java.util.Locale.ROOT, "%.2f", amounts[i]), i == 1 ? 579 : 500, y, 32);
            word(String.format(java.util.Locale.ROOT, "%.2f", balances[i]), 617, y, 45);
        }
        word("Sincerely,", 50, 1090, 55);
        word("Team ICICI Bank", 50, 1106, 100);
        Collections.reverse(words);
        String text = new OcrDocument(words).getText();
        JSONObject result = new PdfParserService().parsePageTexts(Collections.singletonList(text), Collections.emptyMap(), false);
        JSONArray rows = result.getJSONArray("transactions");
        assertEquals(text, 10, rows.length());
        for (int i = 0; i < names.length; i++) {
            JSONObject row = rows.getJSONObject(i);
            assertEquals(row.toString(), names[i], row.getString("merchant"));
            assertEquals(amounts[i], row.getDouble("amount"), .001);
            assertEquals(balances[i], row.getDouble("balance"), .001);
            assertEquals(i == 1 ? "CREDIT" : "DEBIT", row.getString("direction"));
            assertEquals("DATE_ONLY", row.getString("timestampPrecision"));
            assertTrue(row.isNull("time"));
            assertFalse(row.getString("narration").contains("Sincerely"));
        }
    }
}
