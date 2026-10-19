# Analysis of Backup/Restore Issues

## Reported Issues
1.  **Missing Transaction Data:** User reports data is not there.
2.  **Crash on Restore:** App crashes when "restore backup" is clicked.

## Findings

### 1. Crash on Restore
The `BackupViewModel.restoreNow()` method attempts to unzip the backup directly into the `databases` folder while the app is running and the Room database connection is likely open.
```java
    public boolean restoreNow() {
        // ...
        File dbDir = context.getDatabasePath("spend_tracker_db").getParentFile();
        StorageHelper.unzipFile(backupZip, dbDir); // CRASH POINT
        return true;
    }
```
Overwriting active database files (`-wal`, `-shm`, and the main `.db`) causes immediate instability or crashes in SQLite/Room.

### 2. Missing Data
There are several potential reasons for missing data:
-   **Privacy Mode:** Default is `TRUE`, which masks amounts with `***`. User might interpret this as missing data.
-   **Failed Migration/Recovery:** `DatabaseModule` uses a custom `ultimateRecoveryFromCache` and `migrateIfNecessary`. If these fail or if they encrypted an empty DB, data would be lost.
-   **Soft Delete:** Data might be marked as `DELETED` and thus not visible in the main dashboard.
-   **August Data Comment:** A comment in `DatabaseModule` suggests there was a known issue with "missing August data", and `ultimateRecoveryFromCache` was added to fix it.

## Proposed Fixes

### Fix for Crash on Restore
-   Modify `restoreNow()` to copy the `backup.zip` to the `cache` directory and then trigger a process restart.
-   Leverage the existing `ultimateRecoveryFromCache` in `DatabaseModule` to handle the actual file swap and encryption during the next app startup, before Room initializes.

### Fix for Missing Data
-   Ensure `ultimateRecoveryFromCache` properly handles the WAL checkpointing to avoid data loss from the WAL file.
-   Check if `backup.zip` exists in the external files directory and, if so, ensure it's prioritized during recovery if the current DB is empty.
-   Check "Privacy Mode" default and ensure users understand how to toggle it.
