package com.example.spendtracker.release;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.example.spendtracker.util.*;
import com.example.spendtracker.data.repository.SecurityRepositoryImpl;
import org.junit.*;
import org.junit.runner.RunWith;
import java.io.*;
import java.nio.file.Files;
import java.util.*;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class ReinstallRecoveryDeviceTest {
    private File root;
    private Context target;
    private byte[] key;
    @Before public void setup() throws Exception {
        System.loadLibrary("sqlcipher");
        Context app = ApplicationProvider.getApplicationContext();
        root = new File(app.getCacheDir(), "recovery-test-" + UUID.randomUUID()); root.mkdirs();
        target = isolatedContext(app, new File(root, "databases"));
        key = new byte[32]; new java.security.SecureRandom().nextBytes(key);
    }
    private Context isolatedContext(Context base, File databases) {
        return new ContextWrapper(base) {
            @Override public Context getApplicationContext() { return this; }
            private File dir(String name) { File result = new File(root, name); result.mkdirs(); return result; }
            @Override public File getFilesDir() { return dir("files"); }
            @Override public File getNoBackupFilesDir() { return dir("no-backup"); }
            @Override public File getCacheDir() { return dir("cache"); }
            @Override public File getDatabasePath(String name) { return new File(databases, name); }
            @Override public android.database.sqlite.SQLiteDatabase openOrCreateDatabase(String name, int mode,
                    android.database.sqlite.SQLiteDatabase.CursorFactory factory) {
                return openOrCreateDatabase(name, mode, factory, null);
            }
            @Override public android.database.sqlite.SQLiteDatabase openOrCreateDatabase(String name, int mode,
                    android.database.sqlite.SQLiteDatabase.CursorFactory factory, android.database.DatabaseErrorHandler handler) {
                databases.mkdirs();
                return android.database.sqlite.SQLiteDatabase.openDatabase(getDatabasePath(name).getPath(), factory,
                        android.database.sqlite.SQLiteDatabase.CREATE_IF_NECESSARY, handler);
            }
            @Override public boolean deleteDatabase(String name) {
                return android.database.sqlite.SQLiteDatabase.deleteDatabase(getDatabasePath(name));
            }
            @Override public SharedPreferences getSharedPreferences(String name, int mode) {
                return super.getSharedPreferences(root.getName() + "-" + name, mode);
            }
        };
    }
    @After public void cleanup() throws Exception {
        target.deleteSharedPreferences(root.getName() + "-secure_prefs");
        ReinstallRecovery.delete(root);
    }
    private File archiveFolder() throws Exception {
        File folder = new File(root, "original"); folder.mkdirs();
        Files.write(new File(folder, ReinstallRecovery.KEY).toPath(), key);
        Context source = isolatedContext(ApplicationProvider.getApplicationContext(), folder);
        com.example.spendtracker.data.local.database.SpendTrackerDatabase db = androidx.room.Room.databaseBuilder(
                source, com.example.spendtracker.data.local.database.SpendTrackerDatabase.class, BackupArchive.MAIN_DATABASE)
                .openHelperFactory(new net.zetetic.database.sqlcipher.SupportOpenHelperFactory(key.clone())).build();
        try {
            com.example.spendtracker.data.local.entity.TransactionEntity row = new com.example.spendtracker.data.local.entity.TransactionEntity();
            row.amount = 1234.50; row.type = "EXPENSE"; row.category = "Food"; row.date = System.currentTimeMillis();
            db.transactionDao().insertTransaction(row);
        } finally { db.close(); }
        com.example.prediction.data.local.PredictionDatabase modelDb = androidx.room.Room.databaseBuilder(
                source, com.example.prediction.data.local.PredictionDatabase.class, BackupArchive.PREDICTION_DATABASE).build();
        try {
            modelDb.globalCategoryStatsDao().insert(new com.example.prediction.data.local.entity.GlobalCategoryStatsEntity("Food", "EXPENSE", 7));
        } finally { modelDb.close(); }
        return folder;
    }
    private byte[] portable(File folder) throws Exception {
        File zip = new File(root, "backup.zip"); StorageHelper.zipFiles(folder.listFiles(), zip);
        ByteArrayOutputStream encrypted = new ByteArrayOutputStream();
        try (InputStream input = new FileInputStream(zip)) { RecoveryCipher.encrypt(input, encrypted, "test-backup-password".toCharArray()); }
        return encrypted.toByteArray();
    }
    private void assertRestored() throws Exception {
        ReinstallRecovery.installPending(target);
        // Re-entering startup before key import must not reapply or erase the recovery.
        ReinstallRecovery.installPending(target);
        final byte[][] imported = new byte[1][];
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().runOnMainSync(() ->
                imported[0] = new SecurityRepositoryImpl(target).getDatabasePassphrase());
        assertArrayEquals(key, imported[0]);
        assertFalse(ReinstallRecovery.payload(target).exists());
        com.example.spendtracker.data.local.database.SpendTrackerDatabase db = androidx.room.Room.databaseBuilder(
                target, com.example.spendtracker.data.local.database.SpendTrackerDatabase.class, BackupArchive.MAIN_DATABASE)
                .openHelperFactory(new net.zetetic.database.sqlcipher.SupportOpenHelperFactory(imported[0].clone())).build();
        try (android.database.Cursor rows = db.getOpenHelper().getReadableDatabase().query("SELECT amount FROM transactions")) {
            assertTrue(rows.moveToFirst()); assertEquals(1234.50, rows.getDouble(0), .001);
        } finally { db.close(); }
        com.example.prediction.data.local.PredictionDatabase modelDb = androidx.room.Room.databaseBuilder(
                target, com.example.prediction.data.local.PredictionDatabase.class, BackupArchive.PREDICTION_DATABASE).build();
        try {
            assertEquals(7, modelDb.globalCategoryStatsDao().getByCategory("Food").count);
        } finally { modelDb.close(); }
    }
    @Test public void phoneFileRestoresRecordsAndKeyIntoFreshInstallation() throws Exception {
        byte[] file = portable(archiveFolder());
        ReinstallRecovery.stagePortable(target, new ByteArrayInputStream(file), "test-backup-password".toCharArray());
        assertRestored();
    }
    @Test public void androidRestoredPayloadImportsNewKeystoreKey() throws Exception {
        File folder = archiveFolder();
        assertTrue(folder.renameTo(ReinstallRecovery.payload(target)));
        assertRestored();
    }
    @Test public void wrongPasswordLeavesExistingRecordsUntouched() throws Exception {
        File current = target.getDatabasePath(BackupArchive.MAIN_DATABASE); current.getParentFile().mkdirs();
        Files.write(current.toPath(), new byte[]{9,8,7});
        byte[] file = portable(archiveFolder());
        assertThrows(Exception.class, () -> ReinstallRecovery.stagePortable(target, new ByteArrayInputStream(file), "wrong".toCharArray()));
        assertArrayEquals(new byte[]{9,8,7}, Files.readAllBytes(current.toPath()));
        assertFalse(ReinstallRecovery.payload(target).exists());
    }
    @Test public void unexpectedArchiveEntryIsRejected() throws Exception {
        File folder = archiveFolder(); Files.write(new File(folder, "unexpected").toPath(), new byte[]{1});
        byte[] file = portable(folder);
        assertThrows(Exception.class, () -> ReinstallRecovery.stagePortable(target, new ByteArrayInputStream(file), "test-backup-password".toCharArray()));
        assertFalse(ReinstallRecovery.payload(target).exists());
    }
    @Test public void interruptedBackupIsNotMistakenForRestore() throws Exception {
        File current = target.getDatabasePath(BackupArchive.MAIN_DATABASE); current.getParentFile().mkdirs();
        Files.write(current.toPath(), new byte[]{9,8,7});
        ReinstallRecovery.payload(target).mkdirs();
        Files.write(new File(ReinstallRecovery.payload(target), "partial-file").toPath(), new byte[]{1});
        Files.write(new File(target.getNoBackupFilesDir(), ReinstallRecovery.LOCAL_BACKUP_MARKER).toPath(), new byte[]{1});
        ReinstallRecovery.installPending(target);
        assertArrayEquals(new byte[]{9,8,7}, Files.readAllBytes(current.toPath()));
        assertFalse(ReinstallRecovery.payload(target).exists());
    }
    @Test public void snapshotIncludesCommittedWalRecords() throws Exception {
        File folder = archiveFolder();
        Context source = isolatedContext(ApplicationProvider.getApplicationContext(), folder);
        File snapshot = new File(root, "snapshot");
        try (net.zetetic.database.sqlcipher.SQLiteDatabase db = net.zetetic.database.sqlcipher.SQLiteDatabase.openOrCreateDatabase(
                new File(folder, BackupArchive.MAIN_DATABASE).getPath(), key, null, null)) {
            db.enableWriteAheadLogging();
            db.execSQL("UPDATE transactions SET amount=9876.5");
            assertTrue(new File(folder, BackupArchive.MAIN_DATABASE + "-wal").length() > 0);
            db.beginTransaction();
            try { ReinstallRecovery.snapshotQuiet(source, snapshot, key); }
            finally { db.endTransaction(); }
        }
        ReinstallRecovery.stagePortable(target, new ByteArrayInputStream(portable(snapshot)), "test-backup-password".toCharArray());
        ReinstallRecovery.installPending(target);
        try (net.zetetic.database.sqlcipher.SQLiteDatabase db = net.zetetic.database.sqlcipher.SQLiteDatabase.openOrCreateDatabase(
                target.getDatabasePath(BackupArchive.MAIN_DATABASE).getPath(), key, null, null);
             android.database.Cursor rows = db.rawQuery("SELECT amount FROM transactions", null)) {
            assertTrue(rows.moveToFirst()); assertEquals(9876.5, rows.getDouble(0), .001);
        }
    }
}
