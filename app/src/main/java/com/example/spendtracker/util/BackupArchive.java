package com.example.spendtracker.util;

import android.content.Context;
import androidx.sqlite.db.SupportSQLiteDatabase;
import com.example.prediction.data.local.PredictionDatabase;
import com.example.spendtracker.data.local.database.SpendTrackerDatabase;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Creates and restores a complete local backup: encrypted finance data plus the learning model. */
public final class BackupArchive {
    public static final String MAIN_DATABASE = "spend_tracker_db";
    public static final String PREDICTION_DATABASE = "prediction_database";
    private static final String STAGED_RESTORE = "backup.zip";
    private static final String RESTORE_PREFS = "backup_restore";
    private static final String PREDICTION_PENDING = "prediction_restore_pending";

    private BackupArchive() {}

    public static File create(Context context, SpendTrackerDatabase mainDatabase) throws IOException {
        checkpoint(mainDatabase);
        checkpoint(PredictionDatabase.getDatabase(context));
        File databases = context.getDatabasePath(MAIN_DATABASE).getParentFile();
        File backups = new File(context.getExternalFilesDir(null), "backups");
        if (!backups.exists() && !backups.mkdirs()) throw new IOException("Could not create backup directory");
        String name = "spendtracker-" + new java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US)
                .format(new java.util.Date()) + ".zip";
        File archive = new File(backups, name);
        StorageHelper.zipFiles(new File[]{
                new File(databases, MAIN_DATABASE), new File(databases, MAIN_DATABASE + "-wal"), new File(databases, MAIN_DATABASE + "-shm"),
                new File(databases, PREDICTION_DATABASE), new File(databases, PREDICTION_DATABASE + "-wal"), new File(databases, PREDICTION_DATABASE + "-shm")
        }, archive);
        if (!archive.isFile() || archive.length() == 0) throw new IOException("Backup archive is empty");
        return archive;
    }

    public static File latest(Context context) {
        File folder = new File(context.getExternalFilesDir(null), "backups");
        File[] files = folder.listFiles((dir, name) -> name.startsWith("spendtracker-") && name.endsWith(".zip"));
        if (files == null || files.length == 0) return null;
        java.util.Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        return files[0];
    }

    public static boolean stageRestore(Context context, File archive) {
        if (archive == null || !archive.isFile()) return false;
        try {
            StorageHelper.copyFile(archive, new File(context.getCacheDir(), STAGED_RESTORE));
            context.getSharedPreferences(RESTORE_PREFS, Context.MODE_PRIVATE).edit().putBoolean(PREDICTION_PENDING, true).apply();
            return true;
        } catch (IOException e) { return false; }
    }

    /** Restores only known prediction database entries from a user-selected staged archive. */
    public static void restorePredictionIfPending(Context context) {
        android.content.SharedPreferences prefs = context.getSharedPreferences(RESTORE_PREFS, Context.MODE_PRIVATE);
        if (!prefs.getBoolean(PREDICTION_PENDING, false)) return;
        File staged = new File(context.getCacheDir(), STAGED_RESTORE);
        if (!staged.isFile()) return;
        File databases = context.getDatabasePath(PREDICTION_DATABASE).getParentFile();
        boolean restored = false;
        try (ZipInputStream input = new ZipInputStream(new BufferedInputStream(new FileInputStream(staged)))) {
            ZipEntry entry;
            byte[] buffer = new byte[8192];
            while ((entry = input.getNextEntry()) != null) {
                String name = entry.getName();
                if (!(PREDICTION_DATABASE.equals(name) || (PREDICTION_DATABASE + "-wal").equals(name)
                        || (PREDICTION_DATABASE + "-shm").equals(name))) continue;
                File target = new File(databases, name);
                try (FileOutputStream output = new FileOutputStream(target, false)) {
                    int read;
                    while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
                }
                restored = restored || PREDICTION_DATABASE.equals(name);
                input.closeEntry();
            }
        } catch (IOException e) {
            android.util.Log.e("BackupArchive", "Prediction restore failed", e);
            return;
        }
        if (restored) prefs.edit().remove(PREDICTION_PENDING).apply();
    }

    private static void checkpoint(androidx.room.RoomDatabase database) {
        try {
            SupportSQLiteDatabase sqlite = database.getOpenHelper().getWritableDatabase();
            sqlite.execSQL("PRAGMA wal_checkpoint(FULL)");
        } catch (Exception e) {
            android.util.Log.w("BackupArchive", "Database checkpoint skipped", e);
        }
    }
}
