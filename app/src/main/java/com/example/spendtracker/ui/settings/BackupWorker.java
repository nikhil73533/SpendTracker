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

    @EntryPoint @InstallIn(SingletonComponent.class)
    public interface Dependencies { @MainDatabase SpendTrackerDatabase database(); }

    public BackupWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        try {
            Context context = getApplicationContext();
            SpendTrackerDatabase database = EntryPointAccessors.fromApplication(context, Dependencies.class).database();
            File backupZip = BackupArchive.create(context, database);
            if (DriveBackupClient.isConnected(context)) DriveBackupClient.upload(context, backupZip);

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
