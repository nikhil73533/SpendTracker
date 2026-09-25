package com.example.spendtracker.util;

import static org.junit.Assert.*;

import com.example.spendtracker.domain.model.Transaction;
import com.example.spendtracker.ui.dashboard.DashboardViewModel;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class PdfReportServiceTest {
    @Test public void accountReportExcludesCardsIncomeTransfersAndDeletedRows() {
        List<Transaction> transactions = new ArrayList<>();
        String[] types = {"EXPENSE", "EXPENSE", "INCOME", "TRANSFER", "EXPENSE", "EXPENSE"};
        String[] sources = {"Account", "Credit Card", "Account", "Account", "Account", "Cash"};
        for (int i = 0; i < types.length; i++) {
            Transaction transaction = new Transaction(i + 1, 100, "Food", "Test", types[i], 1L,
                    "Test Bank", "", "", "Test", "Test Bank", sources[i]);
            if (i == 4) transaction.setStatus("DELETED");
            transactions.add(transaction);
        }
        transactions.add(new Transaction(7, 300, "Self Transfer", "Test", "EXPENSE", 1L,
                "Test Bank", "", "", "Savings", "Test Bank", "Account"));
        PdfReportService.ReportPayload payload = PdfReportService.buildExpenseAccountPayload(transactions, "September");
        assertTrue(payload.expenseAccountsOnly);
        assertEquals(1, payload.transactions.size());
        assertEquals(100, payload.accountExpenses, 0.001);
        assertEquals(0, payload.totalIncome, 0.001);
        assertEquals(0, payload.cardExpenses, 0.001);
        assertEquals(0, payload.totalTransfers, 0.001);
    }

    @Test(expected = IllegalArgumentException.class)
    public void emptyAccountReportDoesNotCreateMisleadingPdf() {
        PdfReportService.buildExpenseAccountPayload(java.util.Collections.emptyList(), "September");
    }

    @Test
    public void testBuildPayload_calculatesTotalsAndSeparatesTransfers() {
        List<Transaction> transactions = new ArrayList<>();
        // Income
        transactions.add(new Transaction(1, 50000.0, "Salary", "Monthly Salary", "INCOME", System.currentTimeMillis(), "HDFC (Account)", "Employer", "", "", "HDFC", "Account"));
        // Expenses
        transactions.add(new Transaction(2, 2000.0, "Food", "Groceries", "EXPENSE", System.currentTimeMillis(), "HDFC (Account)", "", "", "Supermarket", "HDFC", "Account"));
        transactions.add(new Transaction(3, 3000.0, "Shopping", "Clothes", "EXPENSE", System.currentTimeMillis(), "HDFC (Credit Card)", "", "", "Mall", "HDFC", "Credit Card"));
        // Transfer (should be excluded from income/expense sums)
        Transaction incomingTransfer = new Transaction(4, 10000.0, "Transfer", "Self Transfer", "TRANSFER", System.currentTimeMillis(), "HDFC (Account)", "", "", "Self", "HDFC", "Account");
        incomingTransfer.setDirection("CREDIT");
        transactions.add(incomingTransfer);

        DashboardViewModel.TotalPageData data = new DashboardViewModel.TotalPageData(
                10, 2000.0, 3000.0, 10000.0, 10000.0, 0.0, 50000.0
        );

        PdfReportService.ReportPayload payload = PdfReportService.buildPayload(data, transactions, "August 2026");

        assertEquals("August 2026", payload.dateRangeLabel);
        assertEquals(50000.0, payload.totalIncome, 0.001);
        assertEquals(50000.0 - 5000.0, payload.netSavings, 0.001);
        assertEquals(5000.0, payload.totalExpense, 0.001);
        assertEquals(10000.0, payload.totalTransfers, 0.001);
        assertEquals(10000.0, payload.transferIncoming, 0.001);
        assertEquals(0.0, payload.transferOutgoing, 0.001);

        assertFalse("Category breakdown should be populated", payload.categoryBreakdown.isEmpty());
        assertFalse("Bank breakdown should be populated", payload.bankBreakdown.isEmpty());
        assertFalse("Source breakdown should be populated", payload.sourceTypeBreakdown.isEmpty());
    }

    @Test
    public void testBuildPayload_usesItsTransactionRowsInsteadOfStaleDashboardTotals() {
        Transaction expense = new Transaction(1, 250.0, "Food", "Lunch", "EXPENSE", 1L, "UPI", "", "", "Cafe", "Bank", "Account");
        Transaction incoming = new Transaction(2, 700.0, "Transfer", "Refund", "TRANSFER", 2L, "UPI", "Bank", "", "", "Bank", "Account");
        incoming.setDirection("CREDIT");
        Transaction outgoing = new Transaction(3, 100.0, "Transfer", "To savings", "TRANSFER", 3L, "UPI", "", "", "Savings", "Bank", "Account");
        outgoing.setDirection("DEBIT");

        DashboardViewModel.TotalPageData stale = new DashboardViewModel.TotalPageData(0, 999, 999, 999, 999, 0, 999);
        PdfReportService.ReportPayload payload = PdfReportService.buildPayload(
                stale, java.util.Arrays.asList(expense, incoming, outgoing), "Test period");

        assertEquals(250.0, payload.totalExpense, 0.001);
        assertEquals(0.0, payload.totalIncome, 0.001);
        assertEquals(700.0, payload.transferIncoming, 0.001);
        assertEquals(100.0, payload.transferOutgoing, 0.001);
        assertEquals(600.0, payload.totalTransfers, 0.001);
        assertEquals(-250.0, payload.netSavings, 0.001);
    }
}
