package com.example.spendtracker.util;

import com.example.spendtracker.domain.model.Transaction;
import org.junit.Test;
import static org.junit.Assert.*;

public class TransferDirectionTest {
    @Test public void incomeCategoryCorrectionPreservesIncomingDirection() {
        Transaction t = new Transaction(); t.setType("INCOME"); t.setCategory("Transfer");
        t.setToAccount("My account");
        TransferDirection.normalize(t);
        assertEquals("TRANSFER", t.getType()); assertEquals("CREDIT", t.getDirection());
        assertTrue(TransferDirection.isIncoming(t));
        assertEquals("INCOME", CategoryPrediction.from(t).type);
    }
    @Test public void bankSmsSenderDoesNotMakeDebitIncoming() {
        Transaction t = new Transaction(); t.setType("EXPENSE"); t.setCategory("Transfer"); t.setSender("VM-BANK");
        TransferDirection.normalize(t);
        assertFalse(TransferDirection.isIncoming(t)); assertEquals("DEBIT", t.getDirection());
    }
    @Test public void explicitCreditWinsAndUnknownTransferDefaultsOutgoing() {
        Transaction t = new Transaction(); t.setType("TRANSFER");
        assertFalse(TransferDirection.isIncoming(t));
        t.setDirection("CREDIT"); t.setToAccount("Own account");
        TransferDirection.normalize(t); assertTrue(TransferDirection.isIncoming(t));
    }
    @Test public void manualTransactionsStartConfirmedAndZeroConfidenceIsRetained() {
        Transaction t = new Transaction(0, 10, "Food", "", "EXPENSE", 1, "", "", "", "", "", "");
        assertEquals(1.0, t.getConfidenceScore(), 0);
        t.setConfidenceScore(0); assertTrue(t.getConfidenceScore() < CategoryPrediction.REVIEW_THRESHOLD);
    }
    @Test public void initialsUseUppercaseFirstAndLastNames() {
        assertEquals("NP", new UserProfile("  nikhil   patil ", "", "").initials());
        assertEquals("N", new UserProfile("nikhil", "", "").initials());
        assertEquals("?", new UserProfile("", "", "").initials());
    }
}
