package com.example.spendtracker.ui.transaction;
import androidx.arch.core.executor.testing.InstantTaskExecutorRule;
import androidx.lifecycle.MutableLiveData;
import com.example.spendtracker.domain.model.Transaction;
import com.example.spendtracker.domain.repository.*;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import static org.mockito.Mockito.*;
import static org.junit.Assert.*;
public class TransactionSaveTest {
    @Rule public InstantTaskExecutorRule main = new InstantTaskExecutorRule();
    @Test public void waitsForCommitPreventsDoubleTapAndReportsFailure() {
        TransactionRepository repository=mock(TransactionRepository.class);
        when(repository.getCategoriesByType(anyString())).thenReturn(new MutableLiveData<>());
        TransactionViewModel model=new TransactionViewModel(repository,mock(SecurityRepository.class));
        Transaction transaction=mock(Transaction.class);
        model.saveTransaction(transaction); model.saveTransaction(transaction);
        ArgumentCaptor<TransactionRepository.SaveCallback> callback=ArgumentCaptor.forClass(TransactionRepository.SaveCallback.class);
        verify(repository,times(1)).saveTransaction(eq(transaction),eq(true),callback.capture());
        assertTrue(model.isSaving().getValue()); assertNull(model.getSaveResult().getValue());
        callback.getValue().onComplete("Storage unavailable");
        assertFalse(model.isSaving().getValue()); assertEquals("Storage unavailable",model.getSaveResult().getValue());
        model.consumeSaveResult(); assertNull(model.getSaveResult().getValue());
        model.saveTransaction(transaction);
        verify(repository,times(2)).saveTransaction(eq(transaction),eq(true),callback.capture());
        callback.getValue().onComplete(null);
        assertEquals("",model.getSaveResult().getValue());
    }
}
