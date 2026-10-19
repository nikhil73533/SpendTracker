package com.example.spendtracker.ui.pdfimport;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.MutableLiveData;
import com.example.spendtracker.domain.model.Transaction;
import com.example.spendtracker.domain.repository.TransactionRepository;
import org.junit.Rule;
import org.junit.Test;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class PdfIngestionViewModelTest {
    @Rule public InstantTaskExecutorRule executor = new InstantTaskExecutorRule();

    @SuppressWarnings("unchecked")
    private PdfIngestionViewModel viewModel(TransactionRepository repository, Transaction... rows) throws Exception {
        PdfIngestionViewModel model = new PdfIngestionViewModel(repository);
        Field field = PdfIngestionViewModel.class.getDeclaredField("state");
        field.setAccessible(true);
        ((MutableLiveData<PdfIngestionViewModel.UiState>) field.get(model)).setValue(
                new PdfIngestionViewModel.UiState(false, "Review", 1, 1, Collections.emptyList(),
                        Arrays.asList(rows), null, null));
        return model;
    }
    @Test public void removesOnlyChosenUnpersistedCandidateAndHandlesLastDeletion() throws Exception {
        TransactionRepository repository = mock(TransactionRepository.class);
        Transaction first = new Transaction();
        Transaction second = new Transaction(); // Both have database ID zero before import.
        PdfIngestionViewModel model = viewModel(repository, first, second);
        model.removeFromReview(first);
        assertEquals(Collections.singletonList(second), model.getState().getValue().reviewTransactions);
        model.removeFromReview(first);
        assertEquals(1, model.getState().getValue().reviewTransactions.size());
        model.removeFromReview(second);
        assertTrue(model.getState().getValue().reviewTransactions.isEmpty());
        verifyNoInteractions(repository);
    }
    @Test public void emptyApprovalDoesNotDiscardPreview() throws Exception {
        Transaction row = new Transaction();
        PdfIngestionViewModel model = viewModel(mock(TransactionRepository.class), row);
        model.importApproved(Collections.emptyList());
        assertEquals(Collections.singletonList(row), model.getState().getValue().reviewTransactions);
        assertNotNull(model.getState().getValue().error);
    }

    @Test public void extractionFailureReleasesLoadingState() throws Exception {
        TransactionRepository repository = mock(TransactionRepository.class);
        when(repository.getTransactionsSync()).thenThrow(new IllegalStateException("Database unavailable"));
        PdfIngestionViewModel model = new PdfIngestionViewModel(repository);
        android.content.Context context = mock(android.content.Context.class);
        when(context.getApplicationContext()).thenReturn(context);
        java.util.concurrent.CountDownLatch completed = new java.util.concurrent.CountDownLatch(1);
        androidx.lifecycle.Observer<PdfIngestionViewModel.UiState> observer = state -> {
            if (state.error != null && !state.isLoading) completed.countDown();
        };
        model.getState().observeForever(observer);
        try {
            model.parsePdfs(context, Collections.singletonList(mock(android.net.Uri.class)));
            assertTrue(completed.await(5, java.util.concurrent.TimeUnit.SECONDS));
            assertFalse(model.getState().getValue().isLoading);
            assertNotNull(model.getState().getValue().error);
        } finally {
            model.getState().removeObserver(observer);
            Field worker = PdfIngestionViewModel.class.getDeclaredField("executor");
            worker.setAccessible(true);
            ((java.util.concurrent.ExecutorService) worker.get(model)).shutdownNow();
        }
    }
}
