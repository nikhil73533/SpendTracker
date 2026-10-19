package com.example.spendtracker.ui.dashboard;

import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
import static org.junit.Assert.*;

public class TransactionSelectionTest {
    @Test public void retainsIdsAcrossReorderingAndDropsDeletedOrFilteredTransactions() {
        TransactionSelection selection = new TransactionSelection();
        selection.toggle(12);
        selection.toggle(9);
        selection.retain(Arrays.asList(9, 15, 12));
        assertEquals(2, selection.size());
        selection.retain(Arrays.asList(9, 15));
        assertTrue(selection.contains(9));
        assertFalse(selection.contains(12));
        assertEquals(1, selection.size());
    }
    @Test public void selectAllReplacesScopeAndCancelLeavesNothingSelected() {
        TransactionSelection selection = new TransactionSelection();
        selection.toggle(99);
        selection.selectAll(Arrays.asList(1, 2, 3));
        assertEquals(3, selection.size());
        assertFalse(selection.contains(99));
        selection.toggle(2);
        assertEquals(2, selection.size());
        selection.clear();
        assertEquals(0, selection.size());
        assertFalse(selection.isActive());
    }
    @Test public void deletingLastRowExitsSelectionMode() {
        TransactionSelection selection = new TransactionSelection();
        selection.toggle(1);
        selection.retain(Collections.emptyList());
        assertFalse(selection.isActive());
        assertEquals(0, selection.size());
    }
}
