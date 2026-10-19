package com.example.spendtracker.ui.settings;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import androidx.lifecycle.*;
import androidx.work.*;
import com.example.spendtracker.di.MainDatabase;
import com.example.spendtracker.data.local.database.SpendTrackerDatabase;
import com.example.spendtracker.domain.repository.SecurityRepository;
import com.example.spendtracker.util.*;
import dagger.hilt.android.lifecycle.HiltViewModel;
import java.io.InputStream;
import java.util.Arrays;
import java.util.concurrent.*;
import javax.inject.Inject;

@HiltViewModel
public class BackupViewModel extends ViewModel {
    private final Application context;
    private final SharedPreferences prefs;
    private final SpendTrackerDatabase database;
    private final SecurityRepository security;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final MutableLiveData<Long> lastBackupTime = new MutableLiveData<>(0L);
    private final MutableLiveData<Boolean> autoBackup = new MutableLiveData<>(false);
    private final MutableLiveData<String> status = new MutableLiveData<>("");
    private final MutableLiveData<Boolean> restoreReady = new MutableLiveData<>(false);
    private final MutableLiveData<Boolean> busy = new MutableLiveData<>(false);

    @Inject public BackupViewModel(Application context, @MainDatabase SpendTrackerDatabase database, SecurityRepository security) {
        this.context = context; this.database = database; this.security = security;
        prefs = context.getSharedPreferences("backup_prefs", Context.MODE_PRIVATE);
        lastBackupTime.setValue(prefs.getLong("last_backup_time", 0));
        autoBackup.setValue(prefs.getBoolean("auto_backup_enabled", false));
    }
    public LiveData<Long> getLastBackupTime() { return lastBackupTime; }
    public LiveData<Boolean> getIsAutoBackupEnabled() { return autoBackup; }
    public LiveData<String> getBackupStatus() { return status; }
    public LiveData<Boolean> getRestoreReady() { return restoreReady; }
    public LiveData<Boolean> getBusy() { return busy; }
    public boolean hasFolder() { return PhoneBackup.configured(context); }
    public void setAutoBackupEnabled(boolean enabled) {
        if (enabled && !hasFolder()) {
            status.setValue("Tap Backup Now to choose a folder and password first.");
            autoBackup.setValue(false); return;
        }
        prefs.edit().putBoolean("auto_backup_enabled", enabled).apply();
        autoBackup.setValue(enabled);
        if (enabled) WorkManager.getInstance(context).enqueueUniquePeriodicWork("daily_backup",
                ExistingPeriodicWorkPolicy.KEEP, new PeriodicWorkRequest.Builder(BackupWorker.class, 1, TimeUnit.DAYS).build());
        else WorkManager.getInstance(context).cancelUniqueWork("daily_backup");
    }
    public void configure(Uri folder, char[] password) {
        busy.setValue(true);
        executor.execute(() -> {
            try { PhoneBackup.configure(context, folder, password); save(); }
            catch (Exception error) { status.postValue("Backup setup failed. Choose a writable folder and try again."); }
            finally { Arrays.fill(password, '\0'); busy.postValue(false); }
        });
    }
    public void backupNow() {
        busy.setValue(true); status.setValue("Saving a protected copy…");
        executor.execute(() -> {
            try { save(); } catch (Exception error) { status.postValue("Backup failed. Check free space and select the folder again if access was removed."); }
            finally { busy.postValue(false); }
        });
    }
    private void save() throws Exception {
        byte[] key = security.getDatabasePassphrase();
        try { PhoneBackup.save(context, database, key); }
        finally { Arrays.fill(key, (byte) 0); }
        lastBackupTime.postValue(prefs.getLong("last_backup_time", 0));
        status.postValue("Backup saved in your chosen folder. Keep your password to recover it after reinstall.");
    }
    public void restore(Uri document, char[] password) {
        busy.setValue(true); status.setValue("Checking backup…");
        executor.execute(() -> {
            try (InputStream input = context.getContentResolver().openInputStream(document)) {
                if (input == null) throw new java.io.IOException("Cannot open backup");
                ReinstallRecovery.stagePortable(context, input, password);
                restoreReady.postValue(true);
            } catch (Exception error) { status.postValue("Restore failed. Check the password and backup file. Current records have not changed."); }
            finally { Arrays.fill(password, '\0'); busy.postValue(false); }
        });
    }
    @Override protected void onCleared() { executor.shutdown(); }
}
