package com.example.spendtracker.ui.settings;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import com.example.spendtracker.util.StorageHelper;
import dagger.hilt.android.lifecycle.HiltViewModel;
import dagger.hilt.android.qualifiers.ApplicationContext;
import java.io.File;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;

@HiltViewModel
public class BackupViewModel extends ViewModel {

    private final Application context;
    private final MutableLiveData<Long> lastBackupTime = new MutableLiveData<>(0L);
    private final MutableLiveData<Boolean> isAutoBackupEnabled = new MutableLiveData<>(false);
    private final SharedPreferences prefs;

    @Inject
    public BackupViewModel(Application context) {
        this.context = context;
        prefs = context.getSharedPreferences("backup_prefs", Context.MODE_PRIVATE);
        loadPrefs();
    }

    private void loadPrefs() {
        lastBackupTime.setValue(prefs.getLong("last_backup_time", 0L));
        isAutoBackupEnabled.setValue(prefs.getBoolean("auto_backup_enabled", false));
    }

    public LiveData<Long> getLastBackupTime() { return lastBackupTime; }
    public LiveData<Boolean> getIsAutoBackupEnabled() { return isAutoBackupEnabled; }

    public void setAutoBackupEnabled(boolean enabled) {
        prefs.edit().putBoolean("auto_backup_enabled", enabled).apply();
        isAutoBackupEnabled.setValue(enabled);

        if (enabled) {
            PeriodicWorkRequest backupWork = new PeriodicWorkRequest.Builder(BackupWorker.class, 1, TimeUnit.DAYS).build();
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("daily_backup", ExistingPeriodicWorkPolicy.KEEP, backupWork);
        } else {
            WorkManager.getInstance(context).cancelUniqueWork("daily_backup");
        }
    }

    public void backupNow() {
        // Simplified Backup logic
        try {
            File dbDir = context.getDatabasePath("spend_tracker_db").getParentFile();
            File dbFile = context.getDatabasePath("spend_tracker_db");
            File walFile = new File(dbDir, "spend_tracker_db-wal");
            File shmFile = new File(dbDir, "spend_tracker_db-shm");
            
            File backupZip = new File(context.getExternalFilesDir(null), "backup.zip");
            StorageHelper.zipFiles(new File[]{dbFile, walFile, shmFile}, backupZip);

            long now = System.currentTimeMillis();
            prefs.edit().putLong("last_backup_time", now).apply();
            lastBackupTime.postValue(now);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public boolean restoreNow() {
        // Restore logic using zip
        File backupZip = new File(context.getExternalFilesDir(null), "backup.zip");
        if (backupZip.exists()) {
            try {
                // Simplified restore: unzip to temp, then use DatabaseEncryptionHelper or direct replace
                File dbDir = context.getDatabasePath("spend_tracker_db").getParentFile();
                StorageHelper.unzipFile(backupZip, dbDir);
                return true;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return false;
    }
}
