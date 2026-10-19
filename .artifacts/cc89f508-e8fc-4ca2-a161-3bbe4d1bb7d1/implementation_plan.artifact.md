# Implementation Plan - Spend Tracker Major Updates

This plan outlines the steps to improve chart rendering, date navigation, category management, UI state management, Excel export, privacy/encryption, and database backup/restore in the Spend Tracker application.

## User Review Required

> [!IMPORTANT]
> - **PII Protection**: Privacy mode will now mask not just amounts, but also Merchant names, Senders, Receivers, UPI IDs, and transaction descriptions across all screens (Calendar, Dashboard, etc.).
> - **Backup/Restore**: Existing "Recover Missing August Data" button will be replaced with a generic "Backup Database" and "Restore Database" flow.
> - **Database Migration**: Schema changes for better grouping (if any) or encryption will be handled via Room migrations.

## Proposed Changes

### Charts & Granularity

#### [MODIFY] [ChartsFragment.java](file:///home/mockingj/AndroidStudioProjects/SpendTracker/app/src/main/java/com/example/spendtracker/ui/charts/ChartsFragment.java)
- Adjust `setupPieChart` for better label distribution (offsets, line lengths).
- Ensure pie chart height is 200dp (verify in XML).
- Hide 0% categories from pie chart (already partially implemented, but verify).
- Update `updateHeaderLabel` to support correct formatting:
    - Weekly: "03 Aug - 09 Aug"
    - Monthly: "August 2026"
    - Yearly: "2026"

#### [MODIFY] [ChartsViewModel.java](file:///home/mockingj/AndroidStudioProjects/SpendTracker/app/src/main/java/com/example/spendtracker/ui/charts/ChartsViewModel.java)
- Update `getChartData` to observe `transactionType` so switching between Income/Expense updates the pie chart immediately.
- Refine `getDailyTrends` logic to ensure all granularities (Daily, Weekly, Monthly, Annually) correctly fetch the previous 2 periods + current period.

### Dashboard & Date Navigation

#### [MODIFY] [DashboardFragment.java](file:///home/mockingj/AndroidStudioProjects/SpendTracker/app/src/main/java/com/example/spendtracker/ui/dashboard/DashboardFragment.java)
- Ensure month navigation (prev/next) updates all dependent UI (totals, charts, lists).
- Fix stale navigation state when returning from breakdowns.

#### [MODIFY] [DashboardViewModel.java](file:///home/mockingj/AndroidStudioProjects/SpendTracker/app/src/main/java/com/example/spendtracker/ui/dashboard/DashboardViewModel.java)
- Ensure `dateRange` updates trigger re-queries of all LiveData.
- Fix weekly date range formatting in `calculateWeeklySummaries`.

### Category Management

#### [MODIFY] [TransactionFormFragment.java](file:///home/mockingj/AndroidStudioProjects/SpendTracker/app/src/main/java/com/example/spendtracker/ui/transaction/TransactionFormFragment.java)
- Improve "Create/Edit/Delete Category" flow directly from the dropdown.
- Ensure category changes immediately propagate to the dashboard and charts.

### Accounts & Chat History

#### [MODIFY] [AccountsFragment.java](file:///home/mockingj/AndroidStudioProjects/SpendTracker/app/src/main/java/com/example/spendtracker/ui/accounts/AccountsFragment.java)
- Redesign list to show unique senders/receivers grouped by UPI ID (primary) or Name.
- Sort by most recent transaction date.

#### [MODIFY] [AccountHistoryFragment.java](file:///home/mockingj/AndroidStudioProjects/SpendTracker/app/src/main/java/com/example/spendtracker/ui/accounts/AccountHistoryFragment.java)
- Implement full chat-style history with month/date filtering.
- Ensure privacy mode applies to this screen.

### Excel Export & Privacy

#### [MODIFY] [DashboardFragment.java](file:///home/mockingj/AndroidStudioProjects/SpendTracker/app/src/main/java/com/example/spendtracker/ui/dashboard/DashboardFragment.java)
- Update `exportToExcel` to check `privacyMode` and mask/encrypt PII in the generated file.
- Add basic validation for exported data.

#### [MODIFY] [SecurityRepositoryImpl.java](file:///home/mockingj/AndroidStudioProjects/SpendTracker/app/src/main/java/com/example/spendtracker/data/repository/SecurityRepositoryImpl.java)
- Add `maskPII(String value)` method.
- Update `maskAmount` if needed.

### Backup & Restore

#### [MODIFY] [fragment_dashboard.xml](file:///home/mockingj/AndroidStudioProjects/SpendTracker/app/src/main/res/layout/fragment_dashboard.xml)
- Rename "Recover Missing August Data" to generic Backup/Restore UI elements.

#### [MODIFY] [DatabaseEncryptionHelper.java](file:///home/mockingj/AndroidStudioProjects/SpendTracker/app/src/main/java/com/example/spendtracker/util/DatabaseEncryptionHelper.java)
- Generalize backup/restore logic to handle any database state safely.

### Verification Plan

### Automated Tests
- **Unit Tests**:
    - Date calculations for weekly/monthly ranges.
    - PII masking logic.
    - Chart data aggregation.
    - Backup integrity validation.
- **Integration Tests**:
    - Add Transaction -> Dashboard update.
    - Month Navigation -> Chart/List update.
    - Backup -> Modify -> Restore -> UI Refresh.

### Manual Verification
- Verify pie chart labels on different screen sizes.
- Verify Excel file content for privacy leakage.
- Verify chat-style history navigation.
- Verify migration from version 4 to 5 (if needed).
