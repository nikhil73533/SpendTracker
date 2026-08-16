# Implementation Plan - Data Recovery & Backup/Restore

The user reported missing August transactions after the encryption update. We will prioritize recovering this data from the automatic backup and then implement a full Backup/Restore feature for safety.

## User Review Required

> [!IMPORTANT]
> **Data Recovery**: We will attempt to restore your August transactions from the `spend_tracker_db.bak` file created during the last update.
>
> **Backup/Restore**: You will be able to export a compressed `.zip` file of your entire database and restore it at any time.

## Proposed Changes

### 1. Data Recovery & Migration Fix
#### [MODIFY] [DatabaseEncryptionHelper.java](file:///home/mockingj/AndroidStudioProjects/SpendTracker/app/src/main/java/com/example/spendtracker/util/DatabaseEncryptionHelper.java)
- Improve migration: Checkpoint the WAL before exporting to ensure all data (including August records) is copied.
- Add `restoreFromInternalBackup()` method.

### 2. UI Updates
#### [MODIFY] [fragment_dashboard.xml](file:///home/mockingj/AndroidStudioProjects/SpendTracker/app/src/main/res/layout/fragment_dashboard.xml)
- Add "Backup Database" and "Restore Database" buttons in the "Total" tab.

### 3. Backup/Restore Implementation
#### [MODIFY] [DashboardFragment.java](file:///home/mockingj/AndroidStudioProjects/SpendTracker/app/src/main/java/com/example/spendtracker/ui/dashboard/DashboardFragment.java)
- Implement logic to export database files as a ZIP.
- Implement logic to import a ZIP and replace the current database.
- Add an "Emergency Restore" check on startup to alert the user if a backup is found while the main DB is empty.

## Verification Plan

### Manual Verification
- **Recovery**: Verify transactions from August are restored.
- **Backup/Restore**: Perform a full cycle of backup to file and restoration.
