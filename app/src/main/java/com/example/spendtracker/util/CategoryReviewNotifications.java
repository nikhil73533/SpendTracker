package com.example.spendtracker.util;

import android.content.Context;
import androidx.core.app.NotificationManagerCompat;
import com.example.spendtracker.domain.model.Transaction;

/** Emits one alert each time a saved transaction enters the category-review list. */
public final class CategoryReviewNotifications {
    private CategoryReviewNotifications() {}

    public static boolean needsReview(Transaction transaction) {
        return transaction != null && "ACTIVE".equals(transaction.getStatus())
                && transaction.getConfidenceScore() < CategoryPrediction.REVIEW_THRESHOLD;
    }

    public static boolean shouldNotify(Transaction previous, Transaction current) {
        return needsReview(current) && !needsReview(previous);
    }

    public static void cancel(Context context, int id) {
        try {
            NotificationManagerCompat.from(context).cancel(AppNotifications.CATEGORY_REVIEW, id);
        } catch (RuntimeException error) {
            android.util.Log.w("CategoryReview", "Unable to clear category review notification", error);
        }
    }

    public static void onSaved(Context context, Transaction previous, Transaction current) {
        onSaved(context, previous, current, current == null ? 0 : current.getId());
    }

    public static void onSaved(Context context, Transaction previous, Transaction current, int id) {
        if (current == null || id <= 0) return;
        try {
            if (shouldNotify(previous, current)) {
                AppNotifications.postCategoryReview(context, id);
            } else if (!needsReview(current)) {
                cancel(context, id);
            }
        } catch (RuntimeException error) {
            // A notification failure must never make a committed transaction look unsaved.
            android.util.Log.w("CategoryReview", "Unable to update category review notification", error);
        }
    }
}
