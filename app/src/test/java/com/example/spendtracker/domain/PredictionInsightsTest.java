package com.example.spendtracker.domain;
import com.example.spendtracker.domain.model.*;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;
public class PredictionInsightsTest {
    private Transaction tx(String prediction, String confirmed) {
        Transaction t = new Transaction();
        t.setPredictedCategory(prediction); t.setPredictionFinalCategory(confirmed);
        t.setCategory("Food"); t.setConfidenceScore(1);
        return t;
    }
    @Test public void highConfidenceUnverifiedAndLegacyRowsAreNotCorrect() {
        PredictionInsights s = PredictionInsights.from(Arrays.asList(tx("Food", null), tx(null, "Food")));
        assertEquals(1, s.predicted); assertEquals(0, s.evaluated); assertTrue(Double.isNaN(s.accuracy()));
    }
    @Test public void percentagesUseOnlyReviewedPredictions() {
        PredictionInsights s = PredictionInsights.from(Arrays.asList(tx("Food", "Food"), tx("Food", "Travel"), tx("Food", null)));
        assertEquals(3, s.predicted); assertEquals(2, s.evaluated);
        assertEquals(50, s.accuracy(), 0); assertEquals(50, s.correctedPercent(), 0);
    }
    @Test public void latestReviewHasOneVoteAndDeletedRowsAreExcluded() {
        Transaction t = tx("Food", "Travel"); t.setPredictionFinalCategory(" food ");
        Transaction deleted = tx("Food", "Travel"); deleted.setStatus("DELETED");
        PredictionInsights s = PredictionInsights.from(Arrays.asList(t, deleted));
        assertEquals(1, s.evaluated); assertEquals(100, s.accuracy(), 0);
    }
    @Test public void missingFinalCategoryIsNotAnEvaluation() {
        assertEquals(0, PredictionInsights.from(Arrays.asList(tx("Food", "PENDING"), tx("Food", "Uncategorized"))).evaluated);
    }
    @Test public void drilldownOutcomesMatchChartCountsAndExcludeDeletedAndLegacy() {
        Transaction retained = tx("Food", " food "), corrected = tx("Food", "Travel"), pending = tx("Food", " PENDING ");
        Transaction deleted = tx("Food", "Food"); deleted.setStatus("DELETED");
        assertEquals(PredictionInsights.Outcome.RETAINED, PredictionInsights.outcome(retained));
        assertEquals(PredictionInsights.Outcome.CORRECTED, PredictionInsights.outcome(corrected));
        assertEquals(PredictionInsights.Outcome.UNREVIEWED, PredictionInsights.outcome(pending));
        assertNull(PredictionInsights.outcome(deleted));
        assertNull(PredictionInsights.outcome(tx(null, "Food")));
        PredictionInsights stats = PredictionInsights.from(Arrays.asList(retained, corrected, pending, deleted));
        assertEquals(3, stats.predicted); assertEquals(2, stats.evaluated); assertEquals(1, stats.retained);
    }
}
