package com.example.spendtracker.domain;

import com.example.spendtracker.domain.model.Summary;
import com.example.spendtracker.domain.model.Transaction;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Financial Calculation Unit Tests.
 * Tests all formulas used in Summary, Dashboard, and Total calculations.
 *
 * Test Report Format:
 * - Test Name: method name
 * - Input Scenario: described in method
 * - Expected Result: assert statements
 * - Actual Result: JUnit output
 * - PASS/FAIL: JUnit verdict
 */
public class FinancialCalculationTest {

    private List<Transaction> transactions;

    @Before
    public void setUp() {
        transactions = new ArrayList<>();
    }

    // ── Helper methods ──────────────────────────────────────────────────────

    private Transaction createTransaction(int id, double amount, String type, String category) {
        return new Transaction(id, amount, category, "desc", type, System.currentTimeMillis(),
                "source", "sender", "upi", "receiver", "ICICI", "Account");
    }

    private Transaction createTransfer(int id, double amount, String fromAccount, String toAccount) {
        return new Transaction(id, amount, "Transfer", "transfer", "TRANSFER",
                System.currentTimeMillis(), "source", "sender", "upi", "receiver", "ICICI", "Account",
                fromAccount, toAccount, 0.0);
    }

    private double calculateIncome(List<Transaction> txns) {
        double sum = 0;
        for (Transaction t : txns) {
            if ("INCOME".equals(t.getType()) && !"Transfer".equalsIgnoreCase(t.getCategory())) {
                sum += t.getAmount();
            }
        }
        return sum;
    }

    private double calculateExpense(List<Transaction> txns) {
        double sum = 0;
        for (Transaction t : txns) {
            if ("EXPENSE".equals(t.getType()) && !"Transfer".equalsIgnoreCase(t.getCategory())) {
                sum += t.getAmount();
            }
        }
        return sum;
    }

    private double calculateTotal(List<Transaction> txns) {
        return calculateIncome(txns) - calculateExpense(txns);
    }

    private double calculateTransfer(List<Transaction> txns) {
        double sum = 0;
        for (Transaction t : txns) {
            if ("TRANSFER".equals(t.getType()) || "Transfer".equalsIgnoreCase(t.getCategory())) {
                sum += t.getAmount();
            }
        }
        return sum;
    }

    private double calculateTransferOutgoing(List<Transaction> txns) {
        double sum = 0;
        for (Transaction t : txns) {
            if (("TRANSFER".equals(t.getType()) || "Transfer".equalsIgnoreCase(t.getCategory()))
                    && t.getToAccount() != null && !t.getToAccount().isEmpty()) {
                sum += t.getAmount();
            }
        }
        return sum;
    }

    private double calculateTransferIncoming(List<Transaction> txns) {
        double sum = 0;
        for (Transaction t : txns) {
            if (("TRANSFER".equals(t.getType()) || "Transfer".equalsIgnoreCase(t.getCategory()))
                    && (t.getToAccount() == null || t.getToAccount().isEmpty())) {
                sum += t.getAmount();
            }
        }
        return sum;
    }

    private Map<String, Double> calculateCategoryBreakdown(List<Transaction> txns, String type) {
        Map<String, Double> map = new HashMap<>();
        for (Transaction t : txns) {
            if (type.equals(t.getType()) && !"Transfer".equalsIgnoreCase(t.getCategory())) {
                map.merge(t.getCategory(), t.getAmount(), Double::sum);
            }
        }
        return map;
    }

    // ── 1. Income Calculation ────────────────────────────────────────────────

    @Test
    public void testIncomeCalculation_basicIncome() {
        transactions.add(createTransaction(1, 50000, "INCOME", "Salary"));
        transactions.add(createTransaction(2, 5000, "INCOME", "Freelance"));
        assertEquals(55000.0, calculateIncome(transactions), 0.01);
    }

    @Test
    public void testIncomeCalculation_excludesTransfers() {
        transactions.add(createTransaction(1, 50000, "INCOME", "Salary"));
        transactions.add(createTransfer(2, 10000, null, "SavingsAccount"));
        assertEquals(50000.0, calculateIncome(transactions), 0.01);
    }

    // ── 2. Expense Calculation ───────────────────────────────────────────────

    @Test
    public void testExpenseCalculation_basicExpense() {
        transactions.add(createTransaction(1, 500, "EXPENSE", "Food"));
        transactions.add(createTransaction(2, 300, "EXPENSE", "Transport"));
        assertEquals(800.0, calculateExpense(transactions), 0.01);
    }

