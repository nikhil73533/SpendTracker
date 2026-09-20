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
    private AppNotifications() {}

    public static void createChannels(Context context) {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null) return;
        manager.createNotificationChannel(new NotificationChannel(BILLS, "Bill reminders", NotificationManager.IMPORTANCE_HIGH));
        manager.createNotificationChannel(new NotificationChannel(BUDGETS, "Budget limit warnings", NotificationManager.IMPORTANCE_HIGH));
        manager.createNotificationChannel(new NotificationChannel(UPI, "UPI limit warnings", NotificationManager.IMPORTANCE_HIGH));
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
        createChannels(context);
        // Explicit check here also satisfies the notification permission lint contract.
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context,
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return false;
        if (!enabled(context, channel)) return false;
        PendingIntent pending = new NavDeepLinkBuilder(context).setComponentName(MainActivity.class)
                .setGraph(R.navigation.nav_graph)
                .setDestination(BILLS.equals(channel) ? R.id.billAlertsFragment
                        : UPI.equals(channel) ? R.id.advancedAnalyticsFragment : R.id.categoryManagementFragment)
                .createPendingIntent();
        try {
            NotificationManagerCompat.from(context).notify(channel, id,
                    new NotificationCompat.Builder(context, channel)
                            .setSmallIcon(android.R.drawable.ic_popup_reminder)
                            .setContentTitle(title).setContentText(body)
                            .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                            .setContentIntent(pending).setAutoCancel(true)
                            .setPriority(NotificationCompat.PRIORITY_HIGH).build());
            return true;
        } catch (SecurityException ignored) { return false; }
    }

    public static void cancelBill(Context context, int id) {
        NotificationManagerCompat.from(context).cancel(BILLS, id);
    }
}
