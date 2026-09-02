package com.example.spendtracker.util;

import static org.junit.Assert.*;

import com.example.spendtracker.domain.model.Transaction;
import com.example.spendtracker.ui.dashboard.DashboardViewModel;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class PdfReportServiceTest {

    @Test
    public void testBuildPayload_calculatesTotalsAndSeparatesTransfers() {
        List<Transaction> transactions = new ArrayList<>();
        // Income
        transactions.add(new Transaction(1, 50000.0, "Salary", "Monthly Salary", "INCOME", System.currentTimeMillis(), "HDFC (Account)", "Employer", "", "", "HDFC", "Account"));
        // Expenses
        transactions.add(new Transaction(2, 2000.0, "Food", "Groceries", "EXPENSE", System.currentTimeMillis(), "HDFC (Account)", "", "", "Supermarket", "HDFC", "Account"));
        transactions.add(new Transaction(3, 3000.0, "Shopping", "Clothes", "EXPENSE", System.currentTimeMillis(), "HDFC (Credit Card)", "", "", "Mall", "HDFC", "Credit Card"));
        // Transfer (should be excluded from income/expense sums)
        transactions.add(new Transaction(4, 10000.0, "Transfer", "Self Transfer", "TRANSFER", System.currentTimeMillis(), "HDFC (Account)", "", "", "Self", "HDFC", "Account"));

        DashboardViewModel.TotalPageData data = new DashboardViewModel.TotalPageData(
                10, 2000.0, 3000.0, 10000.0, 10000.0, 0.0, 50000.0
        );

        PdfReportService.ReportPayload payload = PdfReportService.buildPayload(data, transactions, "August 2026");

        assertEquals("August 2026", payload.dateRangeLabel);
        assertEquals(50000.0, payload.totalIncome, 0.001);
        assertEquals(50000.0 - 5000.0, payload.netSavings, 0.001);
        assertEquals(5000.0, payload.totalExpense, 0.001);
        assertEquals(10000.0, payload.totalTransfers, 0.001);

        assertFalse("Category breakdown should be populated", payload.categoryBreakdown.isEmpty());
        assertFalse("Bank breakdown should be populated", payload.bankBreakdown.isEmpty());
        assertFalse("Source breakdown should be populated", payload.sourceTypeBreakdown.isEmpty());
    }
}
