package com.example.spendtracker.util;

import com.example.spendtracker.domain.model.Transaction;
import org.junit.Test;
import static org.junit.Assert.*;

public class CategoryReviewNotificationsTest {
    private Transaction transaction(double confidence) {
        Transaction transaction = new Transaction();
        transaction.setConfidenceScore(confidence);
        return transaction;
    }

    @Test public void alertsOnlyWhenEnteringReviewAndAgainAfterReentry() {
        Transaction uncertain = transaction(0.4);
        Transaction confirmed = transaction(1.0);
        assertTrue(CategoryReviewNotifications.shouldNotify(null, uncertain));
        assertFalse(CategoryReviewNotifications.shouldNotify(uncertain, transaction(0.5)));
        assertFalse(CategoryReviewNotifications.shouldNotify(uncertain, confirmed));
        assertTrue(CategoryReviewNotifications.shouldNotify(confirmed, uncertain));
        assertFalse(CategoryReviewNotifications.shouldNotify(null, transaction(CategoryPrediction.REVIEW_THRESHOLD)));
    }

    @Test public void deletedTransactionsDoNotAlertButRestoredOnesDo() {
        Transaction deleted = transaction(0.2);
        deleted.setStatus("DELETED");
        assertFalse(CategoryReviewNotifications.shouldNotify(null, deleted));
        assertTrue(CategoryReviewNotifications.shouldNotify(deleted, transaction(0.2)));
        assertFalse(CategoryReviewNotifications.shouldNotify(null, null));
    }
}
