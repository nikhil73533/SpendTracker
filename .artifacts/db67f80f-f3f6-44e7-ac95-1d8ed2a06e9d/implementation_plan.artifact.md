# SpendTracker Implementation Plan - Feature Updates & Bug Fixes

This plan outlines the steps to improve chart rendering, date navigation, category management, Excel export, and account management in the SpendTracker application.

## User Review Required

> [!IMPORTANT]
> **Category Management Migration:** Category management will be moved from the 'More' section directly into the 'Add Transaction' flow. Users will be able to Create, Edit, and Delete categories from the category selection dropdown.
> **Account Section Redesign:** The Account section will now focus on unique senders/receivers (based on UPI ID) and feature a chat-style transaction history for each.

## Proposed Changes

### 1. Charts & Trends
Summary: Fix pie chart rendering, implement historical trends, and improve granularity handling.

#### [MODIFY] [ChartsFragment.java](file:///home/mockingj/AndroidStudioProjects/SpendTracker/app/src/main/java/com/example/spendtracker/ui/charts/ChartsFragment.java)
- Increase Pie Chart height to 200dp in `fragment_charts.xml`.
- Update `setupPieChart` to:
    - Filter out categories with 0%.
    - Tune label positioning (`OUTSIDE_SLICE`) and offsets to prevent overlap.
    - Set larger `ExtraOffsets` for the chart.
- Update `setupLineChart` to use date ranges for Weekly granularity labels (e.g., "03 Aug – 09 Aug").
- Ensure `tabChartType` change immediately refreshes both Pie and Trend charts.

#### [MODIFY] [ChartsViewModel.java](file:///home/mockingj/AndroidStudioProjects/SpendTracker/app/src/main/java/com/example/spendtracker/ui/charts/ChartsViewModel.java)
- Update `getDailyTrends` to return exactly 3 periods (Previous 2 + Current) based on selected granularity.
- Update `moveNext/movePrev` to respect granularity (e.g., jump 1 week if Weekly is selected).
- Implement `refreshData()` to force recalculation when any filter changes.

---

### 2. Dashboard & Navigation
Summary: Fix month navigation and header dynamic labels.

#### [MODIFY] [DashboardViewModel.java](file:///home/mockingj/AndroidStudioProjects/SpendTracker/app/src/main/java/com/example/spendtracker/ui/dashboard/DashboardViewModel.java)
- Fix `moveNext/movePrev` to always change the month/year appropriately and update the entire view, even when in Daily view.
- Update `DateRange` logic to properly support different headers (e.g., "2026" for Yearly, "10 Aug – 16 Aug" for Weekly).

#### [MODIFY] [DashboardFragment.java](file:///home/mockingj/AndroidStudioProjects/SpendTracker/app/src/main/java/com/example/spendtracker/ui/dashboard/DashboardFragment.java)
- Ensure header `TextView` updates correctly based on the selected filter/granularity.
- Clear stale category breakdown state when navigating back to the dashboard.

---

### 3. Category Management
Summary: Move category management to the transaction form and support full CRUD.

#### [MODIFY] [TransactionFormFragment.java](file:///home/mockingj/AndroidStudioProjects/SpendTracker/app/src/main/java/com/example/spendtracker/ui/transaction/TransactionFormFragment.java)
- Enhance the category dropdown to include "Edit" and "Delete" icons/actions for each category (possibly using a custom adapter or a dialog).
- Ensure category changes (Create/Edit/Delete) are immediately reflected in the current form and other UI components.

#### [MODIFY] [MoreFragment.java](file:///home/mockingj/AndroidStudioProjects/SpendTracker/app/src/main/java/com/example/spendtracker/ui/more/MoreFragment.java)
- Remove "Manage Categories" button.

---

### 4. Excel Export
Summary: Fix the broken Excel export pipeline.

#### [MODIFY] [DashboardFragment.java](file:///home/mockingj/AndroidStudioProjects/SpendTracker/app/src/main/java/com/example/spendtracker/ui/dashboard/DashboardFragment.java)
- Fix the `exportToExcel` method:
    - Ensure all transaction fields (Date, Category, Amount, Type, Description, Sender/Receiver, UPI ID) are included.
    - Verify file storage permissions for different Android versions (using `FileProvider`).
    - Test the generated file structure.

---

### 5. Accounts & Chat-style History
Summary: Redesign account section and implement transaction history.

#### [MODIFY] [TransactionDao.java](file:///home/mockingj/AndroidStudioProjects/SpendTracker/app/src/main/java/com/example/spendtracker/data/local/dao/TransactionDao.java)
- Add query to get unique senders/receivers grouped by `upiId` (falling back to name), sorted by latest transaction.
- Add query to get transaction history for a specific account (sender/receiver/upiId).

#### [MODIFY] [AccountsFragment.java](file:///home/mockingj/AndroidStudioProjects/SpendTracker/app/src/main/java/com/example/spendtracker/ui/accounts/AccountsFragment.java)
- Implement a list of unique senders/receivers.
- Sort by most recent transaction.
- Add navigation to the new chat-style history screen.

#### [NEW] [AccountHistoryFragment.java](file:///home/mockingj/AndroidStudioProjects/SpendTracker/app/src/main/java/com/example/spendtracker/ui/accounts/AccountHistoryFragment.java)
- Implement chat-style UI for transaction history.
- Include header with account summary (total debit/credit).
- Add date/month filter consistent with the rest of the app.

---

## Verification Plan

### Automated Tests
- Run `TransactionDaoTest` (if exists, or create one) to verify account queries.
- Run `ChartsViewModelTest` to verify trend calculations for different granularities.

### Manual Verification
- **Charts**: Verify Pie chart labels don't overlap, 0% labels are hidden, and chart type switching is instant.
- **Navigation**: Test left/right arrows for Daily, Weekly (if implemented in dashboard), Monthly, and Yearly views.
- **Categories**: Create, edit, and delete a category from the transaction form and verify it updates in the charts and lists.
- **Excel**: Export transactions and open the file on the device.
- **Accounts**: Verify unique entries in the account list and "chat-style" rendering in the detail view.
