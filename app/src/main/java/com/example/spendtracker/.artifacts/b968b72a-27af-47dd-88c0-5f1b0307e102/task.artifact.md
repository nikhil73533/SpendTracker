# Task List - Data Recovery & Backup/Restore

## 1. Data Recovery (Top Priority)
- [x] Implement WAL checkpointing in `DatabaseEncryptionHelper` to prevent data loss.
- [x] Implement `restoreFromBackup` logic in `DatabaseEncryptionHelper` (supports Samsung `.corrupt` backups).
- [x] Trigger automatic restore if August data is missing and backup exists (via manual button in Total tab).

## 2. Backup & Restore UI
- [x] Add Backup/Restore buttons to `fragment_dashboard.xml`.

## 3. Backup Mechanism
- [x] Implement ZIP compression of database files in `StorageHelper`.
- [x] Implement `Intent.ACTION_CREATE_DOCUMENT` flow for saving backups.

## 4. Restore Mechanism
- [x] Implement `Intent.ACTION_OPEN_DOCUMENT` flow for picking backup files.
- [x] Implement ZIP extraction and safe file replacement.
- [x] Add restart trigger after restoration.

## 5. Verification
- [ ] Verify August transactions are visible after clicking "Recover Missing August Data".
- [x] Verify ZIP backup contains valid encrypted DB.
- [x] Verify restore works correctly.
