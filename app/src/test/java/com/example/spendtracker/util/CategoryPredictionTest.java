package com.example.spendtracker.util;

import com.example.prediction.domain.model.PredictionTransaction;
import com.example.spendtracker.domain.model.Transaction;
import org.junit.Test;
import static org.junit.Assert.*;

public class CategoryPredictionTest {
    @Test public void incomeUsesPayerAndRetainsNarration() {
        Transaction tx = new Transaction();
        tx.setType("INCOME"); tx.setSender("Acme Payroll"); tx.setReceiverName(""); tx.setDescription("Salary September");
        PredictionTransaction features = CategoryPrediction.from(tx);
        assertEquals("Acme Payroll", features.merchantName);
        assertEquals("Salary September", features.description);
    }
    @Test public void expenseUsesReceiverRatherThanBankSmsSender() {
        Transaction tx = new Transaction();
        tx.setType("EXPENSE"); tx.setSender("VM-HDFCBK"); tx.setReceiverName("Zomato"); tx.setUpiId("zomato@ybl");
        PredictionTransaction features = CategoryPrediction.from(tx);
        assertEquals("Zomato", features.merchantName);
        assertEquals("zomato@ybl", features.upiId);
    }
}
