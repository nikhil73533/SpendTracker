package com.example.spendtracker.ui.pdfimport;

import com.example.spendtracker.ui.pdfimport.parser.AUBankStatementParser;
import com.example.spendtracker.ui.pdfimport.parser.AxisStatementParser;
import com.example.spendtracker.ui.pdfimport.parser.HDFCStatementParser;
import com.example.spendtracker.ui.pdfimport.parser.HSBCStatementParser;
import com.example.spendtracker.ui.pdfimport.parser.ICICIStatementParser;
import com.example.spendtracker.ui.pdfimport.parser.RawTransactionRow;
import com.example.spendtracker.ui.pdfimport.parser.SBIStatementParser;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/** Regression fixtures transcribed from the supplied statement screenshots. */
public class StatementScreenshotLayoutsTest {

    @Test
    public void parsesIciciDepositBeforeWithdrawalColumnsAndSkipsBroughtForward() {
        String text = "ICICI Bank\n" +
                "DATE MODE PARTICULARS DEPOSITS WITHDRAWALS BALANCE\n" +
                "01-04-2019 B/F 65,731.31\n" +
                "02-04-2019 DEBIT CARD VPS/ACT/HYDERABAD 0.00 4,377.00 61,354.31\n" +
                "03-04-2019 IPS REF CASHLESS IN 19.95 61,374.26\n" +
                "03-04-2019 VISA REF SRI SAI BABA FILLINGS 15.00 61,389.26\n" +
                "03-04-2019 MOBILE BANKING MMT/IMPS/SHINE HAIR 29,600.00 90,989.26\n";

        List<RawTransactionRow> rows = new ICICIStatementParser().parse(text);

        assertEquals(4, rows.size());
        assertEquals(4377.00, rows.get(0).getDebitAmount(), 0.001);
        assertEquals(19.95, rows.get(1).getCreditAmount(), 0.001);
        assertEquals(15.00, rows.get(2).getCreditAmount(), 0.001);
        assertEquals(29600.00, rows.get(3).getCreditAmount(), 0.001);
    }

    @Test
    public void parsesHdfcTextMonthDatesAndDebitCreditColumns() {
        String text = "HDFC BANK BANK STATEMENT\n" +
                "Txn Date Value Date Description Ref No/Cheque No Debit Credit Balance\n" +
                "02 Jan 2019 02 Jan 2019 CARD PURCHASE REF100001 500.00 0.00 9,500.00\n" +
                "10 Jan 2019 10 Jan 2019 SALARY REF100002 0.00 25,000.00 34,500.00\n";

        List<RawTransactionRow> rows = new HDFCStatementParser().parse(text);

        assertEquals(2, rows.size());
        assertEquals(500.00, rows.get(0).getDebitAmount(), 0.001);
        assertEquals(25000.00, rows.get(1).getCreditAmount(), 0.001);
    }

    @Test
    public void parsesAxisDebitCreditTable() {
        String text = "AXIS BANK\n" +
                "Tran Date Chq No Particulars Debit Credit Balance Init. Br\n" +
                "01-10-2021 NEFT/IMB/AXMB/NISHAR 3299.00 0.00 31181.32\n" +
                "04-10-2021 RTGS/HDFC/SHREE ASHAPURA 0.00 402600.00 403712.37\n";

        List<RawTransactionRow> rows = new AxisStatementParser().parse(text);

        assertEquals(2, rows.size());
        assertEquals(3299.00, rows.get(0).getDebitAmount(), 0.001);
        assertEquals(402600.00, rows.get(1).getCreditAmount(), 0.001);
    }

    @Test
    public void parsesSbiAmountsWithDrCrSuffixAndBlankColumns() {
        String text = "STATE BANK OF INDIA STATEMENT OF ACCOUNT\n" +
                "Txn Date Value Date Description Ref/Cheque No Debit Credit Balance\n" +
                "01-08-23 01-08-23 NEFT*ICIC0000393*PHYSICSWALLAH 000000 - 164211.00 CR 171191.04 CR\n" +
                "01-08-23 01-08-23 COMM - OTHER MISC. SERVICES 000000 118.00 DR - 171073.04 CR\n";

        List<RawTransactionRow> rows = new SBIStatementParser().parse(text);

        assertEquals(2, rows.size());
        assertEquals(164211.00, rows.get(0).getCreditAmount(), 0.001);
        assertEquals(118.00, rows.get(1).getDebitAmount(), 0.001);
    }

    @Test
    public void parsesAuBankUpiDirectionsAndIndianGrouping() {
        String text = "AU SMALL FINANCE BANK ACCOUNT STATEMENT\n" +
                "Transaction Date Value Date Description/Narration Cheque/Reference No. Debit (₹) Credit (₹) Balance (₹)\n" +
                "01 Nov 2025 01 Nov 2025 UPI/CR/530596541294/AN UPAMA G AXI3862 - 350.00 358.71\n" +
                "01 Nov 2025 01 Nov 2025 UPI/DR/51335189609/ABDUL SALAM YBL36 340.00 - 18.71\n" +
                "04 Nov 2025 04 Nov 2025 UPI/CR/567405851850/AN UPAMA G AXI8 - 15,000.00 15,178.71\n";

        List<RawTransactionRow> rows = new AUBankStatementParser().parse(text);

        assertEquals(3, rows.size());
        assertEquals(350.00, rows.get(0).getCreditAmount(), 0.001);
        assertEquals(340.00, rows.get(1).getDebitAmount(), 0.001);
        assertEquals(15000.00, rows.get(2).getCreditAmount(), 0.001);
        assertNull(rows.get(1).getCreditAmount());
    }

    @Test
    public void parsesHsbcRowsThatInheritThePreviousPrintedDate() {
        String text = "HSBC UK Your Statement\n" +
                "Date Payment type and details Paid out Paid in Balance\n" +
                "14 Oct 20 BALANCE BROUGHT FORWARD 0.57\n" +
                "15 Oct 20 BP GBCLONTD construction 9,000.00 9,000.57\n" +
                "BP C FLOREA VICTOR POPA 1,225.00 7,775.57\n" +
                "BP V DIMINET FOR HELP 1,900.00 5,875.57\n";

        List<RawTransactionRow> rows = new HSBCStatementParser().parse(text);

        assertEquals(3, rows.size());
        assertEquals(9000.00, rows.get(0).getCreditAmount(), 0.001);
        assertEquals(1225.00, rows.get(1).getDebitAmount(), 0.001);
        assertEquals(1900.00, rows.get(2).getDebitAmount(), 0.001);
        assertEquals("15 Oct 20", rows.get(1).getDateStr());
    }
}