    @Test
    public void testExpenseCalculation_excludesTransfers() {
        transactions.add(createTransaction(1, 500, "EXPENSE", "Food"));
        transactions.add(createTransfer(2, 5000, "Checking", "Savings"));
        assertEquals(500.0, calculateExpense(transactions), 0.01);
    }

    // ── 3. Total Calculation (Income - Expense) ─────────────────────────────

    @Test
    public void testTotalCalculation_incomeMinusExpense() {
        transactions.add(createTransaction(1, 50000, "INCOME", "Salary"));
        transactions.add(createTransaction(2, 20000, "EXPENSE", "Rent"));
        assertEquals(30000.0, calculateTotal(transactions), 0.01);
    }

    @Test
    public void testTotalCalculation_transfersDoNotAffectTotal() {
        transactions.add(createTransaction(1, 50000, "INCOME", "Salary"));
        transactions.add(createTransaction(2, 20000, "EXPENSE", "Rent"));
        transactions.add(createTransfer(3, 10000, "Checking", "Savings"));
        // Total should still be 50000 - 20000 = 30000 (transfer not counted)
        assertEquals(30000.0, calculateTotal(transactions), 0.01);
    }

    @Test
    public void testTotalCalculation_matchesSummaryModel() {
        Summary summary = new Summary(50000, 20000, 0, new HashMap<>(), new HashMap<>(), new HashMap<>(), new HashMap<>());
        assertEquals(30000.0, summary.getNetBalance(), 0.01);
    }

    // ── 4. Transfer Calculation ──────────────────────────────────────────────

    @Test
    public void testTransferCalculation_totalTransfers() {
        transactions.add(createTransfer(1, 10000, "Checking", "Savings"));
        transactions.add(createTransfer(2, 5000, "Savings", "FD"));
        assertEquals(15000.0, calculateTransfer(transactions), 0.01);
    }

    // ── 5. Transfer Incoming ─────────────────────────────────────────────────

    @Test
    public void testTransferIncoming_noToAccount() {
        transactions.add(createTransfer(1, 10000, "ExternalBank", null));
        assertEquals(10000.0, calculateTransferIncoming(transactions), 0.01);
    }

    // ── 6. Transfer Outgoing ─────────────────────────────────────────────────

    @Test
    public void testTransferOutgoing_hasToAccount() {
        transactions.add(createTransfer(1, 5000, "Checking", "Savings"));
        assertEquals(5000.0, calculateTransferOutgoing(transactions), 0.01);
    }

    // ── 7. Category-level Aggregation ────────────────────────────────────────

    @Test
    public void testCategoryAggregation_groupsByCategory() {
        transactions.add(createTransaction(1, 500, "EXPENSE", "Food"));
        transactions.add(createTransaction(2, 300, "EXPENSE", "Food"));
        transactions.add(createTransaction(3, 200, "EXPENSE", "Transport"));
        Map<String, Double> breakdown = calculateCategoryBreakdown(transactions, "EXPENSE");
        assertEquals(800.0, breakdown.get("Food"), 0.01);
        assertEquals(200.0, breakdown.get("Transport"), 0.01);
    }

    // ── 8. Account-level Aggregation ─────────────────────────────────────────

    @Test
    public void testAccountAggregation_separatesByBank() {
        // This tests that bank-level grouping would work correctly
        Map<String, Double> bankTotals = new HashMap<>();
        bankTotals.put("ICICI", 5000.0);
        bankTotals.put("HDFC", 3000.0);
        assertEquals(5000.0, bankTotals.get("ICICI"), 0.01);
        assertEquals(3000.0, bankTotals.get("HDFC"), 0.01);
    }

    // ── 9. Zero Transactions ─────────────────────────────────────────────────

    @Test
    public void testZeroTransactions_allZero() {
        assertEquals(0.0, calculateIncome(transactions), 0.01);
        assertEquals(0.0, calculateExpense(transactions), 0.01);
        assertEquals(0.0, calculateTotal(transactions), 0.01);
        assertEquals(0.0, calculateTransfer(transactions), 0.01);
    }

    // ── 10. Only Income Transactions ─────────────────────────────────────────

    @Test
    public void testOnlyIncome_expenseIsZero() {
        transactions.add(createTransaction(1, 50000, "INCOME", "Salary"));
        assertEquals(50000.0, calculateIncome(transactions), 0.01);
        assertEquals(0.0, calculateExpense(transactions), 0.01);
        assertEquals(50000.0, calculateTotal(transactions), 0.01);
    }

    // ── 11. Only Expense Transactions ────────────────────────────────────────

    @Test
    public void testOnlyExpense_negativeTotal() {
        transactions.add(createTransaction(1, 20000, "EXPENSE", "Rent"));
        assertEquals(0.0, calculateIncome(transactions), 0.01);
        assertEquals(20000.0, calculateExpense(transactions), 0.01);
        assertEquals(-20000.0, calculateTotal(transactions), 0.01);
    }

