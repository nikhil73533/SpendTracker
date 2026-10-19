# Enhanced Modeling, UI Calendar, and Auto-Prediction

This plan implements advanced user behavior features for the ML model, a redesigned calendar UI matching the provided reference, and an auto-update mechanism for transaction categories.

## User Review Required

> [!IMPORTANT]
> The `prediction_database` will be upgraded to version 2. This will involve adding new tables for Merchant and Category statistics to improve accuracy.

## Proposed Changes

### [Modeling Component]

#### [NEW] [MerchantStatsEntity.java](file:///home/mockingj/AndroidStudioProjects/SpendTracker/prediction/src/main/java/com/example/prediction/data/local/entity/MerchantStatsEntity.java)
- Store `merchantName` (PK), `frequency`, `averageAmount`, `preferredCategory`, `lastTransactionDate`.

#### [MODIFY] [PredictionDatabase.java](file:///home/mockingj/AndroidStudioProjects/SpendTracker/prediction/src/main/java/com/example/prediction/data/local/PredictionDatabase.java)
- Add `MerchantStatsEntity` and `CategoryStatsEntity`.
- Bump version to `2`.

#### [MODIFY] [FeatureExtractor.java](file:///home/mockingj/AndroidStudioProjects/SpendTracker/prediction/src/main/java/com/example/prediction/util/FeatureExtractor.java)
- Implement normalization and bucketing:
    - **Amount Buckets**: (e.g., Small, Medium, Large, Huge).
    - **Hour Buckets**: (Morning, Afternoon, Evening, Night).
    - **Weekend**: Binary flag.
    - **Recency**: Days since last transaction with this merchant.

#### [MODIFY] [PredictionService.java](file:///home/mockingj/AndroidStudioProjects/SpendTracker/prediction/src/main/java/com/example/prediction/domain/service/PredictionService.java)
- Update `learn()` to maintain stats in the new tables.
- Update `predict()` to weight KNN results with merchant preferences and frequency.

### [UI Components]

#### [MODIFY] [CalendarGridAdapter.java](file:///home/mockingj/AndroidStudioProjects/SpendTracker/app/src/main/java/com/example/spendtracker/ui/dashboard/CalendarGridAdapter.java)
- Update the layout logic to match Image 1:
    - Show day number as the primary element.
    - Format first day of month as "1.M" (e.g., "1.7").
    - Display daily totals (Expenses in Red, Income in Blue) centered in the cell.

#### [MODIFY] [fragment_dashboard.xml](file:///home/mockingj/AndroidStudioProjects/SpendTracker/app/src/res/layout/fragment_dashboard.xml)
- Add a header row for "Sun, Mon, Tue..." above the calendar grid.
- Style the `gv_calendar` background and grid lines to match the reference.

#### [MODIFY] [item_calendar_day.xml](file:///home/mockingj/AndroidStudioProjects/SpendTracker/app/src/res/layout/item_calendar_day.xml)
- Redesign for a minimalist grid look with proper text sizing.

### [Auto-Update Logic]

#### [MODIFY] [DashboardViewModel.java](file:///home/mockingj/AndroidStudioProjects/SpendTracker/app/src/main/java/com/example/spendtracker/ui/dashboard/DashboardViewModel.java)
- Update `updateTransactionCategory()` to trigger a "Refinement Pass" after learning.
- The refinement pass will query all transactions with category "Other" or "Uncategorized" and run them through the updated model.
- If the new prediction has a confidence > 0.8, the transaction will be automatically updated in the primary database.

#### [MODIFY] [PredictionService.java](file:///home/mockingj/AndroidStudioProjects/SpendTracker/prediction/src/main/java/com/example/prediction/domain/service/PredictionService.java)
- Add a method to perform batch predictions for a list of transactions efficiently.

## Verification Plan

### Automated Tests
- Build verification.
- Unit test for `FeatureExtractor` bucketing logic.

### Manual Verification
- **Calendar**: Compare side-by-side with reference image. Verify date range navigation works.
- **Modeling**: Add a manual transaction, change its category, then add a similar manual transaction and verify it auto-populates the category.
- **Auto-Update**: Verify "Uncategorized" transactions get updated in the list after several manual corrections.
