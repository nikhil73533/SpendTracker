package com.example.spendtracker.data.repository;

import android.content.Context;
import com.example.spendtracker.billing.PremiumRepository;
import com.example.spendtracker.data.local.dao.*;
import com.example.spendtracker.data.local.entity.TransactionEntity;
import com.example.spendtracker.data.local.database.SpendTrackerDatabase;
import com.example.spendtracker.domain.model.Transaction;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class PredictionPersistenceTest {
    @org.junit.Rule public androidx.arch.core.executor.testing.InstantTaskExecutorRule main = new androidx.arch.core.executor.testing.InstantTaskExecutorRule();
    @Test public void explicitCorrectionPreservesOriginalPredictionEvenWhenFormCreatesNewObject() {
        TransactionDao dao = mock(TransactionDao.class);
        when(dao.getAllTransactions()).thenReturn(new androidx.lifecycle.MutableLiveData<>(java.util.Collections.emptyList()));
        Context context = mock(Context.class);
        TransactionEntity saved = new TransactionEntity(); saved.id = 5; saved.category = "Food";
        saved.predictedCategory = "Food"; saved.confidenceScore = .95; saved.bookmarked = true;
        when(dao.getTransactionByIdSync(5)).thenReturn(saved);
        TransactionRepositoryImpl repo = new TransactionRepositoryImpl(context, dao, mock(TransactionDao.class),
                mock(CategoryDao.class), mock(TransactionGroupDao.class), mock(RepeatedAlertDao.class),
                mock(PremiumRepository.class), mock(SpendTrackerDatabase.class));
        Transaction edited = new Transaction(5, 50, "Travel", "", "EXPENSE", 0, "", "", "", "", "", "");
        repo.updateConfirmedTransaction(edited);
        ArgumentCaptor<TransactionEntity> captured = ArgumentCaptor.forClass(TransactionEntity.class);
        verify(dao, timeout(3000)).updateTransaction(captured.capture());
        assertEquals("Food", captured.getValue().predictedCategory);
        assertEquals("Travel", captured.getValue().predictionFinalCategory);
        assertTrue(captured.getValue().bookmarked);
    }
    @Test public void automatedRefreshCannotConfirmOrReplaceOriginalPrediction() {
        TransactionDao dao = mock(TransactionDao.class);
        when(dao.getAllTransactions()).thenReturn(new androidx.lifecycle.MutableLiveData<>(java.util.Collections.emptyList()));
        TransactionEntity saved = new TransactionEntity(); saved.id = 5; saved.predictedCategory = "Food";
        when(dao.getTransactionByIdSync(5)).thenReturn(saved);
        TransactionRepositoryImpl repo = new TransactionRepositoryImpl(mock(Context.class), dao, mock(TransactionDao.class),
                mock(CategoryDao.class), mock(TransactionGroupDao.class), mock(RepeatedAlertDao.class),
                mock(PremiumRepository.class), mock(SpendTrackerDatabase.class));
        Transaction refreshed = new Transaction(5, 50, "Travel", "", "EXPENSE", 0, "", "", "", "", "", "");
        refreshed.setPredictedCategory("Travel"); refreshed.setPredictionFinalCategory("Travel");
        repo.updateTransaction(refreshed);
        ArgumentCaptor<TransactionEntity> captured = ArgumentCaptor.forClass(TransactionEntity.class);
        verify(dao, timeout(3000)).updateTransaction(captured.capture());
        assertEquals("Food", captured.getValue().predictedCategory);
        assertNull(captured.getValue().predictionFinalCategory);
    }
}
