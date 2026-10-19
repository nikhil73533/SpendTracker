package com.example.spendtracker.ui.settings;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;
import com.example.spendtracker.domain.model.Transaction;
import com.example.spendtracker.domain.repository.TransactionRepository;
import dagger.hilt.android.lifecycle.HiltViewModel;
import java.util.List;
import javax.inject.Inject;

@HiltViewModel
public class TrashViewModel extends ViewModel {

    private final TransactionRepository transactionRepository;

    @Inject
    public TrashViewModel(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    public LiveData<List<Transaction>> getDeletedTransactions() {
        return transactionRepository.getDeletedTransactions();
    }

    public void restoreTransaction(int transactionId) {
        transactionRepository.restoreTransaction(transactionId);
    }

    public void permanentlyDeleteTransaction(int transactionId) {
        transactionRepository.permanentlyDeleteTransaction(transactionId);
    }
}
