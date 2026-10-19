package com.example.spendtracker.util;

import com.example.spendtracker.data.local.entity.CategoryEntity;

public final class CategoryValidation {
    private CategoryValidation() {}
    public static boolean reserved(String name) {
        return name != null && ("Transfer".equalsIgnoreCase(name.trim()) || "Uncategorized".equalsIgnoreCase(name.trim()));
    }
    public static String error(CategoryEntity c) {
        if (c == null || c.name == null || c.name.trim().isEmpty()) return "Enter a category name";
        if (!"EXPENSE".equals(c.type) && !"INCOME".equals(c.type)) return "Choose Expense or Income";
        if (!validBudget(c.unlimitedWeekly, c.weeklyBudget)
                || !validBudget(c.unlimitedMonthly, c.monthlyBudget)
                || !validBudget(c.unlimitedAnnually, c.annuallyBudget))
            return "Each limited budget must be a valid amount greater than zero";
        return null;
    }
    private static boolean validBudget(boolean unlimited, double value) {
        return Double.isFinite(value) && value >= 0 && (unlimited || value > 0);
    }
}
