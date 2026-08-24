# Fix Zero Value in Transfer Widget

The "Transfer" widget in the Total Summary fragment shows zero because it only sums transactions where the `type` field is explicitly set to `'TRANSFER'`. However, when users change a transaction's category to "Transfer" using the quick-change menu in the transaction list, only the `category` field is updated, while the `type` field remains `'EXPENSE'` or `'INCOME'`. This causes the transaction to be counted in the wrong category and ignored by the Transfer widget.

## User Review Required

> [!NOTE]
> I will ensure that any transaction categorized as "Transfer" is automatically treated as a `TRANSFER` type. This will fix the summary calculations and the Transfer widget.

## Proposed Changes

### UI / ViewModels

#### [MODIFY] [DashboardViewModel.java](file:///home/mockingj/AndroidStudioProjects/SpendTracker/app/src/main/java/com/example/spendtracker/ui/dashboard/DashboardViewModel.java)
- Update `updateTransactionCategory` to automatically change the transaction `type` to `"TRANSFER"` if the new category is "Transfer".
- This ensures that quick category changes in the list view correctly update the transaction type.

### Data Layer

#### [MODIFY] [TransactionDao.java](file:///home/mockingj/AndroidStudioProjects/SpendTracker/app/src/main/java/com/example/spendtracker/data/local/dao/TransactionDao.java)
- Update `getTransferTotal` to sum transactions where `type = 'TRANSFER'` OR `category = 'Transfer'`.
- Update `getTotalIncome` and `getTotalExpense` to explicitly exclude transactions where `category = 'Transfer'` to prevent double-counting.
- Update `getBankTotals` and other breakdown queries to exclude "Transfer" category transactions from Expense/Income summaries.

#### [MODIFY] [TransactionRepositoryImpl.java](file:///home/mockingj/AndroidStudioProjects/SpendTracker/app/src/main/java/com/example/spendtracker/data/repository/TransactionRepositoryImpl.java)
- Add a one-time background task to synchronize existing data: set `type = 'TRANSFER'` for all transactions where `category = 'Transfer'`.

## Verification Plan

### Automated Tests
- Build and run the app.
- Change an existing expense's category to "Transfer" in the "Daily" tab.
- Navigate to the "Total" tab and verify that the "Transfers" widget now shows the correct value and the "Expenses" total has decreased.

### Manual Verification
- Create a new transaction with type "Transfer" and verify it appears in the widget.
- Verify that bank totals at the bottom of the "Total" tab correctly exclude transfers.
