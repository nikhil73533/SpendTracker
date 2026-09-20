package com.example.prediction.domain.service;

import com.example.prediction.data.local.PredictionDatabase;
import com.example.prediction.data.local.dao.*;
import com.example.prediction.data.local.entity.*;
import com.example.prediction.domain.model.PredictionTransaction;
import org.junit.Before;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

public class IncrementalPredictionPersistenceTest {
    private PredictionDatabase db;
    private CategoryFeedbackDao feedback;
    private Map<String, CategoryFeedbackEntity> disk;
    private List<String> categories;
    private IncrementalPredictionService service;
    private final PredictionTransaction tx = new PredictionTransaction("Acme", "", 500, "EXPENSE", 1);
    @Before public void setup() {
        db = mock(PredictionDatabase.class);
        feedback = mock(CategoryFeedbackDao.class);
        disk = new HashMap<>();
        categories = new ArrayList<>(Arrays.asList("Food", "Health", "Work"));
        when(db.categoryFeedbackDao()).thenReturn(feedback);
        when(db.merchantCategoryStatsDao()).thenReturn(mock(MerchantCategoryStatsDao.class));
        when(db.globalCategoryStatsDao()).thenReturn(mock(GlobalCategoryStatsDao.class));
        when(db.prototypeDao()).thenReturn(mock(PrototypeDao.class));
        when(db.merchantStatsDao()).thenReturn(mock(MerchantStatsDao.class));
        when(db.merchantCategoryStatsDao().getAll()).thenReturn(Collections.emptyList());
        when(feedback.getAll()).thenAnswer(i -> new ArrayList<>(disk.values()));
        doAnswer(i -> { CategoryFeedbackEntity row = i.getArgument(0); disk.put(row.id, row); return null; }).when(feedback).put(any());
        doAnswer(i -> { disk.remove(i.getArgument(0)); return null; }).when(feedback).delete(anyString());
        doAnswer(i -> { disk.clear(); return null; }).when(feedback).deleteAll();
        doAnswer(i -> { ((Runnable)i.getArgument(0)).run(); return null; }).when(db).runInTransaction(any(Runnable.class));
        service = new IncrementalPredictionService(db, type -> categories);
    }
    @Test public void correctionIsAvailableImmediatelyAcrossServiceInstances() {
        service.learn("transaction:1", tx, "Food");
        IncrementalPredictionService second = new IncrementalPredictionService(db, type -> categories);
        assertEquals("Food", second.predict(tx).getCategory());
        second.learn("transaction:1", tx, "Health");
        assertEquals("Health", service.predict(tx).getCategory());
        assertEquals(1, disk.size());
        verify(feedback, times(1)).getAll();
    }
    @Test public void freshModelRehydratesCorrectionsFromDisk() {
        service.learn("transaction:1", tx, "Work");
        PredictionDatabase reopened = mock(PredictionDatabase.class);
        when(reopened.categoryFeedbackDao()).thenReturn(feedback);
        MerchantCategoryStatsDao merchantDao = db.merchantCategoryStatsDao();
        when(reopened.merchantCategoryStatsDao()).thenReturn(merchantDao);
        assertEquals("Work", new IncrementalPredictionService(reopened, type -> categories).predict(tx).getCategory());
    }
    @Test public void failedWriteCannotPoisonMemory() {
        service.learn("transaction:1", tx, "Food");
        doThrow(new IllegalStateException("disk unavailable")).when(feedback).put(any());
        try { service.learn("transaction:1", tx, "Health"); fail("write should fail"); }
        catch (IllegalStateException expected) { }
        assertEquals("Food", service.predict(tx).getCategory());
    }
    @Test public void transferOrUncategorizedRetractsPreviousSample() {
        service.learn("transaction:1", tx, "Work");
        service.learn("transaction:1", new PredictionTransaction("Acme", "", 500, "TRANSFER", 1), "Transfer");
        assertTrue(disk.isEmpty());
        assertTrue(service.predict(tx).needsUserConfirmation());
    }
    @Test public void renamePreservesLearningAndDeletionRemovesIt() {
        service.learn("transaction:1", tx, "Work");
        service.renameCategory("EXPENSE", "Work", "Business");
        categories.remove("Work"); categories.add("Business");
        assertEquals("Business", service.predict(tx).getCategory());
        service.renameCategory("EXPENSE", "Business", null);
        categories.remove("Business");
        assertTrue(service.predict(tx).needsUserConfirmation());
        assertTrue(disk.isEmpty());
    }
    @Test public void resetClearsMemoryAndDiskAndKeepsColdStartHints() {
        service.learn("transaction:1", tx, "Work");
        service.resetAllData();
        assertTrue(disk.isEmpty());
        assertTrue(service.predict(tx).needsUserConfirmation());
        assertEquals("Food", service.predict(new PredictionTransaction("Zomato", "", 300, "EXPENSE", 1)).getCategory());
    }
    @Test public void legacyCorrectionIsRetainedButNewFeedbackWins() {
        when(db.merchantCategoryStatsDao().getAll()).thenReturn(Collections.singletonList(
                new MerchantCategoryStatsEntity("acme", "Health", "EXPENSE", 20, 20)));
        assertEquals("Health", service.predict(tx).getCategory());
        service.learn("transaction:1", tx, "Work");
        assertEquals("Work", service.predict(tx).getCategory());
        verify(db.merchantCategoryStatsDao(), times(1)).getAll();
    }
    @Test public void inferenceDoesNotWriteAnyTrainingData() {
        service.predict(tx); service.predict(tx);
        verify(feedback, never()).put(any());
        verify(feedback, times(1)).getAll();
    }
    @Test public void numberedCustomCategoryNamesAreDistinct() {
        categories.add("Work 2025"); categories.add("Work 2026");
        service.learn("transaction:1", tx, "Work 2026");
        assertEquals("Work 2026", service.predict(tx).getCategory());
    }
    @Test public void fastCorrectionsHaveStrictOrderingRegardlessOfId() {
        service.learn("transaction:9", tx, "Food");
        service.learn("transaction:10", tx, "Work");
        assertTrue(disk.get("transaction:10").updatedAt > disk.get("transaction:9").updatedAt);
        assertEquals("Work", service.predict(tx).getCategory());
    }
}
