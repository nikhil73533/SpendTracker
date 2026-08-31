package com.example.spendtracker.ui.settings;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import com.example.spendtracker.util.StorageHelper;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.Scope;
import com.google.android.gms.tasks.Task;
import dagger.hilt.android.lifecycle.HiltViewModel;
import java.io.File;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;

@HiltViewModel
public class BackupViewModel extends ViewModel {

    private final Application context;
    private final MutableLiveData<Long> lastBackupTime = new MutableLiveData<>(0L);
    private final MutableLiveData<Boolean> isAutoBackupEnabled = new MutableLiveData<>(false);
    private final MutableLiveData<String> driveAccountEmail = new MutableLiveData<>("");
    private final MutableLiveData<String> driveStatus = new MutableLiveData<>("");
    private final SharedPreferences prefs;

    /** Scope for Google Drive appdata folder access only (minimal permissions). */
    private static final String DRIVE_APPDATA_SCOPE = "https://www.googleapis.com/auth/drive.appdata";

    @Inject
    public BackupViewModel(Application context) {
        this.context = context;
        prefs = context.getSharedPreferences("backup_prefs", Context.MODE_PRIVATE);
        loadPrefs();
    }

    private void loadPrefs() {
        lastBackupTime.setValue(prefs.getLong("last_backup_time", 0L));
        isAutoBackupEnabled.setValue(prefs.getBoolean("auto_backup_enabled", false));
        String savedEmail = prefs.getString("drive_account_email", "");
        driveAccountEmail.setValue(savedEmail);

        // Check if there's an already signed-in account on app restart
        GoogleSignInAccount lastAccount = GoogleSignIn.getLastSignedInAccount(context);
        if (lastAccount != null && lastAccount.getEmail() != null) {
            if (GoogleSignIn.hasPermissions(lastAccount, new Scope(DRIVE_APPDATA_SCOPE))) {
                driveAccountEmail.setValue(lastAccount.getEmail());
                prefs.edit().putString("drive_account_email", lastAccount.getEmail()).apply();
                driveStatus.setValue("Connected");
            } else {
                // Has account but missing Drive scope — will need re-auth
                driveStatus.setValue("Re-authentication required");
            }
        }
    }

    public LiveData<Long> getLastBackupTime() { return lastBackupTime; }
    public LiveData<Boolean> getIsAutoBackupEnabled() { return isAutoBackupEnabled; }
    public LiveData<String> getDriveAccountEmail() { return driveAccountEmail; }
    public LiveData<String> getDriveStatus() { return driveStatus; }

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
        // Safe restore: copy to cache and let DatabaseModule handle it on next start
        File backupZip = new File(context.getExternalFilesDir(null), "backup.zip");
        if (backupZip.exists()) {
            try {
                File cacheZip = new File(context.getCacheDir(), "backup.zip");
                StorageHelper.copyFile(backupZip, cacheZip);
                return true;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return false;
    }

    // ── Google Drive Authentication ──────────────────────────────────────────

    /**
     * Builds the Google Sign-In intent with Drive appdata scope.
     * Signs out first to force account picker and avoid stale token issues.
     */
    public Intent getGoogleSignInIntent(Context activityContext) {
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(new Scope(DRIVE_APPDATA_SCOPE))
            .build();
        GoogleSignInClient client = GoogleSignIn.getClient(activityContext, gso);
        // Sign out first to force fresh account picker & avoid cached stale tokens
        client.signOut();
        driveStatus.postValue("Connecting...");
        return client.getSignInIntent();
    }

    /**
     * Handles the result from Google Sign-In ActivityResultLauncher.
     * Extracts the account email, validates scope grants, and persists state.
     */
    public void handleSignInResult(Intent data) {
        try {
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
            GoogleSignInAccount account = task.getResult(ApiException.class);
            if (account != null && account.getEmail() != null) {
                // Verify the Drive scope was actually granted
                if (account.getGrantedScopes() != null
                        && account.getGrantedScopes().contains(new Scope(DRIVE_APPDATA_SCOPE))) {
                    String email = account.getEmail();
                    prefs.edit().putString("drive_account_email", email).apply();
                    driveAccountEmail.postValue(email);
                    driveStatus.postValue("Connected as " + email);
                } else {
                    driveStatus.postValue("Drive permission not granted. Please try again.");
                    driveAccountEmail.postValue("");
                }
            } else {
                driveStatus.postValue("Sign-in succeeded but no email found");
                driveAccountEmail.postValue("");
            }
        } catch (ApiException e) {
            String errorMsg;
            switch (e.getStatusCode()) {
                case 12501: errorMsg = "Sign-in cancelled"; break;
                case 12502: errorMsg = "Sign-in currently in progress"; break;
                case 7:     errorMsg = "Network error. Check connectivity."; break;
                case 8:     errorMsg = "Internal error. Please try again."; break;
                case 10:    errorMsg = "Developer configuration error (check SHA-1 and OAuth setup)"; break;
                default:    errorMsg = "Sign-in failed (code: " + e.getStatusCode() + ")"; break;
            }
            android.util.Log.w("BackupViewModel", "Google Sign-In failed: code=" + e.getStatusCode(), e);
            driveStatus.postValue(errorMsg);
            driveAccountEmail.postValue("");
        }
    }

    /**
     * Disconnects the Google Drive account and clears persisted state.
     */
    public void disconnectDrive(Context activityContext) {
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(new Scope(DRIVE_APPDATA_SCOPE))
            .build();
        GoogleSignInClient client = GoogleSignIn.getClient(activityContext, gso);
        client.revokeAccess().addOnCompleteListener(task -> {
            prefs.edit().remove("drive_account_email").apply();
            driveAccountEmail.postValue("");
            driveStatus.postValue("Disconnected");
        });
    }
}
