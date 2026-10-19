package com.example.spendtracker.util;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import com.google.android.play.core.review.ReviewManager;
import com.google.android.play.core.review.ReviewManagerFactory;

/** Keeps ratings independent from feedback and gracefully falls back to the listing. */
public final class PlayStoreManager {
    private PlayStoreManager() { }

    public static void requestReview(Activity activity, Runnable unavailable) {
        if (!ConnectivityHelper.isOnline(activity)) {
            unavailable.run();
            return;
        }
        ReviewManager manager = ReviewManagerFactory.create(activity);
        manager.requestReviewFlow().addOnCompleteListener(task -> {
            if (!task.isSuccessful()) {
                if (!openListing(activity)) unavailable.run();
                return;
            }
            manager.launchReviewFlow(activity, task.getResult())
                    .addOnFailureListener(error -> {
                        if (!openListing(activity)) unavailable.run();
                    });
        });
    }

    public static boolean openListing(Context context) {
        Uri market = Uri.parse("market://details?id=" + context.getPackageName());
        Intent marketIntent = new Intent(Intent.ACTION_VIEW, market);
        try {
            if (marketIntent.resolveActivity(context.getPackageManager()) != null) {
                context.startActivity(marketIntent);
                return true;
            }
            Intent webIntent = new Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/apps/details?id=" + context.getPackageName()));
            if (webIntent.resolveActivity(context.getPackageManager()) == null) return false;
            context.startActivity(webIntent);
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }
}
