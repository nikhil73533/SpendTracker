package com.example.spendtracker.data.repository;
import com.example.spendtracker.data.local.dao.TransactionDao;
import com.example.spendtracker.domain.model.Summary;
import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;
public class DashboardSummaryMapperTest {
    private TransactionDao.SummaryRow row(String type, String category, double total, int count) {
        TransactionDao.SummaryRow row = new TransactionDao.SummaryRow();
        row.type = type; row.category = category; row.total = total; row.transactionCount = count;
        return row;
    }
    @Test public void oneSnapshotPreservesWeightedAveragesAndTransferDirection() {
        TransactionDao.SummaryRow meta = row("__META", "", -125, 0); meta.accountTotal = 250;
        Summary summary = DashboardSummaryMapper.from(Arrays.asList(row("EXPENSE", "Food", 150, 3),
                row("EXPENSE", "Travel", 100, 1), row("INCOME", "Salary", 1000, 2), meta));
        assertEquals(250, summary.getTotalExpense(), 0);
        assertEquals(1000, summary.getTotalIncome(), 0);
        assertEquals(-125, summary.getTotalTransfer(), 0);
        assertEquals(250, summary.getTotalAccountTransaction(), 0);
        assertEquals(50, summary.getExpenseAverages().get("Food"), 0);
        assertEquals(500, summary.getIncomeAverages().get("Salary"), 0);
    }
    @Test public void emptySnapshotHasNoPhantomActivity() {
        Summary summary = DashboardSummaryMapper.from(null);
        assertEquals(0, summary.getNetBalance(), 0);
        assertTrue(summary.getExpenseBreakdown().isEmpty());
    }
}
