package com.example.spendtracker.util;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;
import android.util.Log;
import androidx.core.app.NotificationCompat;
import com.example.spendtracker.data.local.dao.CategoryDao;
import com.example.spendtracker.data.local.dao.TransactionDao;
import com.example.spendtracker.data.local.entity.CategoryEntity;
import com.example.spendtracker.domain.model.Transaction;
import java.util.Calendar;
import java.util.Locale;

/**
 * Utility helper to evaluate category budget ranges (weekly, monthly, annually)
 * and trigger system warning notifications when budgets are exceeded.
 */
public class BudgetNotificationHelper {
    private static final String TAG = "BudgetNotificationHelper";
    private static final String CHANNEL_ID = "budget_warning_channel";

    /**
     * Checks if the given transaction causes any budget limit (weekly, monthly, or annual)
     * to be exceeded for its category, and fires corresponding warning notifications.
     *
     * @param context Application context
     * @param categoryDao Category DAO to fetch budget limits
     * @param transactionDao Transaction DAO to calculate category totals
     * @param transaction The inserted or updated transaction
     */
    public static void checkBudgetAndNotify(Context context, CategoryDao categoryDao, TransactionDao transactionDao, Transaction transaction) {
        if (context == null || categoryDao == null || transactionDao == null || transaction == null) {
            return;
        }

        // Only evaluate expense transactions with a valid category
        if (!"EXPENSE".equalsIgnoreCase(transaction.getType()) || transaction.getCategory() == null || transaction.getCategory().trim().isEmpty()) {
            return;
        }

        try {
            CategoryEntity category = categoryDao.getCategoryByNameSync(transaction.getCategory());
            if (category == null || !category.notificationsEnabled) {
                Log.d(TAG, "Category not found or notifications disabled for: " + transaction.getCategory());
                return;
            }

            long txDate = transaction.getDate();

            // 1. Calculate Weekly Total Range (Monday 00:00 to Sunday 23:59)
            long[] weeklyRange = getWeeklyRange(txDate);
            double weeklyTotal = transactionDao.getCategoryTotalInRangeSync(category.name, weeklyRange[0], weeklyRange[1]);

            // 2. Calculate Monthly Total Range (1st of month to Last day of month)
            long[] monthlyRange = getMonthlyRange(txDate);
            double monthlyTotal = transactionDao.getCategoryTotalInRangeSync(category.name, monthlyRange[0], monthlyRange[1]);

            // 3. Calculate Annual Total Range (Jan 1 to Dec 31)
            long[] annualRange = getAnnualRange(txDate);
            double annualTotal = transactionDao.getCategoryTotalInRangeSync(category.name, annualRange[0], annualRange[1]);

            boolean weeklyExceeded = !category.unlimitedWeekly && category.weeklyBudget > 0 && weeklyTotal > category.weeklyBudget;
            boolean monthlyExceeded = !category.unlimitedMonthly && category.monthlyBudget > 0 && monthlyTotal > category.monthlyBudget;
            boolean annualExceeded = !category.unlimitedAnnually && category.annuallyBudget > 0 && annualTotal > category.annuallyBudget;

            int exceededCount = (weeklyExceeded ? 1 : 0) + (monthlyExceeded ? 1 : 0) + (annualExceeded ? 1 : 0);
            Log.d(TAG, String.format(Locale.getDefault(), "Budget check for %s: weeklyExceeded=%b (₹%.0f/₹%.0f), monthlyExceeded=%b (₹%.0f/₹%.0f), annualExceeded=%b (₹%.0f/₹%.0f) -> %d notifications",
                    category.name, weeklyExceeded, weeklyTotal, category.weeklyBudget, monthlyExceeded, monthlyTotal, category.monthlyBudget, annualExceeded, annualTotal, category.annuallyBudget, exceededCount));

            if (exceededCount > 0) {
                createNotificationChannel(context);
                NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
                if (nm == null) return;

                int baseNotificationId = (int) (System.currentTimeMillis() % 100000);

                if (weeklyExceeded) {
                    fireWarning(context, nm, baseNotificationId + 1, category.name, "Weekly", weeklyTotal, category.weeklyBudget);
                }
                if (monthlyExceeded) {
                    fireWarning(context, nm, baseNotificationId + 2, category.name, "Monthly", monthlyTotal, category.monthlyBudget);
                }
                if (annualExceeded) {
                    fireWarning(context, nm, baseNotificationId + 3, category.name, "Annually", annualTotal, category.annuallyBudget);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error checking budget notifications: " + e.getMessage(), e);
        }
    }

    private static void fireWarning(Context context, NotificationManager nm, int notificationId, String categoryName, String period, double actual, double limit) {
        String title = String.format(Locale.getDefault(), "⚠️ %s Budget Exceeded: %s", period, categoryName);
        String body = String.format(Locale.getDefault(), "Your %s spending in %s reached ₹%.0f, exceeding your set budget of ₹%.0f.",
                period.toLowerCase(), categoryName, actual, limit);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true);

        try {
            nm.notify(notificationId, builder.build());
        } catch (SecurityException e) {
            Log.w(TAG, "Notification permission missing: " + e.getMessage());
        }
    }

    private static void createNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Budget Limit Warnings",
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Notifications triggered when category budget limits are exceeded");
            NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) {
                nm.createNotificationChannel(channel);
            }
        }
    }

    private static long[] getWeeklyRange(long timestamp) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(timestamp);
        cal.setFirstDayOfWeek(Calendar.MONDAY);
        cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        long start = cal.getTimeInMillis();

        cal.add(Calendar.DAY_OF_WEEK, 6);
        cal.set(Calendar.HOUR_OF_DAY, 23);
        cal.set(Calendar.MINUTE, 59);
        cal.set(Calendar.SECOND, 59);
        cal.set(Calendar.MILLISECOND, 999);
        long end = cal.getTimeInMillis();

        return new long[]{start, end};
    }

    private static long[] getMonthlyRange(long timestamp) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(timestamp);
        cal.set(Calendar.DAY_OF_MONTH, 1);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        long start = cal.getTimeInMillis();

        cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH));
        cal.set(Calendar.HOUR_OF_DAY, 23);
        cal.set(Calendar.MINUTE, 59);
        cal.set(Calendar.SECOND, 59);
        cal.set(Calendar.MILLISECOND, 999);
        long end = cal.getTimeInMillis();

        return new long[]{start, end};
    }

    private static long[] getAnnualRange(long timestamp) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(timestamp);
        cal.set(Calendar.MONTH, Calendar.JANUARY);
        cal.set(Calendar.DAY_OF_MONTH, 1);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        long start = cal.getTimeInMillis();

        cal.set(Calendar.MONTH, Calendar.DECEMBER);
        cal.set(Calendar.DAY_OF_MONTH, 31);
        cal.set(Calendar.HOUR_OF_DAY, 23);
        cal.set(Calendar.MINUTE, 59);
        cal.set(Calendar.SECOND, 59);
        cal.set(Calendar.MILLISECOND, 999);
        long end = cal.getTimeInMillis();

        return new long[]{start, end};
    }
}
