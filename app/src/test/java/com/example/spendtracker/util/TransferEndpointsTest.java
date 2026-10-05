package com.example.spendtracker.util;
import com.example.spendtracker.domain.model.Transaction;
import org.junit.Test;
import static org.junit.Assert.*;
public class TransferEndpointsTest {
    @Test public void incomingUsesSenderAndReceivingBank() {
        Transaction t=new Transaction(); t.setType("INCOME"); t.setDirection("CREDIT"); t.setCategory("Transfer"); t.setSender("CEL EBA"); t.setBankName("ICICI");
        TransferDirection.normalize(t); TransferDirection.fillEndpoints(t,"Alex");
        assertEquals("TRANSFER",t.getType()); assertEquals("CEL EBA",t.getFromAccount()); assertEquals("ICICI",t.getToAccount());
    }
    @Test public void outgoingFallsBackToProfileAndRetainsManualEndpoints() {
        Transaction t=new Transaction(); t.setType("EXPENSE"); t.setCategory("Transfer"); t.setReceiverName("Merchant");
        TransferDirection.normalize(t); TransferDirection.fillEndpoints(t,"Alex");
        assertEquals("Alex",t.getFromAccount()); assertEquals("Merchant",t.getToAccount());
        t.setToAccount("Savings"); TransferDirection.fillEndpoints(t,"Someone else"); assertEquals("Savings",t.getToAccount());
    }
    @Test public void transferCategoryRemainsProtected() {
        assertTrue(CategoryValidation.reserved(" Transfer ")); assertTrue(CategoryValidation.reserved("TRANSFER"));
    }
}
