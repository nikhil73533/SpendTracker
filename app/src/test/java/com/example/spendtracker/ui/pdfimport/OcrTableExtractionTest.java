package com.example.spendtracker.ui.pdfimport;

import com.example.spendtracker.ui.pdfimport.ocr.OcrDocument;
import com.example.spendtracker.ui.pdfimport.ocr.OcrLine;
import com.example.spendtracker.ui.pdfimport.parser.GenericStatementParser;
import com.example.spendtracker.ui.pdfimport.parser.RawTransactionRow;
import com.example.spendtracker.ui.pdfimport.parser.StatementFields;
import org.junit.Test;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import static org.junit.Assert.*;

/** Word-box fixtures, rather than pre-flattened rows, exercise the OCR-to-parser boundary. */
public class OcrTableExtractionTest {
    private final List<OcrLine> words = new ArrayList<>();

    private void word(int page, String text, int x, int y, int width) {
        words.add(new OcrLine(page, text, x, y, x + width, y + 12));
    }
    private void header(int page, String debit, String credit) {
        word(page, "Txn Date", 10, 100, 70);
        word(page, "Value Date", 100, 100, 70);
        word(page, "Description", 200, 100, 110);
        word(page, "Ref No", 360, 100, 50);
        word(page, debit, 450, 100, 60);
        word(page, credit, 540, 100, 60);
        word(page, "Balance", 640, 100, 70);
    }
    private List<RawTransactionRow> parse() {
        // ML Kit block order is commonly column-major or otherwise unrelated to reading order.
        Collections.reverse(words);
        String text = new OcrDocument(words).getText();
        List<RawTransactionRow> result = new GenericStatementParser().parse(text);
        assertFalse("No reconstructed transactions:\n" + text, result.isEmpty());
        return result;
    }

    @Test public void preservesBlankColumnsEvenWithoutDirectionWordsOrPriorBalance() {
        header(1, "Debit", "Credit");
        word(1, "01/08/23", 10, 130, 66);
        word(1, "02/08/23", 100, 130, 66);
        word(1, "ACME", 200, 130, 50);
        word(1, "972969", 360, 130, 50);
        word(1, "1,64,211.00", 535, 131, 83);
        word(1, "1,71,191.04", 635, 132, 83);
        List<RawTransactionRow> rows = parse();
        assertEquals(1, rows.size());
        assertEquals("2023-08-01", rows.get(0).getDateStr());
        assertEquals(164211, rows.get(0).getCreditAmount(), .001);
        assertNull(rows.get(0).getDebitAmount());
        assertEquals("972969", rows.get(0).getReferenceNo());
    }

    @Test public void readsHdfcWithdrawalAmtAndDepositAmtAsWholeHeaders() {
        word(1, "Date", 10, 100, 70);
        word(1, "Narration", 180, 100, 95);
        word(1, "Withdrawal", 380, 100, 80);
        word(1, "Amt", 464, 100, 25);
        word(1, "Deposit", 535, 100, 60);
        word(1, "Amt", 599, 100, 25);
        word(1, "Balance", 680, 100, 60);
        word(1, "04/09/2026", 10, 140, 70);
        word(1, "SHOP", 185, 140, 45);
        word(1, "1250.00", 430, 140, 65);
        word(1, "8750.00", 680, 140, 65);
        List<RawTransactionRow> rows = parse();
        assertEquals(1, rows.size());
        assertEquals(1250, rows.get(0).getDebitAmount(), .001);
        assertNull(rows.get(0).getCreditAmount());
    }

    @Test public void usesTransactionDateEvenWhenValueDateIsPrintedFirst() {
        word(1, "Value Date", 10, 100, 70);
        word(1, "Transaction Date", 105, 100, 90);
        word(1, "Narration", 240, 100, 90);
        word(1, "Debit", 420, 100, 55);
        word(1, "Credit", 535, 100, 55);
        word(1, "Balance", 655, 100, 60);
        word(1, "06/09/2026", 10, 140, 70);
        word(1, "04/09/2026", 105, 140, 70);
        word(1, "SHOP", 245, 140, 45);
        word(1, "1250.00", 420, 140, 65);
        word(1, "8750.00", 655, 140, 65);
        List<RawTransactionRow> rows = parse();
        assertEquals("2026-09-04", rows.get(0).getDateStr());
        assertEquals(1250, rows.get(0).getDebitAmount(), .001);
    }

