package com.example.spendtracker.util;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.Build;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.navigation.NavDeepLinkBuilder;
import com.example.spendtracker.MainActivity;
import com.example.spendtracker.R;

public final class AppNotifications {
    public static final String BILLS = "bill_alert_channel";
    public static final String BUDGETS = "budget_warning_channel";
    public static final String UPI = "upi_limit_warning_channel";
    public static final String CATEGORY_REVIEW = "category_review_channel";
    private AppNotifications() {}

    public static void createChannels(Context context) {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null) return;
        manager.createNotificationChannel(new NotificationChannel(BILLS, "Bill reminders", NotificationManager.IMPORTANCE_HIGH));
        manager.createNotificationChannel(new NotificationChannel(BUDGETS, "Budget limit warnings", NotificationManager.IMPORTANCE_HIGH));
        manager.createNotificationChannel(new NotificationChannel(CATEGORY_REVIEW, "Category corrections", NotificationManager.IMPORTANCE_HIGH));
    }

    /** Removes the legacy tracking channel from devices upgraded from older releases. */
    public static void removeUpiChannel(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager != null) manager.deleteNotificationChannel(UPI);
    }

    public static boolean enabled(Context context, String channel) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context,
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return false;
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false;
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        NotificationChannel existing = manager == null ? null : manager.getNotificationChannel(channel);
        return existing == null || existing.getImportance() != NotificationManager.IMPORTANCE_NONE;
    }

    public static boolean post(Context context, String channel, int id, String title, String body) {
        return post(context, channel, id, title, body, null);
    }

    public static boolean postCategoryReview(Context context, int transactionId) {
        android.os.Bundle arguments = new android.os.Bundle();
        arguments.putInt("transactionId", transactionId);
        arguments.putBoolean("reviewCategory", true);
        return post(context, CATEGORY_REVIEW, transactionId, context.getString(R.string.category_review_title),
                context.getString(R.string.category_review_body), arguments);
    }

    private static boolean post(Context context, String channel, int id, String title, String body,
                                android.os.Bundle arguments) {
        createChannels(context);
        // Explicit check here also satisfies the notification permission lint contract.
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context,
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return false;
        if (!enabled(context, channel)) return false;
        PendingIntent pending = new NavDeepLinkBuilder(context).setComponentName(MainActivity.class)
                .setGraph(R.navigation.nav_graph)
                .setDestination(CATEGORY_REVIEW.equals(channel) ? R.id.transactionFormFragment : BILLS.equals(channel) ? R.id.billAlertsFragment
                        : UPI.equals(channel) ? R.id.advancedAnalyticsFragment : R.id.categoryManagementFragment)
                .setArguments(arguments)
                .createPendingIntent();
        try {
            NotificationCompat.Builder builder = new NotificationCompat.Builder(context, channel)
                            .setSmallIcon(android.R.drawable.ic_popup_reminder)
                            .setContentTitle(title).setContentText(body)
                            .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                            .setContentIntent(pending).setAutoCancel(true)
                            .setPriority(NotificationCompat.PRIORITY_HIGH);
            if (CATEGORY_REVIEW.equals(channel)) {
                builder.setCategory(NotificationCompat.CATEGORY_REMINDER)
                        .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                        .addAction(android.R.drawable.ic_menu_edit, context.getString(R.string.correct_category), pending);
            }
            NotificationManagerCompat.from(context).notify(channel, id, builder.build());
            return true;
        } catch (SecurityException ignored) { return false; }
    }

    public static void cancelBill(Context context, int id) {
        NotificationManagerCompat.from(context).cancel(BILLS, id);
    }
}
