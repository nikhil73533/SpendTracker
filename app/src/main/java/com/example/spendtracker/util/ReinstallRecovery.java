package com.example.spendtracker.util;

import android.content.Context;
import com.example.spendtracker.data.local.database.SpendTrackerDatabase;
import com.example.prediction.data.local.PredictionDatabase;
import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.zip.*;

/** Portable database snapshots and crash-recoverable installation before any Room connection opens. */
public final class ReinstallRecovery {
    public static final String PAYLOAD = "reinstall-payload";
    public static final String KEY = "database-key";
    public static final String LOCAL_BACKUP_MARKER = "local-backup-in-progress";
    private static final String MAIN = BackupArchive.MAIN_DATABASE;
    private static final String MODEL = BackupArchive.PREDICTION_DATABASE;
    private static final Set<String> ENTRIES = new HashSet<>(Arrays.asList(
            MAIN, MAIN + "-wal", MODEL, MODEL + "-wal", KEY));
    private ReinstallRecovery() {}
    public static File payload(Context context) { return new File(context.getFilesDir(), PAYLOAD); }

    public static void snapshotQuiet(Context context, File directory, byte[] key) throws IOException {
        delete(directory);
        if (!directory.mkdirs()) throw new IOException("Cannot prepare backup");
        for (String name : ENTRIES) {
            if (KEY.equals(name)) continue;
            File source = context.getDatabasePath(name);
            if (source.isFile()) Files.copy(source.toPath(), new File(directory, name).toPath());
        }
        if (!new File(directory, MAIN).isFile()) throw new IOException("No finance database to back up");
        Files.write(new File(directory, KEY).toPath(), key);
    }

    public static void writePortable(Context context, SpendTrackerDatabase db, byte[] key,
                                     char[] password, OutputStream output) throws Exception {
        File temp = new File(context.getCacheDir(), "portable-snapshot");
        File zip = new File(context.getCacheDir(), "portable-snapshot.zip");
        PredictionDatabase model = PredictionDatabase.getDatabase(context);
        try {
            // Exclusive writer locks prevent the DB and its WAL changing while they are copied.
            db.beginTransaction();
            try {
                model.beginTransaction();
                try { snapshotQuiet(context, temp, key); }
                finally { model.endTransaction(); }
            } finally { db.endTransaction(); }
            StorageHelper.zipFiles(Objects.requireNonNull(temp.listFiles()), zip);
            try (InputStream input = new FileInputStream(zip)) { RecoveryCipher.encrypt(input, output, password); }
        } finally { delete(temp); zip.delete(); }
    }

    public static void stagePortable(Context context, InputStream input, char[] password) throws Exception {
        File zip = new File(context.getCacheDir(), "restore-authenticated.zip");
        File stage = new File(context.getCacheDir(), "restore-validated");
        delete(stage); stage.mkdirs();
        try {
            try (OutputStream output = new FileOutputStream(zip)) { RecoveryCipher.decrypt(input, output, password); }
            long total = 0;
            Set<String> found = new HashSet<>();
            try (ZipInputStream stream = new ZipInputStream(new FileInputStream(zip))) {
                ZipEntry entry; byte[] bytes = new byte[8192];
                while ((entry = stream.getNextEntry()) != null) {
                    String name = entry.getName();
                    if (!ENTRIES.contains(name) || !found.add(name) || entry.isDirectory())
                        throw new IOException("Invalid backup entry");
                    try (OutputStream out = new FileOutputStream(new File(stage, name))) {
                        int count;
                        while ((count = stream.read(bytes)) != -1) {
                            if ((total += count) > RecoveryCipher.MAX_BYTES) throw new IOException("Backup is too large");
                            out.write(bytes, 0, count);
                        }
                    }
                }
            }
            validate(stage);
            File target = payload(context);
            if (target.exists()) throw new IOException("A restore is already pending. Restart the app first.");
            if (!stage.renameTo(target)) throw new IOException("Cannot stage recovery");
        } finally { zip.delete(); delete(stage); }
    }

    public static byte[] readKey(File folder) throws IOException {
        File file = new File(folder, KEY);
        if (file.length() != 32) throw new IOException("Missing or invalid recovery key");
        return Files.readAllBytes(file.toPath());
    }

