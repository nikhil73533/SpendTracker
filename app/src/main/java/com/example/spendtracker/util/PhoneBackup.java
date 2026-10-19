package com.example.spendtracker.util;

import android.content.*;
import android.net.Uri;
import android.provider.DocumentsContract;
import androidx.security.crypto.*;
import com.example.spendtracker.data.local.database.SpendTrackerDatabase;
import java.io.*;
import java.util.Arrays;

/** Writes new immutable copies into a user-selected folder outside app-owned storage. */
public final class PhoneBackup {
    private PhoneBackup() {}
    private static SharedPreferences secrets(Context context) throws Exception {
        return EncryptedSharedPreferences.create("phone_backup_secrets",
                MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC), context,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM);
    }
    public static boolean configured(Context context) {
        try { return secrets(context).contains("folder"); } catch (Exception e) { return false; }
    }
    public static void configure(Context context, Uri folder, char[] password) throws Exception {
        if (password.length < 10) throw new IOException("Use a backup password of at least 10 characters");
        context.getContentResolver().takePersistableUriPermission(folder,
                Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        if (!secrets(context).edit().putString("folder", folder.toString())
                .putString("password", new String(password)).commit()) throw new IOException("Could not save backup settings");
    }
    public static synchronized void save(Context context, SpendTrackerDatabase db, byte[] key) throws Exception {
        SharedPreferences prefs = secrets(context);
        String folder = prefs.getString("folder", null), storedPassword = prefs.getString("password", null);
        if (folder == null || storedPassword == null) throw new IOException("Choose a phone backup folder first");
        char[] password = storedPassword.toCharArray();
        File encrypted = new File(context.getCacheDir(), "phone-backup.pinch");
        Uri document = null;
        try {
            try (OutputStream output = new FileOutputStream(encrypted)) {
                ReinstallRecovery.writePortable(context, db, key, password, output);
            }
            Uri tree = Uri.parse(folder);
            Uri parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree));
            document = DocumentsContract.createDocument(context.getContentResolver(), parent,
                    "application/octet-stream", "Pinch-" + System.currentTimeMillis() + ".pinch");
            if (document == null) throw new IOException("Cannot create backup in the selected folder");
            try (InputStream input = new FileInputStream(encrypted);
                 OutputStream output = context.getContentResolver().openOutputStream(document, "w")) {
                if (output == null) throw new IOException("Cannot write backup");
                RecoveryCipher.copy(input, output);
            }
            context.getSharedPreferences("backup_prefs", 0).edit()
                    .putLong("last_backup_time", System.currentTimeMillis()).commit();
        } catch (Exception error) {
            if (document != null) try { DocumentsContract.deleteDocument(context.getContentResolver(), document); } catch (Exception ignored) {}
            throw error;
        } finally { Arrays.fill(password, '\0'); encrypted.delete(); }
    }
}
