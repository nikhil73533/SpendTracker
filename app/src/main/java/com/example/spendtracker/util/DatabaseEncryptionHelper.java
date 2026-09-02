package com.example.spendtracker.util;

import android.content.Context;
import net.sqlcipher.database.SQLiteDatabase;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;

public class DatabaseEncryptionHelper {

    public static void migrateIfNecessary(Context context, String dbName, byte[] passphrase) {
        File dbFile = context.getDatabasePath(dbName);
        if (!dbFile.exists()) {
            return;
        }

        // Check for hanging large WAL
        File walFile = new File(dbFile.getAbsolutePath() + "-wal");
        if (walFile.exists()) {
             android.util.Log.e("RECOVERY_CORE", "WAL DETECTED (" + walFile.length() + " bytes). Checking WAL header...");
             try (FileInputStream fis = new FileInputStream(walFile)) {
                 byte[] walHeader = new byte[32];
                 if (fis.read(walHeader) == 32) {
                     // Magic bytes for SQLite WAL: 0x37 0x0f 0x06 0x37 (Little Endian) or 0x37 0x06 0x0f 0x37
                     android.util.Log.e("RECOVERY_CORE", "WAL Header (hex): " + bytesToHex(walHeader));
                 }
             } catch (IOException e) {}
        }

        // Safer check: read the first 16 bytes. Unencrypted SQLite starts with "SQLite format 3\0"
        boolean isUnencrypted = false;
        try (FileInputStream fis = new FileInputStream(dbFile)) {
            byte[] header = new byte[16];
            if (fis.read(header) == 16) {
                String magic = new String(header);
                android.util.Log.e("RECOVERY_CORE", "DB Header: " + magic);
                if (magic.startsWith("SQLite format 3")) {
                    isUnencrypted = true;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (isUnencrypted) {
            android.util.Log.e("RECOVERY_CORE", "MIGRATING UNENCRYPTED DB. Performing WAL checkpoint...");
            // Checkpoint WAL first using standard SQLite
            try {
                android.database.sqlite.SQLiteDatabase db = android.database.sqlite.SQLiteDatabase.openDatabase(
                        dbFile.getAbsolutePath(), null, android.database.sqlite.SQLiteDatabase.OPEN_READWRITE);
                db.rawQuery("PRAGMA wal_checkpoint(FULL);", null).close();
                db.close();
                android.util.Log.e("RECOVERY_CORE", "WAL CHECKPOINT COMPLETE.");
            } catch (Exception e) {
                android.util.Log.e("RECOVERY_CORE", "WAL CHECKPOINT FAILED: " + e.getMessage());
                e.printStackTrace();
            }

            try {
                android.util.Log.e("RECOVERY_CORE", "Encrypting database...");
                // Open unencrypted with SQLCipher (empty password) to perform migration
                SQLiteDatabase unencryptedDb = SQLiteDatabase.openOrCreateDatabase(dbFile, "", null);
                encryptDatabase(context, dbFile, unencryptedDb, passphrase);
                android.util.Log.e("RECOVERY_CORE", "ENCRYPTION COMPLETE.");
            } catch (Exception e) {
                android.util.Log.e("RECOVERY_CORE", "ENCRYPTION FAILED: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    private static void encryptDatabase(Context context, File oldDbFile, SQLiteDatabase unencryptedDb, byte[] passphrase) {
        File tempFile = new File(context.getCacheDir(), "temp_encrypted.db");
        if (tempFile.exists()) tempFile.delete();

        // Convert byte[] to Hex for ATTACH command
        StringBuilder hexString = new StringBuilder();
        for (byte b : passphrase) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        String keyHex = "x'" + hexString.toString() + "'";

        unencryptedDb.rawExecSQL("ATTACH DATABASE '" + tempFile.getAbsolutePath() + "' AS encrypted KEY " + keyHex + ";");
        unencryptedDb.rawExecSQL("SELECT sqlcipher_export('encrypted');");
        unencryptedDb.rawExecSQL("DETACH DATABASE encrypted;");
        unencryptedDb.close();

        // Backup old and swap files
        File backupFile = new File(oldDbFile.getAbsolutePath() + ".bak");
        if (backupFile.exists()) backupFile.delete();
        
        if (oldDbFile.renameTo(backupFile)) {
            if (!tempFile.renameTo(oldDbFile)) {
                backupFile.renameTo(oldDbFile);
            }
        }
        
        new File(oldDbFile.getAbsolutePath() + "-wal").delete();
        new File(oldDbFile.getAbsolutePath() + "-shm").delete();
    }

    public static boolean restoreInternalBackup(Context context, String dbName) {
        File dbFile = context.getDatabasePath(dbName);
        File backupFile = new File(dbFile.getAbsolutePath() + ".bak");
        File corruptFile = new File(dbFile.getAbsolutePath() + ".corrupt");
        File corruptWal = new File(dbFile.getAbsolutePath() + ".corrupt-wal");
        
        File src = null;
        if (corruptWal.exists() && corruptWal.length() > 0) src = corruptFile; // Corrupt exists if WAL exists
        else if (backupFile.exists() && backupFile.length() > 0) src = backupFile;
        else if (corruptFile.exists() && corruptFile.length() > 0) src = corruptFile;

        if (src == null) return false;

        try {
            // CRITICAL: Must close existing Room connections before swapping files
            // Since this is called from UI, it might be tricky. DatabaseModule handles it on startup.
            
            dbFile.delete();
            new File(dbFile.getAbsolutePath() + "-wal").delete();
            new File(dbFile.getAbsolutePath() + "-shm").delete();

            copyFile(src, dbFile);
            
            File srcWal = new File(src.getAbsolutePath() + "-wal");
            if (srcWal.exists()) {
                copyFile(srcWal, new File(dbFile.getAbsolutePath() + "-wal"));
            }
            File srcShm = new File(src.getAbsolutePath() + "-shm");
            if (srcShm.exists()) {
                copyFile(srcShm, new File(dbFile.getAbsolutePath() + "-shm"));
            }

            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    public static void ultimateRecoveryFromCache(Context context, String dbName, byte[] passphrase) {
        File cacheDir = context.getCacheDir();
        File backupZip = new File(cacheDir, "backup.zip");
        if (!backupZip.exists()) {
            // Also check external storage if cache is empty and DB is missing
            File extZip = new File(context.getExternalFilesDir(null), "backup.zip");
            if (extZip.exists() && !context.getDatabasePath(dbName).exists()) {
                backupZip = extZip;
                android.util.Log.e("RECOVERY_CORE", "Using EXTERNAL backup for fresh install recovery.");
            } else {
                return;
            }
        }

        android.util.Log.e("RECOVERY_CORE", "STARTING ULTIMATE RECOVERY FROM ZIP...");
        try {
            File dbFile = context.getDatabasePath(dbName);
            File dbDir = dbFile.getParentFile();
            if (!dbDir.exists()) dbDir.mkdirs();
            
            // Backup existing if it exists, just in case
            if (dbFile.exists()) {
                File emergencyBackup = new File(dbFile.getAbsolutePath() + ".emergency");
                copyFile(dbFile, emergencyBackup);
            }

            // 1. Unzip to databases folder
            com.example.spendtracker.util.StorageHelper.unzipFile(backupZip, dbDir);
            android.util.Log.e("RECOVERY_CORE", "Unzipped files to databases folder.");

            // 2. Check if unencrypted
            boolean isUnencrypted = false;
            if (dbFile.exists()) {
                try (FileInputStream fis = new FileInputStream(dbFile)) {
                    byte[] header = new byte[16];
                    if (fis.read(header) == 16) {
                        if (new String(header).startsWith("SQLite format 3")) {
                            isUnencrypted = true;
                        }
                    }
                }
            }
            
            if (isUnencrypted) {
                android.util.Log.e("RECOVERY_CORE", "Unzipped DB is unencrypted. Merging WAL...");
                // 3. Merge WAL
                try {
                    android.database.sqlite.SQLiteDatabase db = android.database.sqlite.SQLiteDatabase.openDatabase(
                            dbFile.getAbsolutePath(), null, android.database.sqlite.SQLiteDatabase.OPEN_READWRITE);
                    db.rawQuery("PRAGMA wal_checkpoint(FULL);", null).close();
                    db.close();
                    android.util.Log.e("RECOVERY_CORE", "WAL Merged. New size: " + dbFile.length());
                } catch (Exception e) {
                    android.util.Log.e("RECOVERY_CORE", "WAL Merge failed (might be empty): " + e.getMessage());
                }

                // 4. Encrypt
                SQLiteDatabase unencryptedDb = SQLiteDatabase.openOrCreateDatabase(dbFile, "", null);
                encryptDatabase(context, dbFile, unencryptedDb, passphrase);
                android.util.Log.e("RECOVERY_CORE", "Re-encryption complete.");
            } else {
                android.util.Log.e("RECOVERY_CORE", "Unzipped DB is encrypted. Attempting SQLCipher checkpoint...");
                try {
                    SQLiteDatabase encryptedDb = SQLiteDatabase.openOrCreateDatabase(dbFile.getAbsolutePath(), passphrase, null);
                    encryptedDb.rawExecSQL("PRAGMA wal_checkpoint(FULL);");
                    encryptedDb.close();
                    android.util.Log.e("RECOVERY_CORE", "SQLCipher checkpoint complete.");
                } catch (Exception e) {
                    android.util.Log.e("RECOVERY_CORE", "SQLCipher checkpoint failed: " + e.getMessage());
                }
            }

            // 5. Cleanup ZIP if it was from cache
            if (backupZip.getAbsolutePath().contains(context.getCacheDir().getAbsolutePath())) {
                backupZip.delete();
            }

            // 6. Sanitize restored columns
            try {
                SQLiteDatabase restoredDb = SQLiteDatabase.openOrCreateDatabase(dbFile.getAbsolutePath(), passphrase, null);
                restoredDb.rawExecSQL("UPDATE transactions SET status = 'ACTIVE' WHERE status IS NULL OR status = '';");
                restoredDb.rawExecSQL("UPDATE transactions SET categoryEmoji = '' WHERE categoryEmoji IS NULL;");
                restoredDb.rawExecSQL("UPDATE transactions SET fromAccount = '' WHERE fromAccount IS NULL;");
                restoredDb.rawExecSQL("UPDATE transactions SET toAccount = '' WHERE toAccount IS NULL;");
                restoredDb.rawExecSQL("UPDATE transactions SET fees = 0.0 WHERE fees IS NULL;");
                restoredDb.rawExecSQL("UPDATE transactions SET transactionGroupId = 0 WHERE transactionGroupId IS NULL;");
                restoredDb.rawExecSQL("UPDATE transactions SET deletedAt = 0 WHERE deletedAt IS NULL;");
                restoredDb.rawExecSQL("UPDATE transactions SET isRead = 1 WHERE isRead IS NULL;");
                restoredDb.rawExecSQL("UPDATE transactions SET category = 'Uncategorized' WHERE category IS NULL OR category = '';");
                restoredDb.rawExecSQL("UPDATE transactions SET type = 'EXPENSE' WHERE type IS NULL OR type = '';");
                restoredDb.close();
                android.util.Log.e("RECOVERY_CORE", "Restored database column sanitization complete.");
            } catch (Exception e) {
                android.util.Log.e("RECOVERY_CORE", "Column sanitization after recovery failed: " + e.getMessage());
            }
            android.util.Log.e("RECOVERY_CORE", "ULTIMATE RECOVERY SUCCESSFUL.");

        } catch (Exception e) {
            android.util.Log.e("RECOVERY_CORE", "ULTIMATE RECOVERY FAILED: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void copyFile(File src, File dst) throws IOException {
        try (FileChannel inChannel = new FileInputStream(src).getChannel();
             FileChannel outChannel = new FileOutputStream(dst).getChannel()) {
            inChannel.transferTo(0, inChannel.size(), outChannel);
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    public static boolean validateBackup(Context context, File zipFile, byte[] passphrase) {
        File tempDir = new File(context.getCacheDir(), "validate_backup");
        if (tempDir.exists()) deleteRecursive(tempDir);
        tempDir.mkdirs();

        try {
            StorageHelper.unzipFile(zipFile, tempDir);
            File dbFile = new File(tempDir, "spend_tracker_db");
            if (!dbFile.exists()) return false;

            // Try opening with passphrase
            try {
                // Using openOrCreateDatabase with byte[] passphrase as it's known to work in this version
                SQLiteDatabase db = SQLiteDatabase.openOrCreateDatabase(dbFile.getAbsolutePath(), passphrase, null);
                db.rawQuery("SELECT count(*) FROM transactions", null).close();
                db.close();
                return true;
            } catch (Exception e) {
                // Try unencrypted if passphrase fails (maybe it was an unencrypted backup)
                try {
                    android.database.sqlite.SQLiteDatabase unencrypted = android.database.sqlite.SQLiteDatabase.openDatabase(
                            dbFile.getAbsolutePath(), null, android.database.sqlite.SQLiteDatabase.OPEN_READONLY);
                    unencrypted.rawQuery("SELECT count(*) FROM transactions", null).close();
                    unencrypted.close();
                    return true;
                } catch (Exception e2) {
                    return false;
                }
            }
        } catch (Exception e) {
            return false;
        } finally {
            deleteRecursive(tempDir);
        }
    }

    private static void deleteRecursive(File fileOrDirectory) {
        if (fileOrDirectory.isDirectory()) {
            for (File child : fileOrDirectory.listFiles()) deleteRecursive(child);
        }
        fileOrDirectory.delete();
    }
}
