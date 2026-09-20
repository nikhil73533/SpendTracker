package com.example.spendtracker.data.repository;

import com.example.spendtracker.data.local.dao.*;
import com.example.spendtracker.data.local.entity.CategoryEntity;
import com.example.spendtracker.util.CategoryValidation;

/** Caller must enclose each mutation in the main database's transaction. */
public final class CategoryMutations {
    private final CategoryDao categories;
    private final TransactionDao transactions;
    private final TransactionGroupDao groups;
    public CategoryMutations(CategoryDao categories, TransactionDao transactions, TransactionGroupDao groups) {
        this.categories = categories; this.transactions = transactions; this.groups = groups;
    }
    public String save(CategoryEntity category) {
        String error = CategoryValidation.error(category);
        if (error != null) return error;
        category.name = category.name.trim();
        CategoryEntity duplicate = categories.getCategoryByNameAndTypeSync(category.name, category.type);
        if (duplicate != null && duplicate.id != category.id) return "A category with this name already exists for this type";
        CategoryEntity existing = category.id > 0 ? categories.getCategoryByIdSync(category.id) : null;
        if (category.id > 0 && existing == null) return "This category no longer exists";
        if (existing == null) { categories.insertCategory(category); return null; }
        if (!existing.type.equals(category.type)) return "Create a new category to use a different transaction type";
        if (!existing.name.equals(category.name)) {
            if (CategoryValidation.reserved(existing.name) || CategoryValidation.reserved(category.name))
                return "System categories cannot be renamed";
            transactions.renameCategoryForType(existing.name, category.name, existing.type);
            groups.copyCategoryLinks(existing.name, category.name);
        }
        categories.updateCategory(category);
        if (!existing.name.equals(category.name) && categories.getCategoryByNameSync(existing.name) == null)
            groups.removeCategoryLinks(existing.name);
        return null;
    }
    public String delete(int id) {
        CategoryEntity category = categories.getCategoryByIdSync(id);
        if (category == null) return "This category no longer exists";
        if (CategoryValidation.reserved(category.name)) return "System categories cannot be deleted";
        if (categories.getCategoryByNameAndTypeSync("Uncategorized", category.type) == null)
            categories.insertCategory(new CategoryEntity(0, "Uncategorized", "", true, category.type));
        transactions.renameCategoryForType(category.name, "Uncategorized", category.type);
        categories.deleteCategory(category);
        if (categories.getCategoryByNameSync(category.name) == null) groups.removeCategoryLinks(category.name);
        return null;
    }
}
