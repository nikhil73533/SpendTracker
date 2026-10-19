package com.example.spendtracker.data.sms;

import android.content.Context;
import com.example.spendtracker.data.local.dao.BillAlertDao;
import com.example.spendtracker.data.local.entity.BillAlertEntity;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import java.time.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

public class BillPersistenceTest {
    private final BillAlertDao dao = mock(BillAlertDao.class);
    private final Runnable schedule = mock(Runnable.class);
    private final AlertParsingService service = new AlertParsingService(dao, mock(Context.class), schedule);
    private final long received = LocalDate.of(2026, 9, 18).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();

    @Test public void ordinaryMessagesNeverBecomeBillAlerts() {
        service.processMessage("BANK", "A/c debited INR 500 at Shop", received);
        verifyNoInteractions(dao, schedule);
    }
    @Test public void billIsPersistedSynchronouslyBeforeScheduling() {
        service.processMessage("BANK", "Your bill INR 500 due on 25 Sep 2026", received);
        ArgumentCaptor<BillAlertEntity> row = ArgumentCaptor.forClass(BillAlertEntity.class);
        org.mockito.InOrder order = inOrder(dao, schedule);
        order.verify(dao).findBill(eq("BANK"), anyString(), eq(LocalDate.of(2026, 9, 25).toEpochDay()));
        order.verify(dao).insert(row.capture());
        order.verify(schedule).run();
        assertEquals(500, row.getValue().amount, 0);
        assertEquals(LocalDate.of(2026, 9, 25).toEpochDay(), row.getValue().dueEpochDay);
        assertTrue(row.getValue().createdAt > 0);
    }
    @Test public void repeatedMessageDoesNotInsertOrReopenPaidBill() {
        BillAlertEntity bill = new BillAlertEntity(); bill.isResolved = true; bill.occurrenceCount = 1;
        when(dao.findBill(anyString(), anyString(), anyLong())).thenReturn(bill);
        service.processMessage("BANK", "Your bill INR 500 due on 25 Sep 2026", received);
        verify(dao, never()).insert(any());
        verify(dao).update(bill);
        assertTrue(bill.isResolved);
    }
    @Test public void unknownDateIsKeptForReviewNotInvented() {
        service.processMessage("BANK", "Your bill amount is INR 500", received);
        ArgumentCaptor<BillAlertEntity> row = ArgumentCaptor.forClass(BillAlertEntity.class);
        verify(dao).insert(row.capture());
        assertEquals(0, row.getValue().dueEpochDay);
    }
    @Test public void separateAccountsHaveSeparateIdentities() {
        service.processMessage("BANK", "Bill for card XX1234 INR 500 due on 25 Sep 2026", received);
        service.processMessage("BANK", "Bill for card XX5678 INR 500 due on 25 Sep 2026", received);
        ArgumentCaptor<String> keys = ArgumentCaptor.forClass(String.class);
        verify(dao, times(2)).findBill(eq("BANK"), keys.capture(), anyLong());
        assertNotEquals(keys.getAllValues().get(0), keys.getAllValues().get(1));
    }
}
