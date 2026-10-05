# Prediction Insights and daily charts

Prediction Insights lives in **Advanced Analytics**, with count/accuracy widgets and a retained/corrected/unreviewed outcome chart. It is explicitly all time, independent of the financial date filter. Prediction Insights covers **all active transactions with recorded prediction history**, across all dates. The count is one transaction per original, nonempty model category. Transfers assigned by the deterministic transfer rule, abstentions (`PENDING`/`Uncategorized`), and historical rows without provenance are not model predictions.

Accuracy = matching original prediction / evaluated predictions × 100. An evaluated prediction has an explicit, known final category saved by the user. Confidence, automatic categorization, and approving a statement import do not confirm accuracy. With zero evaluated rows, show “Not enough data yet”. Always show the reviewed sample size.

Retained = the same matching fraction. Corrected = differing final categories / evaluated predictions × 100. These percentages describe the reviewed subset, not all predictions; unreviewed count appears in the analytics widget. Re-editing a transaction replaces its final label, never adds a second vote. Original prediction and latest confirmed label are snapshots, independent of later automatic category changes.

Room migration 16 → 17 adds nullable `predictedCategory` and `predictionFinalCategory` to the main and recovery databases without changing existing rows. Existing confidence and feedback records do not retain an original prediction and cannot reliably reconstruct historical correctness, so they are not backfilled. SMS parsing, PDF parsing and model refinement record future predictions. Explicit category edits/saves record the final label. No server or network is used.

The rolling week is today and the previous six local calendar dates (including zero-spend dates), independent of the selected dashboard month. Active expenses are counted; income, deleted transactions and transfers are excluded. The expanded view is a linear plot; tapping a point shows its full date and rupee amount. Scales use four rounded currency intervals with headroom, showing ₹10,000 as a dashed reference whenever it falls in range. Low amounts use a smaller scale; empty weeks retain seven zero points. Privacy mode hides chart values as before.

A single retained LiveData stream supplies the dashboard and detail chart, so `getValue()` reads the stream that is actually observed. Rendering explicitly updates dataset/viewport after layout, including the first opening and privacy changes. Resume recomputes the rolling dates.

Merchant category variety counts distinct trimmed, case-insensitive, nonempty category names across all active transactions for the merchant, including transfers. Existing account financial totals remain unchanged. Deleted rows are excluded. The detail header lists the included categories for all time.

## Verification (27 September 2026)

- Debug APK assembled and installed over the existing app on Samsung SM-A146B; existing account history preserved.
- 751 JVM tests passed, including observed-stream lifecycle, currency scales, reviewed-only metrics, and repository preservation of original predictions.
- Three isolated instrumentation tests passed on the phone: first-layout/empty/single-value/privacy/data-update chart rendering; migration from schema 16 without fabricated prediction history; distinct category aggregation including transfers and excluding deleted rows.
- Device UI inspected: Daily, seven-day detail and point selection, Prediction Insights empty-history explanation, Accounts counts and merchant category list, and navigation back to Daily.
- Dashboard resource contrast passes 27 foreground/background pairs in each appearance mode (minimum 8.25:1).
- Full lint remains blocked by an existing `RecyclerView` position-capture error in `ui/premium/PremiumPlanAdapter.java:38` (508 warnings); no lint errors in the changed feature code.

Use `scripts/moneymitra-verification.init.gradle` when building/running these device tests. It changes the application ID to `com.example.spendtracker.verification` so tests never modify the installed user's app database. Build the main APK again without that init script before installing the actual update.

## UI refinement (28 September 2026)

The seven-day strip and activity overlay appear only on Daily. Close icons use visible 32dp images inside 48dp targets. Leaving Daily dismisses chart details and animates the add button out; returning animates it back. Rhythm uses a compact header with the yearly share action aligned to the right, and smaller month/week cards. Position cards also use reduced padding. The notification icon has a larger drawable area.

Daily activity uses a labeled square-root scale: bar height = sqrt(actual rupees), tick labels convert back to actual rupees. This compresses exceptional days without clipping them, makes small days visible, and retains an exact zero baseline. Each bar stores its original amount for selection; the ₹10,000 reference sits at sqrt(10,000) = 100. The seven-day line remains linear. Activity still sums absolute income, expense and transfers, not just expenses.
