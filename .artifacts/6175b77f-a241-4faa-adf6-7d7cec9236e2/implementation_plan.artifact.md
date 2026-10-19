# Implementation Plan - Fixes and New Feature

This plan addresses several UI fixes, navigation improvements, and the implementation of a new receiver/sender recommendation feature in the SpendTracker app.

## User Review Required

> [!IMPORTANT]
> - **Navigation Slider**: The term "navigation slider" is interpreted as the month navigation buttons/header in the dashboard and charts toolbars.
> - **Swipe Navigation**: Horizontal swiping in `ChartsFragment` will be implemented using a `GestureDetector` on the main layout.
> - **Income Charts**: The Trends (LineChart) will be kept in the Income section as it shows volume over time, but specifically requested "expense-related" charts like Weekend vs Weekday and Source Type (Bank/Card/Cash) will be hidden.

## Proposed Changes

### [Navigation & Dashboard]

#### [MODIFY] [MainActivity.java](file:///home/mockingj/AndroidStudioProjects/SpendTracker/app/src/main/java/com/example/spendtracker/MainActivity.java)
- Optimize `setupNavigation` to ensure the bottom navigation state is always in sync with the current destination.

#### [MODIFY] [DashboardViewModel.java](file:///home/mockingj/AndroidStudioProjects/SpendTracker/app/src/main/java/com/example/spendtracker/ui/dashboard/DashboardViewModel.java)
- Add `selectedTab` LiveData to allow programmatic tab switching (e.g., from Calendar to Daily).
- Ensure `setFilter` and month navigation correctly trigger data updates for all sub-fragments.

#### [MODIFY] [DashboardFragment.java](file:///home/mockingj/AndroidStudioProjects/SpendTracker/app/src/main/java/com/example/spendtracker/ui/dashboard/DashboardFragment.java)
- Observe `selectedTab` and update `viewPager.currentItem`.
- Ensure month selection updates are consistent.

#### [MODIFY] [CalendarFragment.java](file:///home/mockingj/AndroidStudioProjects/SpendTracker/app/src/main/java/com/example/spendtracker/ui/dashboard/CalendarFragment.java)
- When a date is clicked, call `viewModel.selectTab(0)` to navigate to the Daily transaction list.
- Ensure the fragment re-renders correctly on resume.

---

### [Charts]

#### [MODIFY] [ChartsFragment.java](file:///home/mockingj/AndroidStudioProjects/SpendTracker/app/src/main/java/com/example/spendtracker/ui/charts/ChartsFragment.java)
- Implement `OnTouchListener` with `GestureDetector` to support horizontal swiping between Income and Expense tabs.
- Refine `updateSectionVisibility()` to hide expense-specific charts (Weekend vs Weekday, Bank/Cash distribution) when in Income mode.
- Implement a legend for `pieChartSource` in the Expense section, showing Source Name, Percentage, and Amount.
- Ensure all charts (Pie, Line, Bar) are invalidated and refreshed when granularity or month changes.

#### [MODIFY] [fragment_charts.xml](file:///home/mockingj/AndroidStudioProjects/SpendTracker/app/src/main/res/layout/fragment_charts.xml)
- Add a container/RecyclerView for the source distribution legend below `pie_chart_source`.
- Ensure headers for charts have IDs to manage their visibility.

---

### [Transaction Entry & Recommendations]

#### [MODIFY] [TransactionDao.java](file:///home/mockingj/AndroidStudioProjects/SpendTracker/app/src/main/java/com/example/spendtracker/data/local/dao/TransactionDao.java)
- Add `getUniqueContacts()` query to fetch distinct sender and receiver names from previous transactions.

#### [MODIFY] [TransactionRepository.java](file:///home/mockingj/AndroidStudioProjects/SpendTracker/app/src/main/java/com/example/spendtracker/domain/repository/TransactionRepository.java)
#### [MODIFY] [TransactionRepositoryImpl.java](file:///home/mockingj/AndroidStudioProjects/SpendTracker/app/src/main/java/com/example/spendtracker/data/repository/TransactionRepositoryImpl.java)
- Expose the new unique contacts query.

#### [MODIFY] [TransactionViewModel.java](file:///home/mockingj/AndroidStudioProjects/SpendTracker/app/src/main/java/com/example/spendtracker/ui/transaction/TransactionViewModel.java)
- Add `getUniqueContacts()` LiveData.

#### [MODIFY] [fragment_transaction_form.xml](file:///home/mockingj/AndroidStudioProjects/SpendTracker/app/src/main/res/layout/fragment_transaction_form.xml)
- Change `et_sender` and `et_receiver` to `AutoCompleteTextView`.

#### [MODIFY] [TransactionFormFragment.java](file:///home/mockingj/AndroidStudioProjects/SpendTracker/app/src/main/java/com/example/spendtracker/ui/transaction/TransactionFormFragment.java)
- Set up `ArrayAdapter` for sender and receiver ACTVs.
- Ensure recommendations update dynamically and handle edge cases (empty input, casing, etc.).

## Verification Plan

### Automated Tests
- Run existing unit tests to ensure no regressions.
- Add unit tests for `TransactionDao.getUniqueContacts()`.
- Add unit tests for recommendation filtering logic.

### Manual Verification
1. **Navigation**: Switch between tabs and verify the bottom bar updates immediately.
2. **Calendar**: Click a date and verify it switches to "Daily" with that date's transactions. Return to Calendar and verify it shows the correct month/data.
3. **Charts Swiping**: Swipe left/right in Charts and verify Income/Expense tabs switch.
4. **Income section**: Verify "Weekend vs Weekday" and "Bank/Cash" charts are hidden.
5. **Expense section**: Verify the new legend appears below the Source Pie Chart with correct data.
6. **Month Selection**: Change month in Dashboard and verify Calendar and Total fragments update.
7. **Granularity**: Change granularity in Charts and verify all charts refresh.
8. **Recommendations**: Type in Sender/Receiver fields in Transaction Form and verify suggestions appear and populate correctly.
