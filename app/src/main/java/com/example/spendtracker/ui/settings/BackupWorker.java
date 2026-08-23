package com.example.spendtracker.ui.settings;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import com.example.spendtracker.util.StorageHelper;
import java.io.File;

public class BackupWorker extends Worker {

    public BackupWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        try {
            Context context = getApplicationContext();
            File dbDir = context.getDatabasePath("spend_tracker_db").getParentFile();
            File dbFile = context.getDatabasePath("spend_tracker_db");
            File walFile = new File(dbDir, "spend_tracker_db-wal");
            File shmFile = new File(dbDir, "spend_tracker_db-shm");
            
            File backupZip = new File(context.getExternalFilesDir(null), "backup.zip");
            StorageHelper.zipFiles(new File[]{dbFile, walFile, shmFile}, backupZip);

            long now = System.currentTimeMillis();
            context.getSharedPreferences("backup_prefs", Context.MODE_PRIVATE)
                   .edit().putLong("last_backup_time", now).apply();

            return Result.success();
        } catch (Exception e) {
            e.printStackTrace();
            return Result.failure();
        }
    }
}
