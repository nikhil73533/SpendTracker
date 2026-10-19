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
        public final PasswordRequest passwordRequest;

        UiState(boolean isLoading, String progress, int completedFiles, int totalFiles,
                List<PdfParserService.FileImportResult> fileResults, List<Transaction> reviewTransactions,
                BulkImportResult importResult, String error) {
            this(isLoading, progress, completedFiles, totalFiles, fileResults, reviewTransactions,
                    importResult, error, null);
        }

        UiState(boolean isLoading, String progress, int completedFiles, int totalFiles,
                List<PdfParserService.FileImportResult> fileResults, List<Transaction> reviewTransactions,
                BulkImportResult importResult, String error, PasswordRequest passwordRequest) {
            this.isLoading = isLoading;
            this.progress = progress;
            this.completedFiles = completedFiles;
            this.totalFiles = totalFiles;
            this.fileResults = Collections.unmodifiableList(new ArrayList<>(fileResults));
            this.reviewTransactions = Collections.unmodifiableList(new ArrayList<>(reviewTransactions));
            this.importResult = importResult;
            this.error = error;
            this.passwordRequest = passwordRequest;
        }
    }

    /** A transient UI request. The password itself is deliberately not kept in state. */
    public static class PasswordRequest {
        public final String fileName;
        public final boolean invalidPassword;

        PasswordRequest(String fileName, boolean invalidPassword) {
            this.fileName = fileName;
            this.invalidPassword = invalidPassword;
        }
    }

    private static class PendingImport {
        final List<Uri> uris;
        final List<Transaction> candidates = new ArrayList<>();
        final List<PdfParserService.FileImportResult> results = new ArrayList<>();
        final List<Transaction> existing;
        final String batchId = UUID.randomUUID().toString();
        int nextIndex;

        PendingImport(List<Uri> uris, List<Transaction> existing) {
            this.uris = new ArrayList<>(uris);
            this.existing = existing;
        }
    }

    private final TransactionRepository transactionRepository;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final MutableLiveData<UiState> state = new MutableLiveData<>();
    private volatile PendingImport pendingImport;

    @Inject
    public PdfIngestionViewModel(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
        state.setValue(new UiState(false, "", 0, 0, new ArrayList<>(), new ArrayList<>(), null, null));
    }

    public LiveData<UiState> getState() { return state; }

    /** Backward-compatible name for callers that still refer to PDFs only. */
    public void parsePdfs(Context context, List<Uri> uris) {
        parseStatements(context, uris);
    }

    /** Imports PDF statements and statement screenshots/photos through one review flow. */
    public void parseStatements(Context context, List<Uri> uris) {
        if (uris == null || uris.isEmpty()) return;
        if (state.getValue() != null && state.getValue().isLoading) return;
        if (pendingImport != null) return;
        final Context appContext = context.getApplicationContext();
        state.setValue(new UiState(true, "Preparing statement import…", 0, uris.size(),
                new ArrayList<>(), new ArrayList<>(), null, null));
        executor.execute(() -> {
            try {
                pendingImport = new PendingImport(uris, new ArrayList<>(transactionRepository.getTransactionsSync()));
                processNext(appContext, null);
            } catch (Exception error) {
                android.util.Log.e("StatementImport", "Unable to prepare statement extraction", error);
                state.postValue(new UiState(false, "Extraction interrupted", 0, uris.size(),
                        new ArrayList<>(), new ArrayList<>(), null,
                        "Could not complete extraction. Please try again."));
            }
        });
    }

    /** Continues the currently paused encrypted-PDF import. The supplied password is never retained. */
    public void submitPassword(Context context, String password) {
        if (pendingImport == null) return;
        Context appContext = context.getApplicationContext();
        showPasswordProcessing("Unlocking statement…");
        executor.execute(() -> processNext(appContext, password == null ? "" : password));
    }

    /** Skips only the encrypted file currently waiting for a password and continues the batch. */
    public void skipPasswordProtectedFile(Context context) {
        if (pendingImport == null) return;
        Context appContext = context.getApplicationContext();
        showPasswordProcessing("Skipping protected statement…");
        executor.execute(() -> {
            PendingImport current = pendingImport;
            if (current == null || current.nextIndex >= current.uris.size()) return;
            PdfParserService parser = new PdfParserService();
            PdfParserService.FileImportResult skipped = new PdfParserService.FileImportResult(
                    parser.fileName(appContext, current.uris.get(current.nextIndex)));
            skipped.error = "Skipped because no password was provided.";
            current.results.add(skipped);
            current.nextIndex++;
            processNext(appContext, null);
        });
    }

    private void processNext(Context context, String suppliedPassword) {
        PendingImport current = pendingImport;
        if (current == null) return;
        if (current.nextIndex >= current.uris.size()) {
            state.postValue(new UiState(false, current.candidates.isEmpty()
                    ? "No importable transactions found" : "Review extracted transactions",
                    current.uris.size(), current.uris.size(), current.results, current.candidates, null, null));
            pendingImport = null;
            return;
        }

        PdfParserService parser = new PdfParserService();
        Uri uri = current.uris.get(current.nextIndex);
        try {
            if (!parser.isSupportedStatement(context, uri)) {
                PdfParserService.FileImportResult unsupported = new PdfParserService.FileImportResult(parser.fileName(context, uri));
                unsupported.error = "Unsupported file. Select a PDF, JPG, PNG, WEBP, HEIC, or HEIF statement.";
                current.results.add(unsupported);
                current.nextIndex++;
                processNext(context, null);
                return;
            }

            boolean pdf = parser.isPdf(context, uri);
            if (pdf && suppliedPassword == null && parser.requiresPassword(context, uri)) {
                publishPasswordRequest(current, parser, context, false);
                return;
            }
            if (pdf && suppliedPassword != null && !parser.isPasswordValid(context, uri, suppliedPassword)) {
                publishPasswordRequest(current, parser, context, true);
                return;
            }

            state.postValue(new UiState(true, "Extracting statement " + (current.nextIndex + 1) + " of " + current.uris.size(),
                    current.nextIndex, current.uris.size(), current.results, current.candidates, null, null));
            PdfParserService.FileImportResult result = parser.parseStatement(context, uri,
                    suppliedPassword == null ? "" : suppliedPassword, current.existing, pdf);
            for (Transaction transaction : result.transactions) transaction.setImportBatchId(current.batchId);
            current.results.add(result);
            current.candidates.addAll(result.transactions);
            current.existing.addAll(result.transactions);
            current.nextIndex++;
            processNext(context, null);
        } catch (Exception error) {
            android.util.Log.e("StatementImport", "Unable to process selected statement", error);
            PdfParserService.FileImportResult failed = new PdfParserService.FileImportResult(parser.fileName(context, uri));
            failed.error = "Could not read this file: " + (error.getMessage() == null ? "unknown error" : error.getMessage());
            current.results.add(failed);
            current.nextIndex++;
            processNext(context, null);
        }
    }

    private void publishPasswordRequest(PendingImport current, PdfParserService parser, Context context,
                                        boolean invalidPassword) {
        state.postValue(new UiState(false, "Password required", current.nextIndex, current.uris.size(),
                current.results, current.candidates, null, null,
                new PasswordRequest(parser.fileName(context, current.uris.get(current.nextIndex)), invalidPassword)));
    }

    private void showPasswordProcessing(String progress) {
        UiState previous = state.getValue();
        if (previous == null) return;
        state.setValue(new UiState(true, progress, previous.completedFiles, previous.totalFiles,
                previous.fileResults, previous.reviewTransactions, null, null));
    }

    public void importApproved(List<Transaction> approved) {
        if (state.getValue() != null && state.getValue().isLoading) return;
        if (approved == null || approved.isEmpty()) {
            UiState previous = state.getValue();
            if (previous != null) state.setValue(new UiState(false, previous.progress, previous.completedFiles,
                    previous.totalFiles, previous.fileResults, previous.reviewTransactions, null,
                    "Select at least one transaction to import"));
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

    /** Removes a candidate only from the pending preview; no persisted transaction is touched. */
    public void removeFromReview(Transaction transaction) {
        UiState previous = state.getValue();
        if (previous == null || transaction == null || previous.isLoading) return;
        List<Transaction> remaining = new ArrayList<>(previous.reviewTransactions);
        if (!remaining.remove(transaction)) return;
        state.setValue(new UiState(previous.isLoading, previous.progress, previous.completedFiles,
                previous.totalFiles, previous.fileResults, remaining, previous.importResult, previous.error));
    }

    @Override
    protected void onCleared() {
        executor.shutdownNow();
    }
}
