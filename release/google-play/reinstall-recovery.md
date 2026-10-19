# Reinstall recovery — com.pinchtrack.app

All consumer build types now use the same application ID, `com.pinchtrack.app`. The Java source namespace remains internal implementation detail. Android's instrumentation runner has its own required test package and is not a second consumer app.

## Automatic Android recovery

Enable Android backup and a device screen lock in the phone's settings. When the operating system completes a protected backup, it saves a consistent copy of the finance database, learning model and the key required to open the finance database. A supported device-to-device transfer can also carry this snapshot.

Android may restore the latest completed snapshot during reinstall using the same Google account and compatible app signature. Recovery installs the databases before normal app startup and imports the saved database key into the new installation's protected preferences. Device-bound preferences, payment credentials, folder permissions, the phone-backup password and the SMS inbox are not included.

Android controls when backups run. They are not continuous and may never run if the device conditions are not met. Cloud backups have a 25 MB limit. This implementation requires client-side encryption for cloud backup, available on supported Android 9+ devices; Android 8 users should use phone backup files. No backup can recover changes that were never saved before uninstall.

## Phone backup files

1. Open More → Backup → Choose backup folder.
2. Create or select a folder such as Documents/Pinch using Android's folder picker. Do not select an app-owned Android/data folder.
3. Set and confirm a backup password of at least 10 characters. Keep the password in a safe place; it cannot be reset.
4. The app immediately saves a `.pinch` file in the chosen folder. Enable Daily phone backups if wanted, or tap Backup Now after important changes.
5. After reinstall, open More → Backup → Restore Backup, select a saved `.pinch` file, enter its password, then close and reopen the app as prompted.

The file remains in the selected folder after uninstall. Android removes the app's folder access grant on uninstall, so the user must select the saved file again. A new installation cannot silently browse private documents or obtain the old password. If a cloud document provider is selected instead of local storage, availability depends on that provider.

Phone files encrypt the database, model and recovery key together using AES-256-GCM and PBKDF2-HMAC-SHA256 with 210,000 iterations, a fresh random salt and nonce. A password copy is stored in EncryptedSharedPreferences for this installation's scheduled saves and is excluded from Android backup. Each save creates a new file, preserving earlier copies. Delete obsolete copies through the Files app when no longer needed.

Restore authenticates the complete file, rejects unsupported entries and oversized archives, and checks both databases before staging. Existing databases are preserved during the replacement until the restored key has been saved successfully. An interrupted replacement resumes on the next app start. Restoring intentionally replaces current records; create a current backup first if needed. Restore is limited to supported database/model versions and 128 MB of decompressed data.

## Existing installation under the old ID

Changing from `com.example.spendtracker` to `com.pinchtrack.app` creates a different Android app. The new app cannot automatically read the old app's private database or Android backup dataset. Keep the old installation until its records have been transferred using a supported export/recovery process. Legacy encrypted ZIP files without the original encryption key cannot be recovered merely by renaming them.

This new ID cannot update a Play listing published under the previous ID. Configure the intended Play listing and signing key for `com.pinchtrack.app`.

## Current verification

- 821 unit tests passed, including five new authenticated-backup tests.
- The Play preview Java sources compiled successfully.
- Instrumentation tests cover database/model/key recovery into isolated test storage, repeated startup before key import, wrong passwords and invalid archive entries. Device execution is pending a connected phone.
- Real Android backup transport, actual uninstall/reinstall, and 16 KB runtime verification remain pending. Do not claim guaranteed recovery on every device.

Sources: [Android Auto Backup](https://developer.android.com/identity/data/autobackup), [Android document storage](https://developer.android.com/training/data-storage/shared/documents-files).
