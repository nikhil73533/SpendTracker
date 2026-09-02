package com.example.spendtracker.ui.charts;

import static org.junit.Assert.*;

import com.example.spendtracker.domain.model.Transaction;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class TransferGroupFilterTest {

    private boolean isTransferTransaction(Transaction t) {
        if (t == null) return false;
        if ("TRANSFER".equalsIgnoreCase(t.getType())) return true;
        return t.getCategory() != null && t.getCategory().toLowerCase().contains("transfer");
    }

    @Test
    public void testBankGroupView_excludesTransfers() {
        List<Transaction> transactions = new ArrayList<>();
        transactions.add(new Transaction(1, 500.0, "Food", "Zomato", "EXPENSE", System.currentTimeMillis(), "HDFC (Account)", "", "", "Zomato", "HDFC", "Account"));
        transactions.add(new Transaction(2, 1000.0, "Transfer", "Self", "TRANSFER", System.currentTimeMillis(), "HDFC (Account)", "", "", "Self", "HDFC", "Account"));
        transactions.add(new Transaction(3, 2500.0, "Salary", "Salary", "INCOME", System.currentTimeMillis(), "HDFC (Account)", "Employer", "", "", "HDFC", "Account"));

        List<Transaction> bankFiltered = new ArrayList<>();
        double bankTotal = 0;
        for (Transaction t : transactions) {
            if ("HDFC".equalsIgnoreCase(t.getBankName()) && !isTransferTransaction(t)) {
                bankFiltered.add(t);
                bankTotal += t.getAmount();
            }
        }

        assertEquals("Should exclude the TRANSFER transaction", 2, bankFiltered.size());
        assertEquals("Total should be 500 + 2500 = 3000.0", 3000.0, bankTotal, 0.001);
    }

    @Test
    public void testAccountGroupView_excludesTransfers() {
        List<Transaction> transactions = new ArrayList<>();
        transactions.add(new Transaction(1, 1500.0, "Shopping", "Amazon", "EXPENSE", System.currentTimeMillis(), "HDFC (Credit Card)", "", "", "Amazon", "HDFC", "Credit Card"));
        transactions.add(new Transaction(2, 5000.0, "Transfer", "Card Payment", "TRANSFER", System.currentTimeMillis(), "HDFC (Credit Card)", "", "", "Card Payment", "HDFC", "Credit Card"));

        List<Transaction> accountFiltered = new ArrayList<>();
        double accountTotal = 0;
        String sourceTypeFilter = "Credit Card";

        for (Transaction t : transactions) {
            boolean isTransfer = isTransferTransaction(t);
            boolean matches = sourceTypeFilter.equalsIgnoreCase(t.getSourceType()) && !isTransfer;
            if (matches) {
                accountFiltered.add(t);
                accountTotal += t.getAmount();
            }
        }

        assertEquals("Should only include non-transfer transaction", 1, accountFiltered.size());
        assertEquals("Total should be 1500.0", 1500.0, accountTotal, 0.001);
    }
}