    @Test public void joinsWrappedNarrationAndSplitDrSuffixWithoutReadingBalanceCrAsIncome() {
        header(1, "Debit", "Credit");
        word(1, "01 Nov 2025", 8, 130, 82);
        word(1, "02 Nov 2025", 98, 130, 82);
        word(1, "UPI/DR/123456789012/", 195, 130, 134);
        word(1, "MERCHANT", 200, 146, 80);
        word(1, "PAYMENT", 200, 162, 65);
        word(1, "340.00", 460, 146, 45);
        word(1, "DR", 477, 162, 18);
        word(1, "-", 560, 146, 8);
        word(1, "18.71", 650, 146, 45);
        word(1, "CR", 700, 146, 18);
        word(1, "03 Nov 2025", 8, 190, 82);
        word(1, "03 Nov 2025", 98, 190, 82);
        word(1, "CASH", 200, 190, 45);
        word(1, "25", 470, 190, 20);
        word(1, "0", 560, 190, 10);
        word(1, "100", 660, 190, 28);
        List<RawTransactionRow> rows = parse();
        assertEquals(2, rows.size());
        assertEquals(340, rows.get(0).getDebitAmount(), .001);
        assertEquals(18.71, rows.get(0).getBalance(), .001);
        assertTrue(rows.get(0).getNarration().contains("MERCHANT PAYMENT"));
        assertEquals("123456789012", rows.get(0).getReferenceNo());
        assertEquals(25, rows.get(1).getDebitAmount(), .001);
    }

    @Test public void handlesIciciReversedColumnsAndContinuationPage() {
        header(1, "Deposits", "Withdrawals");
        word(1, "01-04-2019", 10, 130, 70);
        word(1, "B/F", 205, 130, 25);
        word(1, "65,731.31", 645, 130, 70);
        word(1, "02-04-2019", 10, 150, 70);
        word(1, "VPS/ACT", 205, 150, 70);
        word(1, "4377.00", 540, 150, 65);
        word(1, "61,354.31", 645, 150, 70);
        word(2, "03-04-2019", 10, 50, 70);
        word(2, "REFUND", 205, 50, 60);
        word(2, "19.95", 460, 50, 45);
        word(2, "61,374.26", 645, 50, 70);
        List<RawTransactionRow> rows = parse();
        assertEquals(2, rows.size());
        assertEquals(4377, rows.get(0).getDebitAmount(), .001);
        assertEquals(19.95, rows.get(1).getCreditAmount(), .001);
    }

    @Test public void handlesStackedHeadersAndWordLevelDates() {
        word(1, "Transaction", 10, 90, 70);
        word(1, "Value", 105, 90, 45);
        word(1, "Date", 28, 106, 32);
        word(1, "Date", 111, 106, 32);
        word(1, "Narration", 205, 98, 90);
        word(1, "Debit", 450, 98, 55);
        word(1, "Credit", 540, 98, 55);
        word(1, "Balance", 640, 98, 65);
        word(1, "04", 10, 135, 16);
        word(1, "Nov", 30, 135, 24);
        word(1, "2025", 58, 135, 30);
        word(1, "UPI/CR/SENDER", 205, 135, 110);
        word(1, "15,000.00", 535, 135, 75);
        word(1, "15,178.71", 635, 135, 75);
        List<RawTransactionRow> rows = parse();
        assertEquals("2025-11-04", rows.get(0).getDateStr());
        assertEquals(15000, rows.get(0).getCreditAmount(), .001);
    }

