package com.example.spendtracker.data.repository;

import com.example.spendtracker.data.local.dao.*;
import com.example.spendtracker.data.local.entity.CategoryEntity;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

public class CategoryMutationsTest {
    private final CategoryDao categories = mock(CategoryDao.class);
    private final TransactionDao transactions = mock(TransactionDao.class);
    private final TransactionGroupDao groups = mock(TransactionGroupDao.class);
    private final CategoryMutations mutations = new CategoryMutations(categories, transactions, groups);
    private CategoryEntity category(int id, String name, String type) { return new CategoryEntity(id, name, "", false, type); }

    @Test public void sameNameOtherTypeDoesNotOverwrite() {
        CategoryEntity income = category(0, "Gifts", "INCOME");
        when(categories.getCategoryByNameSync("Gifts")).thenReturn(category(1, "Gifts", "EXPENSE"));
        assertNull(mutations.save(income));
        verify(categories).insertCategory(income);
        verify(categories, never()).updateCategory(any());
    }
    @Test public void duplicateInSameTypeRejected() {
        when(categories.getCategoryByNameAndTypeSync("Food", "EXPENSE")).thenReturn(category(1, "Food", "EXPENSE"));
        assertNotNull(mutations.save(category(0, "Food", "EXPENSE")));
        verify(categories, never()).insertCategory(any());
        verify(categories, never()).updateCategory(any());
    }
    @Test public void renameUpdatesOnlyMatchingTypeAndCopiesGroupLinks() {
        when(categories.getCategoryByIdSync(1)).thenReturn(category(1, "Gifts", "EXPENSE"));
        when(categories.getCategoryByNameSync("Gifts")).thenReturn(category(2, "Gifts", "INCOME"));
        assertNull(mutations.save(category(1, "Presents", "EXPENSE")));
        verify(transactions).renameCategoryForType("Gifts", "Presents", "EXPENSE");
        verify(transactions, never()).renameCategory(anyString(), anyString());
        verify(groups).copyCategoryLinks("Gifts", "Presents");
        verify(groups, never()).removeCategoryLinks("Gifts");
    }
    @Test public void typeChangeCannotReclassifyTransactions() {
        when(categories.getCategoryByIdSync(1)).thenReturn(category(1, "Gifts", "EXPENSE"));
        assertNotNull(mutations.save(category(1, "Gifts", "INCOME")));
        verifyNoInteractions(transactions);
        verify(categories, never()).updateCategory(any());
    }
    @Test public void deletionReassignsOnlyMatchingTypeAndKeepsTransactions() {
        CategoryEntity old = category(1, "Gifts", "EXPENSE");
        when(categories.getCategoryByIdSync(1)).thenReturn(old);
        when(categories.getCategoryByNameSync("Gifts")).thenReturn(category(2, "Gifts", "INCOME"));
        assertNull(mutations.delete(1));
        verify(transactions).renameCategoryForType("Gifts", "Uncategorized", "EXPENSE");
        verify(categories).deleteCategory(old);
        verify(groups, never()).removeCategoryLinks("Gifts");
    }
    @Test public void deletionDoesNotReplaceExistingUncategorizedBudget() {
        when(categories.getCategoryByIdSync(1)).thenReturn(category(1, "Food", "EXPENSE"));
        when(categories.getCategoryByNameAndTypeSync("Uncategorized", "EXPENSE")).thenReturn(category(2, "Uncategorized", "EXPENSE"));
        assertNull(mutations.delete(1));
        verify(categories, never()).insertCategory(any());
    }
    @Test public void systemCategoriesCannotBeDeletedOrRenamed() {
        when(categories.getCategoryByIdSync(1)).thenReturn(category(1, "Transfer", "EXPENSE"));
        assertNotNull(mutations.delete(1));
        assertNotNull(mutations.save(category(1, "Something", "EXPENSE")));
        verifyNoInteractions(transactions);
    }
    @Test public void decimalBudgetsAndNotificationPreferencePersist() {
        CategoryEntity old = category(1, "Food", "EXPENSE");
        when(categories.getCategoryByIdSync(1)).thenReturn(old);
        CategoryEntity updated = category(1, "Food", "EXPENSE");
        updated.notificationsEnabled = false; updated.unlimitedMonthly = false; updated.monthlyBudget = 2500.75;
        assertNull(mutations.save(updated));
        verify(categories).updateCategory(updated);
        verifyNoInteractions(transactions);
    }
}
