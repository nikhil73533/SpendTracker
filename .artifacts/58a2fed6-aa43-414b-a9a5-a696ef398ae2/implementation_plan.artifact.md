# Implementation Plan - Responsive UI and Account Identification Fixes

This plan addresses responsive Pie Chart sizing, biometric re-rendering for summary widgets, and a change in account uniqueness identification.

## User Review Required

> [!IMPORTANT]
> **Account Uniqueness**: Changing the grouping logic from UPI ID to Name means that multiple accounts with different UPI IDs but the same name will now be merged into a single "Account" in the UI.

## Proposed Changes

### 1. Responsive Pie Chart
#### [MODIFY] [fragment_charts.xml](file:///home/mockingj/AndroidStudioProjects/SpendTracker/app/src/main/res/layout/fragment_charts.xml)
- Change the static `200dp` height/width to `match_parent` width and use `ConstraintLayout` with a ratio or a flexible height to allow the Pie Chart to use available space.

#### [MODIFY] [ChartsFragment.java](file:///home/mockingj/AndroidStudioProjects/SpendTracker/app/src/main/java/com/example/spendtracker/ui/charts/ChartsFragment.java)
- Remove the hardcoded `200dp` size enforcement logic.

### 2. Biometric Re-rendering Fix
#### [MODIFY] [DashboardFragment.java](file:///home/mockingj/AndroidStudioProjects/SpendTracker/app/src/main/java/com/example/spendtracker/ui/dashboard/DashboardFragment.java)
- Ensure the `isPrivacyModeEnabled` observer correctly refreshes the Income, Expense, and Total widgets by explicitly re-applying the summary data.
- Investigate if `viewModel.getSummary()` needs to be refreshed or if a manual re-binding of the current value is sufficient.

### 3. Unique Account Identification by Name
#### [MODIFY] [TransactionDao.java](file:///home/mockingj/AndroidStudioProjects/SpendTracker/app/src/main/java/com/example/spendtracker/data/local/dao/TransactionDao.java)
- Update `getUniqueAccounts` query to `GROUP BY` the calculated `name` field instead of UPI ID.
- Update `getAccountHistory` query to filter by `receiverName` or `sender` directly matching the account name.

#### [MODIFY] [AccountsFragment.java](file:///home/mockingj/AndroidStudioProjects/SpendTracker/app/src/main/java/com/example/spendtracker/ui/accounts/AccountsFragment.java)
- Ensure the `listener.accept(account)` passes the `account.name` as the identifier for navigation.

#### [MODIFY] [AccountHistoryFragment.java](file:///home/mockingj/AndroidStudioProjects/SpendTracker/app/src/main/java/com/example/spendtracker/ui/accounts/AccountHistoryFragment.java)
- Use `accountName` (passed as `accountId`) for fetching history.

## Verification Plan

### Automated Tests
- None planned for this UI/SQL iteration, but will verify SQL queries via manual testing.

### Manual Verification
- **Pie Chart**: Rotate device and check if chart resizes. Test on different screen sizes (if available).
- **Biometric Auth**: Enable privacy mode (***), authenticate via long-press, and verify Income/Expense/Total widgets immediately show actual numbers.
- **Account Uniqueness**: Add two transactions with different UPI IDs but the same "Receiver Name" and verify they appear as one account in the Accounts list.