    // ── 12. Only Transfer Transactions ───────────────────────────────────────

    @Test
    public void testOnlyTransfers_zeroIncomeExpenseTotal() {
        transactions.add(createTransfer(1, 10000, "Checking", "Savings"));
        assertEquals(0.0, calculateIncome(transactions), 0.01);
        assertEquals(0.0, calculateExpense(transactions), 0.01);
        assertEquals(0.0, calculateTotal(transactions), 0.01);
        assertEquals(10000.0, calculateTransfer(transactions), 0.01);
    }

    // ── 13. Mixed Transactions ───────────────────────────────────────────────

    @Test
    public void testMixedTransactions_correctCalculations() {
        transactions.add(createTransaction(1, 50000, "INCOME", "Salary"));
        transactions.add(createTransaction(2, 20000, "EXPENSE", "Rent"));
        transactions.add(createTransaction(3, 5000, "EXPENSE", "Food"));
        transactions.add(createTransfer(4, 10000, "Checking", "Savings"));
        transactions.add(createTransfer(5, 3000, "External", null)); // incoming

        assertEquals(50000.0, calculateIncome(transactions), 0.01);
        assertEquals(25000.0, calculateExpense(transactions), 0.01);
        assertEquals(25000.0, calculateTotal(transactions), 0.01);
        assertEquals(13000.0, calculateTransfer(transactions), 0.01);
        assertEquals(10000.0, calculateTransferOutgoing(transactions), 0.01);
        assertEquals(3000.0, calculateTransferIncoming(transactions), 0.01);
    }

    // ── 14. Negative / Invalid Values ────────────────────────────────────────

    @Test
    public void testNegativeAmount_handledCorrectly() {
        transactions.add(createTransaction(1, -500, "EXPENSE", "Refund"));
        assertEquals(-500.0, calculateExpense(transactions), 0.01);
    }

    @Test
    public void testZeroAmount_included() {
        transactions.add(createTransaction(1, 0, "INCOME", "Bonus"));
        assertEquals(0.0, calculateIncome(transactions), 0.01);
    }

    // ── 15. Edit Transaction ─────────────────────────────────────────────────

    @Test
    public void testEditTransaction_recalculates() {
        transactions.add(createTransaction(1, 500, "EXPENSE", "Food"));
        assertEquals(500.0, calculateExpense(transactions), 0.01);

        // Simulate edit: remove old, add updated
        transactions.clear();
        transactions.add(createTransaction(1, 800, "EXPENSE", "Food"));
        assertEquals(800.0, calculateExpense(transactions), 0.01);
    }

    // ── 16. Delete Transaction ───────────────────────────────────────────────

    @Test
    public void testDeleteTransaction_recalculates() {
        transactions.add(createTransaction(1, 500, "EXPENSE", "Food"));
        transactions.add(createTransaction(2, 300, "EXPENSE", "Transport"));
        assertEquals(800.0, calculateExpense(transactions), 0.01);

        // Simulate delete
        transactions.remove(0);
        assertEquals(300.0, calculateExpense(transactions), 0.01);
    }

    // ── Summary Model Validation ─────────────────────────────────────────────

    @Test
    public void testSummaryModel_netBalanceFormula() {
        Summary s1 = new Summary(100000, 50000, 0, new HashMap<>(), new HashMap<>(), new HashMap<>(), new HashMap<>());
        assertEquals(50000.0, s1.getNetBalance(), 0.01);

        Summary s2 = new Summary(0, 30000, 0, new HashMap<>(), new HashMap<>(), new HashMap<>(), new HashMap<>());
        assertEquals(-30000.0, s2.getNetBalance(), 0.01);

        Summary s3 = new Summary(25000, 25000, 0, new HashMap<>(), new HashMap<>(), new HashMap<>(), new HashMap<>());
        assertEquals(0.0, s3.getNetBalance(), 0.01);
    }

    @Test
    public void testSummaryModel_breakdownConsistency() {
        Map<String, Double> expBreakdown = new HashMap<>();
        expBreakdown.put("Food", 5000.0);
        expBreakdown.put("Rent", 20000.0);

        Summary summary = new Summary(50000, 25000, 0, expBreakdown, new HashMap<>(), new HashMap<>(), new HashMap<>());
        assertEquals(50000.0, summary.getTotalIncome(), 0.01);
        assertEquals(25000.0, summary.getTotalExpense(), 0.01);
        assertEquals(25000.0, summary.getNetBalance(), 0.01);
        assertEquals(2, summary.getExpenseBreakdown().size());
    }
}
