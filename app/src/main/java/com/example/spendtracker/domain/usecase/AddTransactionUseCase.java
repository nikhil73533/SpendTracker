package com.example.spendtracker.domain.usecase;

import com.example.spendtracker.domain.model.Transaction;
import com.example.spendtracker.domain.repository.TransactionRepository;
import javax.inject.Inject;

public class AddTransactionUseCase {
    private final TransactionRepository repository;

    @Inject
    public AddTransactionUseCase(TransactionRepository repository) {
        this.repository = repository;
    }

    public void execute(Transaction transaction) {
        repository.addTransaction(transaction);
    }
}
