package com.example.spendtracker.domain.usecase;

import androidx.lifecycle.LiveData;
import com.example.spendtracker.domain.model.Transaction;
import com.example.spendtracker.domain.repository.TransactionRepository;
import java.util.List;
import javax.inject.Inject;

public class GetTransactionsUseCase {
    private final TransactionRepository repository;

    @Inject
    public GetTransactionsUseCase(TransactionRepository repository) {
        this.repository = repository;
    }

    public LiveData<List<Transaction>> execute() {
        return repository.getTransactions();
    }
}
