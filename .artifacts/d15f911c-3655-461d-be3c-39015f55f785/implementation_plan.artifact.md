# Implementation Plan - SpendTracker Issues and Features

This plan covers bug fixes for the account transaction widget, updating the ICICI bank regex, fixing the calendar UI, and implementing several new features including a draggable FAB with long-press reset, auto-categorization improvements, and a comprehensive "Total" page with Excel export.

## User Review Required

> [!IMPORTANT]
> **Total Calculation:** The requirement specifies the Account Transaction widget should be `Total Spend - Total Credited`. However, the reference images show `Total = Income - Expenses` (which is `Credited - Spend`). I will follow the textual requirement (`Spend - Credited`) unless instructed otherwise.

> [!IMPORTANT]
> **Excel Export:** I will be adding the `Apache POI` library to the project to handle Excel file generation.

## Proposed Changes

### 1. Issues & Bug Fixes

#### [MODIFY] [TransactionRepositoryImpl.java](file:///home/mockingj/AndroidStudioProjects/SpendTracker/app/src/main/java/com/example/spendtracker/data/repository/TransactionRepositoryImpl.java)
- Update `updateSummary` to calculate `totalAccount` as `totalExpense - totalIncome`.

#### [MODIFY] [icici.json](file:///home/mockingj/AndroidStudioProjects/SpendTracker/app/src/main/assets/bank_configs/icici.json)
- Add a new regex pattern for ICICI bank credit messages.

#### [MODIFY] [fragment_dashboard.xml](file:///home/mockingj/AndroidStudioProjects/SpendTracker/app/src/main/res/layout/fragment_dashboard.xml)
- Update the summary labels to match the reference images ("Income", "Expenses", "Total").
- Reorganize the summary layout to be consistent with the images.
- Add "Sun-Sat" day labels for the calendar if not already perfectly aligned.

#### [MODIFY] [CalendarGridAdapter.java](file:///home/mockingj/AndroidStudioProjects/SpendTracker/app/src/main/java/com/example/spendtracker/ui/dashboard/CalendarGridAdapter.java)
- Add highlighting for the current day.
- Ensure proper alpha for days not in the current month.

---

### 2. Feature Updates

#### [MODIFY] [DashboardFragment.java](file:///home/mockingj/AndroidStudioProjects/SpendTracker/app/src/main/java/com/example/spendtracker/ui/dashboard/DashboardFragment.java)
- Implement `OnLongClickListener` for the FAB to trigger ML model reset.
- Implement `OnTouchListener` for the FAB to make it draggable.
- Add a `CircularProgressIndicator` overlay around the FAB for the reset animation.

#### [MODIFY] [PredictionService.java](file:///home/mockingj/AndroidStudioProjects/SpendTracker/prediction/src/main/java/com/example/prediction/domain/service/PredictionService.java)
- Add `resetModel()` method to clear all prototypes and merchant stats from the database.

#### [MODIFY] [DashboardViewModel.java](file:///home/mockingj/AndroidStudioProjects/SpendTracker/app/src/main/java/com/example/spendtracker/ui/dashboard/DashboardViewModel.java)
- Update `triggerRefinementPass()` to use a 10% (0.10) confidence threshold for auto-updating categories.
- Provide documentation/comments on ML training stages, features, and incremental learning.

---

### 3. New Features - Total Page & Excel Export

#### [MODIFY] [fragment_dashboard.xml](file:///home/mockingj/AndroidStudioProjects/SpendTracker/app/src/main/res/layout/fragment_dashboard.xml)
- Add a new layout section for the "Total" page content (Cards for Budget and Accounts).
- Add the "Export to Excel" button.

#### [MODIFY] [DashboardFragment.java](file:///home/mockingj/AndroidStudioProjects/SpendTracker/app/src/main/java/com/example/spendtracker/ui/dashboard/DashboardFragment.java)
- Update `setupTabLayout` to handle the "Total" tab by showing the new layout section.
- Implement the "Export to Excel" logic.

#### [MODIFY] [DashboardViewModel.java](file:///home/mockingj/AndroidStudioProjects/SpendTracker/app/src/main/java/com/example/spendtracker/ui/dashboard/DashboardViewModel.java)
- Implement methods to calculate:
    - `comparedExpensesPercentage`: (This month / Last month) * 100.
    - `expensesByAccount`: Sum of expenses where `sourceType` is "Account".
    - `expensesByCard`: Sum of expenses where `sourceType` is "Credit Card".
    - `totalTransfers`: Sum of transactions in the "Transfer" category.

#### [MODIFY] [build.gradle.kts](file:///home/mockingj/AndroidStudioProjects/SpendTracker/app/build.gradle.kts) & [libs.versions.toml](file:///home/mockingj/AndroidStudioProjects/SpendTracker/gradle/libs.versions.toml)
- Add `org.apache.poi:poi-ooxml` dependency.

---

## Verification Plan

### Automated Tests
- I will verify the build after adding the new dependency.
- I will check the logs for ML prediction and refinement pass.

### Manual Verification
- Deploy the app and verify the "Account Transaction" calculation.
- Test the new ICICI credit message parsing by simulating or adding a dummy transaction.
- Navigate months in the Calendar tab and check for highlighting.
- Long-press the FAB to trigger reset and observe the animation and toast.
- Drag the FAB around the screen.
- Go to the "Total" tab and verify the values for compared expenses, account/card expenses, and transfers.
- Click "Export to Excel" and verify the file creation.