    @Test public void parsesSingleAmountAndTypeWithIndianSynonyms() {
        for (String type : Arrays.asList("Debited", "Withdrawal", "Expense", "Credited", "Deposited", "Income")) {
            String text = "DATE\tNARRATION\tAMOUNT\tTYPE\tBALANCE\n"
                    + "04/09/2026\tSENDER\t1500\t" + type + "\t99000 CR\n";
            RawTransactionRow row = new GenericStatementParser().parse(text).get(0);
            if (Arrays.asList("Debited", "Withdrawal", "Expense").contains(type))
                assertEquals(1500, row.getDebitAmount(), .001);
            else assertEquals(1500, row.getCreditAmount(), .001);
        }
    }

    @Test public void preservesDescriptionsAboveVerticallyCenteredDates() {
        header(1, "Debit", "Credit");
        word(1, "NEFT/COMPANY", 205, 128, 110);
        word(1, "PAYROLL", 205, 144, 70);
        word(1, "01-08-23", 10, 144, 70);
        word(1, "50000.00", 540, 144, 65);
        word(1, "51000.00", 640, 144, 65);
        word(1, "COMM - OTHER", 205, 176, 110);
        word(1, "MISC SERVICES", 205, 192, 110);
        word(1, "02-08-23", 10, 192, 70);
        word(1, "118.00", 460, 192, 50);
        word(1, "50882.00", 640, 192, 65);
        List<RawTransactionRow> rows = parse();
        assertEquals(2, rows.size());
        assertEquals("NEFT/COMPANY PAYROLL", rows.get(0).getNarration());
        assertEquals("COMM - OTHER MISC SERVICES", rows.get(1).getNarration());
        assertEquals(118, rows.get(1).getDebitAmount(), .001);
    }

    @Test public void includesSerialColumnWithoutUsingItAsDateOrAmount() {
        String text = "OTHER\tDATE\tNARRATION\tDEBIT\tCREDIT\tBALANCE\n"
                + "1\t04/09/2026\tMERCHANT\t99.00\t\t12345.67\n";
        List<RawTransactionRow> rows = new GenericStatementParser().parse(text);
        assertEquals(1, rows.size());
        assertEquals(99, rows.get(0).getDebitAmount(), .001);
        assertFalse(PdfParserService.needsOcrRetry(text, rows));
    }

    @Test public void balanceCrSuffixCannotReverseWithdrawalInPlainTextFallback() {
        List<RawTransactionRow> rows = new GenericStatementParser().parse(
                "Date Description Amount Balance\n04.09.2026 CASH WITHDRAWAL 100.00 900.00 CR\n");
        assertEquals(1, rows.size());
        assertEquals(100, rows.get(0).getDebitAmount(), .001);
        assertNull(rows.get(0).getCreditAmount());
    }

    @Test public void rejectsUnreadableAmountsConflictingCellsAndInvalidDates() {
        String text = "DATE\tNARRATION\tDEBIT\tCREDIT\tBALANCE\n"
                + "04/09/2026\tNAME\tXXXX\t\t12345.67\n"
                + "04/09/2026\tNAME\t100.00\t200.00\t12345.67\n"
                + "31/02/2026\tNAME\t100.00\t\t12345.67\n"
                + "04/09/2026\tNAME\t\t\t12345.67\n";
        List<RawTransactionRow> rows = new GenericStatementParser().parse(text);
        assertTrue(rows.isEmpty());
        assertTrue(PdfParserService.needsOcrRetry(text, rows));
    }

    @Test public void repairsOnlyNumericCellsAndParsesDatesStrictly() {
        assertEquals(1250, StatementFields.amount("1,25O.OO DR"), .001);
        assertEquals(164211, StatementFields.amount("₹ 1,64,211.00 CR"), .001);
        assertEquals("2026-09-04", StatementFields.date("O4 / O9 / 2O26"));
        assertNull(StatementFields.date("31/02/2026"));
        assertNull(StatementFields.date("04/09/2026garbage"));
        assertNull(StatementFields.amount("123456789012/NAME"));
        assertNull(StatementFields.amount("1,23.45"));
        assertNull(StatementFields.amount("100.00 200.00"));
    }
}
