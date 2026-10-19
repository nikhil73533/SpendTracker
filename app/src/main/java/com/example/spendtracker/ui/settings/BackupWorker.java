package com.example.spendtracker.ui.settings;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import com.example.spendtracker.di.MainDatabase;
import com.example.spendtracker.data.local.database.SpendTrackerDatabase;
import com.example.spendtracker.util.BackupArchive;
import com.example.spendtracker.util.DriveBackupClient;
import dagger.hilt.EntryPoint;
import dagger.hilt.InstallIn;
import dagger.hilt.android.EntryPointAccessors;
import dagger.hilt.components.SingletonComponent;
import java.io.File;

public class BackupWorker extends Worker {

    public static void ensureScheduled(Context context) {
        if (context.getSharedPreferences("backup_prefs", 0).getBoolean("auto_backup_enabled", false)
                && com.example.spendtracker.util.PhoneBackup.configured(context)) {
            androidx.work.WorkManager.getInstance(context).enqueueUniquePeriodicWork("daily_backup",
                    androidx.work.ExistingPeriodicWorkPolicy.KEEP,
                    new androidx.work.PeriodicWorkRequest.Builder(BackupWorker.class, 1,
                            java.util.concurrent.TimeUnit.DAYS).build());
        }
    }

    @EntryPoint @InstallIn(SingletonComponent.class)
    public interface Dependencies { @MainDatabase SpendTrackerDatabase database(); com.example.spendtracker.domain.repository.SecurityRepository security(); }

    public BackupWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        try {
            Context context = getApplicationContext();
            SpendTrackerDatabase database = EntryPointAccessors.fromApplication(context, Dependencies.class).database();
            if (!context.getSharedPreferences("backup_prefs", 0).getBoolean("auto_backup_enabled", false)) return Result.success();
            byte[] key = EntryPointAccessors.fromApplication(context, Dependencies.class).security().getDatabasePassphrase();
            try { com.example.spendtracker.util.PhoneBackup.save(context, database, key); }
            finally { java.util.Arrays.fill(key, (byte) 0); }

            long now = System.currentTimeMillis();
            context.getSharedPreferences("backup_prefs", Context.MODE_PRIVATE)
                   .edit().putLong("last_backup_time", now).apply();

            return Result.success();
        } catch (Exception e) {
            e.printStackTrace();
            return getRunAttemptCount() < 3 ? Result.retry() : Result.failure();
        }
    }
}
