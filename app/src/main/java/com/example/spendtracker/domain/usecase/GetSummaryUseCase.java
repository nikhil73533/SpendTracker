package com.example.spendtracker.domain.usecase;

import androidx.lifecycle.LiveData;
import com.example.spendtracker.domain.model.Summary;
import com.example.spendtracker.domain.repository.TransactionRepository;
import javax.inject.Inject;

public class GetSummaryUseCase {
    private final TransactionRepository repository;

    @Inject
    public GetSummaryUseCase(TransactionRepository repository) {
        this.repository = repository;
    }

    public LiveData<Summary> execute(long startDate, long endDate) {
        return repository.getSummary(startDate, endDate);
    }
}
