package com.example.spendtracker.domain.usecase;

import com.example.spendtracker.domain.model.Transaction;
import com.example.spendtracker.domain.repository.TransactionRepository;
import javax.inject.Inject;

public class UpdateTransactionUseCase {
    private final TransactionRepository repository;

    @Inject
    public UpdateTransactionUseCase(TransactionRepository repository) {
        this.repository = repository;
    }

    public void execute(Transaction transaction) {
        repository.updateTransaction(transaction);
    }
}
