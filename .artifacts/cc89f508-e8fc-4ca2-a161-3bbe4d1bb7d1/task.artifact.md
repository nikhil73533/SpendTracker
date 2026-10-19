# Task List - Spend Tracker Production Updates

## Phase 1: Charts & Granularity Fixes
- [x] Fix Pie Chart size (200dp) and label overlapping in `fragment_charts.xml` and `ChartsFragment.java`.
- [x] Hide 0% categories from Pie Chart.
- [x] Fix Income/Expense chart switching (instant update, no stale data).
- [x] Update header date labels for all granularities (Weekly date range, Yearly year only).
- [x] Implement "Previous 2 + Current" trend logic for all granularities.

## Phase 2: Dashboard & Navigation
- [x] Fix Main/Daily Screen month navigation (arrows move month, data refreshes).
- [x] Centralize UI State to prevent stale state bugs across all screens.
- [x] Fix Chart -> Category Breakdown navigation (clear state on return).

## Phase 3: Category Management
- [x] Remove "Manage Categories" from More section.
- [x] Enhance "Add Transaction" category dropdown (Create/Edit/Delete).
- [x] Ensure category changes propagate immediately.

## Phase 4: Accounts & Chat History
- [x] Redesign Account section (unique senders/receivers by UPI ID/Name, sort by recent).
- [x] Implement Chat-style transaction history for individual accounts.
- [x] Add month/date filtering to Account history.

## Phase 5: Privacy & Encryption
- [x] Mask PII (UPI IDs, Merchant names, Senders, Descriptions) in UI when Privacy Mode is on.
- [x] Implement end-to-end PII protection in local storage (Room encryption).
- [x] Perform PII leakage audit (logs, temporary files).
- [x] Fix Excel Export (Privacy-aware, valid file format, correct data).

## Phase 6: Backup & Restore
- [x] Remove "Recover Missing August Data" button and hardcoded logic.
- [x] Implement generic Database Backup (ZIP, encrypted).
- [x] Implement generic Database Restore (integrity validation, UI refresh).

## Phase 7: Verification & Final Polish
- [x] Perform Regression testing for all existing features.
- [x] Run Integration tests for key flows (Add -> Dashboard, Backup -> Restore).
- [x] Verify Excel export content and file integrity.
- [x] Security audit for encryption/PII.
