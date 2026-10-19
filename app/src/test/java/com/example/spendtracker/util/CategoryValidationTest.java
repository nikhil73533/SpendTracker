package com.example.spendtracker.util;

import com.example.spendtracker.data.local.entity.CategoryEntity;
import org.junit.Test;
import static org.junit.Assert.*;

public class CategoryValidationTest {
    private CategoryEntity category() { return new CategoryEntity(0, "Food", "", false, "EXPENSE"); }
    @Test public void acceptsDecimalBudget() {
        CategoryEntity c = category(); c.unlimitedWeekly = false; c.weeklyBudget = 1250.75;
        assertNull(CategoryValidation.error(c));
    }
    @Test public void rejectsInvalidBudgets() {
        for (double amount : new double[]{0, -1, Double.NaN, Double.POSITIVE_INFINITY}) {
            CategoryEntity c = category(); c.unlimitedMonthly = false; c.monthlyBudget = amount;
            assertNotNull(CategoryValidation.error(c));
        }
    }
    @Test public void acceptsUnlimitedZero() { assertNull(CategoryValidation.error(category())); }
    @Test public void rejectsBlankNameAndWrongType() {
        CategoryEntity c = category(); c.name = " "; assertNotNull(CategoryValidation.error(c));
        c.name = "Food"; c.type = "UNKNOWN"; assertNotNull(CategoryValidation.error(c));
    }
    @Test public void protectsSystemCategoriesOnly() {
        assertTrue(CategoryValidation.reserved(" transfer "));
        assertTrue(CategoryValidation.reserved("Uncategorized"));
        assertFalse(CategoryValidation.reserved("Food"));
    }
}
