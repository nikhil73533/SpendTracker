package com.example.spendtracker.util;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import com.example.spendtracker.data.local.dao.TransactionDao;
import com.example.spendtracker.data.local.entity.TransactionEntity;
import com.example.spendtracker.di.MainDatabase;
import dagger.hilt.EntryPoint;
import dagger.hilt.InstallIn;
import dagger.hilt.android.EntryPointAccessors;
import dagger.hilt.components.SingletonComponent;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.concurrent.TimeUnit;

/** Checks configured UPI limits periodically and after a transaction is saved. */
public final class UpiLimitWorker extends Worker {
    private static final String PERIODIC = "upi-limit-check";
    private static final String IMMEDIATE = "upi-limit-check-now";

    @EntryPoint @InstallIn(SingletonComponent.class)
    public interface Dependencies { @MainDatabase TransactionDao transactionDao(); }

    public UpiLimitWorker(@NonNull Context context, @NonNull WorkerParameters parameters) { super(context, parameters); }

    public static void ensureScheduled(Context context) {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP,
                new PeriodicWorkRequest.Builder(UpiLimitWorker.class, 6, TimeUnit.HOURS).build());
    }

    public static void checkNow(Context context) {
        ensureScheduled(context);
        WorkManager.getInstance(context).enqueueUniqueWork(IMMEDIATE, ExistingWorkPolicy.REPLACE,
                new OneTimeWorkRequest.Builder(UpiLimitWorker.class).build());
    }

    @NonNull @Override public Result doWork() {
        try {
            Context context = getApplicationContext();
            double dailyLimit = UpiLimitPreferences.dailyLimit(context);
            double monthlyLimit = UpiLimitPreferences.monthlyLimit(context);
            if (dailyLimit <= 0 && monthlyLimit <= 0) return Result.success();
            ZoneId zone = ZoneId.systemDefault();
            long now = System.currentTimeMillis();
            LocalDate today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate();
            long monthStart = today.withDayOfMonth(1).atStartOfDay(zone).toInstant().toEpochMilli();
            TransactionDao dao = EntryPointAccessors.fromApplication(context, Dependencies.class).transactionDao();
            double daily = 0, monthly = 0;
            for (TransactionEntity row : dao.getActiveTransactionsInRangeSync(monthStart, now)) {
                if (!UpiUsageTracker.isOutgoingUpi(row.type, row.category, row.direction, row.source, row.sourceType, row.upiId)) continue;
                monthly += row.amount;
                LocalDate date = Instant.ofEpochMilli(row.date).atZone(zone).toLocalDate();
                if (today.equals(date)) daily += row.amount;
            }
            UpiUsage usage = new UpiUsage(daily, monthly, 0, 0, dailyLimit, monthlyLimit);
            String key = today + ":" + usage.getDailyPercent() + ":" + usage.getMonthlyPercent();
            if (!usage.isDailyNearLimit() && !usage.isMonthlyNearLimit()) return Result.success();
            android.content.SharedPreferences prefs = context.getSharedPreferences("upi_limit_notifications", Context.MODE_PRIVATE);
            if (key.equals(prefs.getString("last", ""))) return Result.success();
            String body = (usage.isDailyNearLimit() ? "Today: " + usage.getDailyPercent() + "% of your configured UPI limit. " : "")
                    + (usage.isMonthlyNearLimit() ? "This month: " + usage.getMonthlyPercent() + "% of your configured UPI limit." : "");
            if (AppNotifications.post(context, AppNotifications.UPI, 41001, "UPI limit almost reached", body.trim())) {
                prefs.edit().putString("last", key).apply();
            }
            return Result.success();
        } catch (Exception e) {
            android.util.Log.e("UpiLimitWorker", "UPI usage check failed", e);
            return Result.retry();
        }
    }
}
