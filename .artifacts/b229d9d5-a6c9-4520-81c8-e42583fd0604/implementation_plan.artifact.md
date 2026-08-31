# Fix Backup Restore Crash and Missing Data

This plan addresses the crash during backup restoration and investigates/resolves the missing transaction data issue.

## Proposed Changes

### 1. Fix Restore Crash

#### [MODIFY] [BackupViewModel.java](file:///home/mockingj/AndroidStudioProjects/SpendTracker/app/src/main/java/com/example/spendtracker/ui/settings/BackupViewModel.java)
-   Update `restoreNow()` to copy `backup.zip` from external storage to the internal cache directory.
-   Inform the user and trigger an app restart (or kill process) to allow `DatabaseModule` to perform the recovery on next launch.

#### [MODIFY] [StorageHelper.java](file:///home/mockingj/AndroidStudioProjects/SpendTracker/app/src/main/java/com/example/spendtracker/util/StorageHelper.java)
-   Add a `copyFile` utility method.

### 2. Improve Recovery and Data Integrity

#### [MODIFY] [DatabaseEncryptionHelper.java](file:///home/mockingj/AndroidStudioProjects/SpendTracker/app/src/main/java/com/example/spendtracker/util/DatabaseEncryptionHelper.java)
-   Enhance `ultimateRecoveryFromCache` to be more robust, especially with WAL checkpointing for both unencrypted and encrypted databases.
-   Ensure it doesn't delete the backup until it's certain it was applied.

#### [MODIFY] [DatabaseModule.java](file:///home/mockingj/AndroidStudioProjects/SpendTracker/app/src/main/java/com/example/spendtracker/di/DatabaseModule.java)
-   Add logic to check if the database is unexpectedly empty and, if so, look for a `backup.zip` in external storage to automatically recover.

## Verification Plan

### Automated Tests
-   Compile the project: `./gradlew :app:assembleDebug`

### Manual Verification
1.  **Restore Test:** Click "Restore Backup" in settings. Verify the app restarts (or closes) without a "Process has stopped" dialog.
2.  **Data Check:** After restart, verify that transactions are visible in the Dashboard.
3.  **Privacy Mode:** Check if "Privacy Mode" masking is causing confusion.
