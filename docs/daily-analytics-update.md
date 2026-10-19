# Daily and Advanced Analytics update

## User-visible changes

- Daily → Your activity now contains a 7-day expenses option using all transactions across month boundaries. The original selected-period activity remains available. Both support Bars / Line controls and privacy masking. Chart choices survive view recreation.
- The seven-day card was removed from Charts.
- Daily’s add-transaction button now uses the same bottom/end inset as the transaction-group add button.
- Prediction outcome bars open a separate screen with Retained / Corrected / Unreviewed tabs, counts, empty states, original and reviewed categories, and transaction editing. The filters use the same classification as the chart and update after edits/deletes.
- Advanced Analytics puts financial totals first, uses larger typography and consistent insight cards, labels chart units more clearly, and offers a visible review-predictions action. Date filters use local calendar boundaries and retain the current selection when reopened.

## Data loading

Three stored summaries and consolidated dashboard/overview reads reduce repeated aggregation. See [analytics-materialization.md](analytics-materialization.md) for migration, query boundaries, performance tradeoffs and verification.

## Payments

See [upi-autopay-subscriptions.md](upi-autopay-subscriptions.md) for the recurring UPI design, four periods, open-source SDK candidates and production prerequisites. No live checkout or mandate was created.

## Validation limit

The app and instrumentation package compile. JVM regressions and the independent SQLite verification are run locally. On-device visual/interaction and Room migration execution remain to be performed: the connected phone reports debugging as unauthorized and no configured emulator is available.
