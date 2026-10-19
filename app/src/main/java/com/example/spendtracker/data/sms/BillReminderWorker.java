package com.example.spendtracker.data.sms;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.*;
import dagger.hilt.EntryPoint;
import dagger.hilt.InstallIn;
import dagger.hilt.components.SingletonComponent;
import dagger.hilt.android.EntryPointAccessors;
import java.util.concurrent.TimeUnit;

/** Persistent, inexact reminders survive process death and device reboot via WorkManager. */
public final class BillReminderWorker extends Worker {
    @EntryPoint @InstallIn(SingletonComponent.class)
    public interface Dependencies { AlertParsingService alertService(); }

    public BillReminderWorker(@NonNull Context context, @NonNull WorkerParameters parameters) {
        super(context, parameters);
    }

    public static void ensureScheduled(Context context) {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork("bill-reminders",
                ExistingPeriodicWorkPolicy.KEEP,
                new PeriodicWorkRequest.Builder(BillReminderWorker.class, 15, TimeUnit.MINUTES).build());
    }

    public static void checkNow(Context context) {
        ensureScheduled(context);
        WorkManager.getInstance(context).enqueueUniqueWork("bill-reminders-check",
                ExistingWorkPolicy.KEEP, new OneTimeWorkRequest.Builder(BillReminderWorker.class).build());
    }

    @NonNull @Override public Result doWork() {
        try {
            EntryPointAccessors.fromApplication(getApplicationContext(), Dependencies.class)
                    .alertService().notifyDueBills();
            return Result.success();
        } catch (Exception e) {
            android.util.Log.e("BillReminderWorker", "Could not check bill reminders", e);
            return Result.retry();
        }
    }
}