    private static void validate(File folder) throws IOException {
        byte[] key = readKey(folder);
        if (!new File(folder, MAIN).isFile()) throw new IOException("Missing finance database");
        System.loadLibrary("sqlcipher");
        try (net.zetetic.database.sqlcipher.SQLiteDatabase db =
                     net.zetetic.database.sqlcipher.SQLiteDatabase.openOrCreateDatabase(
                             new File(folder, MAIN).getPath(), key, null, null)) {
            try (android.database.Cursor check = db.rawQuery("PRAGMA quick_check", null)) {
                if (!check.moveToFirst() || !"ok".equals(check.getString(0))) throw new IOException("Damaged finance database");
            }
            try (android.database.Cursor check = db.rawQuery("SELECT count(*) FROM transactions", null)) {
                if (!check.moveToFirst()) throw new IOException("Missing transactions");
            }
            if (db.getVersion() > 19) throw new IOException("Install a newer app version to restore this backup");
        } catch (RuntimeException error) { throw new IOException("Cannot read finance backup", error); }
        finally { Arrays.fill(key, (byte) 0); }
        File model = new File(folder, MODEL);
        if (model.exists()) {
            try (android.database.sqlite.SQLiteDatabase db = android.database.sqlite.SQLiteDatabase.openDatabase(
                    model.getPath(), null, android.database.sqlite.SQLiteDatabase.OPEN_READWRITE);
                 android.database.Cursor check = db.rawQuery("PRAGMA quick_check", null)) {
                if (!check.moveToFirst() || !"ok".equals(check.getString(0))) throw new IOException("Damaged learning model");
                if (db.getVersion() < 4 || db.getVersion() > 5)
                    throw new IOException("This learning model needs a compatible app version");
            } catch (RuntimeException error) { throw new IOException("Cannot read learning model", error); }
        }
    }

    /** Invoked from Application.attachBaseContext, before providers, workers or Hilt open databases. */
    public static void installPending(Context context) throws IOException {
        File source = payload(context);
        File localBackup = new File(context.getNoBackupFilesDir(), LOCAL_BACKUP_MARKER);
        // This marker is deliberately not backed up. A leftover locally-created snapshot
        // must never be mistaken for data that Android has restored after a reinstall.
        if (localBackup.exists()) {
            delete(source); delete(localBackup);
            return;
        }
        File previous = new File(context.getNoBackupFilesDir(), "recovery-previous");
        if (new File(previous, "key-imported").exists()) {
            cleanupCommitted(context);
            return;
        }
        if (!source.exists()) return;
        File databases = context.getDatabasePath(MAIN).getParentFile();
        File next = new File(context.getNoBackupFilesDir(), "recovery-next");
        if (!previous.exists()) {
            validate(source);
            delete(next); if (!next.mkdirs()) throw new IOException("Cannot prepare restored databases");
            for (String name : ENTRIES) {
                if (!KEY.equals(name) && new File(source, name).exists())
                    Files.copy(new File(source, name).toPath(), new File(next, name).toPath());
            }
            if (!databases.exists() && !databases.mkdirs()) throw new IOException("Cannot prepare database directory");
            if (!databases.renameTo(previous)) throw new IOException("Cannot preserve current database");
        }
        // If interrupted between renames, finish on the next process start. Keep the previous DB
        // until the recovery key has been durably imported into this installation's Keystore.
        if (!databases.exists() && !next.renameTo(databases)) throw new IOException("Cannot install restored databases");
    }

    public static void finishKeyImport(Context context) throws IOException {
        File previous = new File(context.getNoBackupFilesDir(), "recovery-previous");
        try (FileOutputStream marker = new FileOutputStream(new File(previous, "key-imported"))) {
            marker.write(1); marker.getFD().sync();
        }
        cleanupCommitted(context);
    }

    public static boolean needsKeyImport(Context context) {
        File previous = new File(context.getNoBackupFilesDir(), "recovery-previous");
        return payload(context).exists() && previous.exists() && !new File(previous, "key-imported").exists();
    }

    private static void cleanupCommitted(Context context) {
        try {
            delete(payload(context));
            delete(new File(context.getNoBackupFilesDir(), "recovery-previous"));
            delete(new File(context.getNoBackupFilesDir(), "recovery-next"));
        } catch (IOException error) {
            // Key and databases are committed. Retry cleanup on startup without reapplying data.
            android.util.Log.w("ReinstallRecovery", "Recovery cleanup will be retried");
        }
    }

    public static void delete(File file) throws IOException {
        if (!file.exists()) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children == null) throw new IOException("Cannot read recovery directory");
            for (File child : children) delete(child);
        }
        if (!file.delete()) throw new IOException("Cannot remove temporary recovery data");
    }
}
