package com.example.spendtracker.util;

import android.app.backup.*;
import android.os.ParcelFileDescriptor;
import java.io.*;
import java.util.Arrays;

/** The OS stops the normal app before full backup, so database files are quiescent. */
public final class PinchBackupAgent extends BackupAgent {
    @Override public void onFullBackup(FullBackupDataOutput output) throws IOException {
        if (android.os.Build.VERSION.SDK_INT < 28 ||
                (output.getTransportFlags() & (FLAG_CLIENT_SIDE_ENCRYPTION_ENABLED | FLAG_DEVICE_TO_DEVICE_TRANSFER)) == 0)
            throw new IOException("An encrypted backup transport is required");
        if (!getDatabasePath(BackupArchive.MAIN_DATABASE).isFile()) return;
        byte[] key = null;
        File payload = ReinstallRecovery.payload(this);
        File marker = new File(getNoBackupFilesDir(), ReinstallRecovery.LOCAL_BACKUP_MARKER);
        // Never replace an unfinished restore with a new backup.
        if (payload.exists()) throw new IOException("Restore is pending");
        try {
            try (FileOutputStream out = new FileOutputStream(marker)) { out.write(1); out.getFD().sync(); }
            key = new com.example.spendtracker.data.repository.SecurityRepositoryImpl(this).getDatabasePassphrase();
            ReinstallRecovery.snapshotQuiet(this, payload, key);
            super.onFullBackup(output);
        } finally {
            if (key != null) Arrays.fill(key, (byte) 0);
            ReinstallRecovery.delete(payload);
            ReinstallRecovery.delete(marker);
        }
    }
    @Override public void onBackup(ParcelFileDescriptor oldState, BackupDataOutput data, ParcelFileDescriptor newState) {}
    @Override public void onRestore(BackupDataInput data, int version, ParcelFileDescriptor newState) {}
}
