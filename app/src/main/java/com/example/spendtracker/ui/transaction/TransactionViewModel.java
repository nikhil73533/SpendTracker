package com.example.spendtracker.ui.transaction;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;
import com.example.spendtracker.domain.model.Transaction;
import com.example.spendtracker.domain.repository.SecurityRepository;
import com.example.spendtracker.domain.repository.TransactionRepository;
import dagger.hilt.android.lifecycle.HiltViewModel;
import java.util.List;
import javax.inject.Inject;

@HiltViewModel
public class TransactionViewModel extends ViewModel {
    private final TransactionRepository repository;
    private final SecurityRepository securityRepository;
    private final MutableLiveData<String> categoryTypeFilter = new MutableLiveData<>("EXPENSE");
    private final LiveData<List<String>> categoriesByType;
    private final LiveData<List<String>> incomeCategories;
    private final LiveData<List<String>> expenseCategories;

    @Inject
    public TransactionViewModel(TransactionRepository repository, SecurityRepository securityRepository) {
        this.repository = repository;
        this.securityRepository = securityRepository;
        this.categoriesByType = Transformations.switchMap(categoryTypeFilter, repository::getCategoriesByType);
        this.incomeCategories = repository.getCategoriesByType("INCOME");
        this.expenseCategories = repository.getCategoriesByType("EXPENSE");
    }

    public LiveData<Boolean> isPrivacyModeEnabled() {
        return securityRepository.isPrivacyModeEnabled();
    }

    public String formatAmount(double amount) {
        return securityRepository.maskAmount(amount);
    }

    public String maskPII(String value) {
        return securityRepository.maskPII(value);
    }

    public void addTransaction(Transaction transaction) {
        repository.addTransaction(transaction);
    }

    public void updateTransaction(Transaction transaction) {
        repository.updateTransaction(transaction);
    }

    public void deleteTransaction(Transaction transaction) {
        repository.deleteTransaction(transaction);
    }

    public LiveData<Transaction> getTransaction(int id) {
        return repository.getTransactionById(id);
    }

    public LiveData<List<String>> getCategories() {
        return repository.getCategories();
    }

    public void setCategoryTypeFilter(String type) {
        categoryTypeFilter.setValue(type);
    }

    public LiveData<List<String>> getCategoriesByType() {
        return categoriesByType;
    }

    public LiveData<List<String>> getCategoriesByType(String type) {
        setCategoryTypeFilter(type);
        return categoriesByType;
    }

    public LiveData<List<String>> getIncomeCategories() {
        return incomeCategories;
    }

    public LiveData<List<String>> getExpenseCategories() {
        return expenseCategories;
    }

    public void addCategory(String name, String type) {
        repository.addCategory(name, type);
    }

    public void deleteCategory(String name) {
        repository.deleteCategory(name);
    }

    public void renameCategory(String oldName, String newName) {
        repository.renameCategory(oldName, newName);
    }

    public LiveData<List<Transaction>> getTransactions() {
        return repository.getTransactions();
    }

    public LiveData<List<com.example.spendtracker.data.local.dao.TransactionDao.AccountSummary>> getUniqueAccounts() {
        return repository.getUniqueAccounts();
    }

    public LiveData<List<Transaction>> getAccountHistory(String accountId, long start, long end) {
        return repository.getAccountHistory(accountId, start, end);
    }

    public void markAsRead(String accountName) {
        new Thread(() -> repository.markAsRead(accountName)).start();
    }

    public LiveData<List<String>> getUniqueContacts() {
        return repository.getUniqueContacts();
    }
}
