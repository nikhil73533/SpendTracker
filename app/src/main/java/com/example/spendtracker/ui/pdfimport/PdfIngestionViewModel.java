package com.example.spendtracker.ui.pdfimport;

import android.content.Context;
import android.net.Uri;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.spendtracker.domain.model.BulkImportResult;
import com.example.spendtracker.domain.model.Transaction;
import com.example.spendtracker.domain.repository.TransactionRepository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import dagger.hilt.android.lifecycle.HiltViewModel;
import javax.inject.Inject;

/** Retains extraction/import state across configuration changes and never mutates the UI from a worker. */
@HiltViewModel
public class PdfIngestionViewModel extends ViewModel {
    public static class UiState {
        public final boolean isLoading;
        public final String progress;
        public final int completedFiles;
        public final int totalFiles;
        public final List<PdfParserService.FileImportResult> fileResults;
        public final List<Transaction> reviewTransactions;
        public final BulkImportResult importResult;
        public final String error;

        UiState(boolean isLoading, String progress, int completedFiles, int totalFiles,
                List<PdfParserService.FileImportResult> fileResults, List<Transaction> reviewTransactions,
                BulkImportResult importResult, String error) {
            this.isLoading = isLoading;
            this.progress = progress;
            this.completedFiles = completedFiles;
            this.totalFiles = totalFiles;
            this.fileResults = Collections.unmodifiableList(new ArrayList<>(fileResults));
            this.reviewTransactions = Collections.unmodifiableList(new ArrayList<>(reviewTransactions));
            this.importResult = importResult;
            this.error = error;
        }
    }

    private final TransactionRepository transactionRepository;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final MutableLiveData<UiState> state = new MutableLiveData<>();

    @Inject
    public PdfIngestionViewModel(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
        state.setValue(new UiState(false, "", 0, 0, new ArrayList<>(), new ArrayList<>(), null, null));
    }

    public LiveData<UiState> getState() { return state; }

    public void parsePdfs(Context context, List<Uri> uris) {
        if (uris == null || uris.isEmpty()) return;
        final Context appContext = context.getApplicationContext();
        state.setValue(new UiState(true, "Preparing statement import…", 0, uris.size(),
                new ArrayList<>(), new ArrayList<>(), null, null));
        executor.execute(() -> {
            PdfParserService parser = new PdfParserService();
            List<Transaction> existing = new ArrayList<>(transactionRepository.getTransactionsSync());
            List<Transaction> candidates = new ArrayList<>();
            List<PdfParserService.FileImportResult> results = new ArrayList<>();
            for (int i = 0; i < uris.size(); i++) {
                state.postValue(new UiState(true, "Extracting statement " + (i + 1) + " of " + uris.size(),
                        i, uris.size(), results, candidates, null, null));
                PdfParserService.FileImportResult result = parser.parsePdf(appContext, uris.get(i), existing);
                results.add(result);
                candidates.addAll(result.transactions);
                existing.addAll(result.transactions);
            }
            String batchId = UUID.randomUUID().toString();
            for (Transaction transaction : candidates) transaction.setImportBatchId(batchId);
            state.postValue(new UiState(false, candidates.isEmpty() ? "No importable transactions found" : "Review extracted transactions",
                    uris.size(), uris.size(), results, candidates, null, null));
        });
    }

    public void importApproved(List<Transaction> approved) {
        if (approved == null || approved.isEmpty()) {
            UiState previous = state.getValue();
            state.setValue(new UiState(false, "", 0, 0,
                    previous == null ? new ArrayList<>() : previous.fileResults, new ArrayList<>(),
                    new BulkImportResult(0, 0, 0, "Select at least one transaction to import"), null));
            return;
        }
        UiState previous = state.getValue();
        state.setValue(new UiState(true, "Saving approved transactions…", 0, 0,
                previous == null ? new ArrayList<>() : previous.fileResults, approved, null, null));
        transactionRepository.importTransactions(approved, result -> state.postValue(new UiState(false,
                result.isSuccess() ? "Import complete" : "Import failed", 0, 0,
                previous == null ? new ArrayList<>() : previous.fileResults,
                result.isSuccess() ? new ArrayList<>() : approved, result, result.getError())));
    }

    @Override
    protected void onCleared() {
        executor.shutdownNow();
    }
}
