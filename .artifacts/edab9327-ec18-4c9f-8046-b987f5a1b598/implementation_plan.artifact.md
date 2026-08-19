# Implementation Plan - Chart UI Fixes & Bank Detail Navigation

This plan addresses Chart Fragment layout issues, improves privacy mode unmasking responsiveness, and implements navigation for bank-level transaction details.

## Proposed Changes

### [Component] Domain & Data Layer
Extend the repository to support bank-specific transaction filtering.

#### [MODIFY] [TransactionDao.java](file:///home/mockingj/AndroidStudioProjects/SpendTracker/app/src/main/java/com/example/spendtracker/data/local/dao/TransactionDao.java)
- No new DAO method needed if we use the existing `getTransactionsInRange` and filter in the ViewModel/Fragment (matching the `CategoryDetailFragment` pattern).

#### [MODIFY] [SecurityRepositoryImpl.java](file:///home/mockingj/AndroidStudioProjects/SpendTracker/app/src/main/java/com/example/spendtracker/data/repository/SecurityRepositoryImpl.java)
- Change `privacyMode.postValue(enabled)` to `privacyMode.setValue(enabled)` in `setPrivacyModeEnabled` for immediate main-thread notification.

### [Component] Charts Fragment
Fix layout, label mapping, and section visibility.

#### [MODIFY] [fragment_charts.xml](file:///home/mockingj/AndroidStudioProjects/SpendTracker/app/src/main/res/layout/fragment_charts.xml)
- Move `rv_stats` `RecyclerView` to be directly after the `ConstraintLayout` containing `pie_chart_main`.

#### [MODIFY] [ChartsFragment.java](file:///home/mockingj/AndroidStudioProjects/SpendTracker/app/src/main/java/com/example/spendtracker/ui/charts/ChartsFragment.java)
- **Income Section**: In `setupTabLayout`, toggle visibility of `line_chart`, `bar_chart_weekend`, `bar_chart_banks`, and `pie_chart_source` (hide them when Income is selected).
- **Source Labels**: In `observeViewModel`, map `sourceType` totals to "Credit Card", "Account", and "Other" before displaying in `pie_chart_source`.

### [Component] Bank Detail Navigation
Implement navigation from the Total tab to a detailed bank transaction list.

#### [NEW] [BankDetailFragment.java](file:///home/mockingj/AndroidStudioProjects/SpendTracker/app/src/main/java/com/example/spendtracker/ui/charts/BankDetailFragment.java)
- A clone of `CategoryDetailFragment` but filtering transactions by `bankName` instead of `category`.

#### [MODIFY] [nav_graph.xml](file:///home/mockingj/AndroidStudioProjects/SpendTracker/app/src/main/res/navigation/nav_graph.xml)
- Add `bankDetailFragment` destination.

#### [MODIFY] [TotalSummaryFragment.java](file:///home/mockingj/AndroidStudioProjects/SpendTracker/app/src/main/java/com/example/spendtracker/ui/dashboard/TotalSummaryFragment.java)
- Add a click listener to `BankTotalAdapter` to navigate to `BankDetailFragment` with the selected bank name.

### [Component] Privacy Mode Responsiveness
#### [MODIFY] [DashboardFragment.java](file:///home/mockingj/AndroidStudioProjects/SpendTracker/app/src/main/java/com/example/spendtracker/ui/dashboard/DashboardFragment.java)
- Ensure integer formatting (`%.0f`) is used in `formatAmountWithState`. (Already present, but verifying consistency).

---

## Verification Plan

### Automated Tests
- None.

### Manual Verification
1.  **Charts**:
    - Verify `rv_stats` is directly below the main Pie Chart.
    - Switch to **Income** section and verify only one chart is visible.
    - Verify **Source Pie Chart** uses labels: "Credit Card", "Account", and "Other".
2.  **Navigation Widget**:
    - Authenticate via long-press and verify amounts reveal **immediately**.
3.  **Bank Detail**:
    - Navigate to **Total** tab.
    - Click on a bank (e.g., **ICICI Bank**) and verify it opens a filtered transaction list with a trend chart.
