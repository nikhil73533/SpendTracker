# Critical Android fixes — 10 October 2026

## Changes

- Incoming SMS is persisted as WorkManager work before the broadcast finishes. Work only succeeds after the primary transaction save completes. Inbox recovery runs when the app starts and after permission is granted. Message identities prevent replay from replacing a reviewed transaction; legacy SMS records are checked before import.
- Manual entry waits for the repository result, prevents duplicate submissions, and keeps the form open on failure. The transaction-limit error is returned to the UI instead of displaying a Toast on a background thread. Recovery-copy and post-save errors cannot invalidate a successful primary save. Category budget slider values are rounded to valid increments.
- Premium plan selection disables RecyclerView change animations and visibly marks the selected plan. The existing catalog selection preservation is retained.
- Purchases request `one_time` orders and use standard Razorpay checkout. UPI is prominent and default payment methods remain available. New recurring purchases are rejected in the app. Existing mandate status/cancellation remains available for customers who already have one. Server verification still controls premium access.
- Advanced analytics opens without an authentication prompt, respects the shared privacy state, masks text, and clears chart data while locked. The Show values button authenticates before revealing values. The existing encrypted database remains in use; on-screen masking is not a new encryption format.
- Debug and release use the single application ID `com.example.spendtracker`; the `.autopay` build variants were removed. Test-only scripts continue to isolate test data.
- Bill parsing recognizes mobile recharge expiry/validity, subscription renewals such as Netflix, billing/invoice notices, and credit-card totals and due dates. Successful recharge receipts and promotions are excluded. Missing or ambiguous amounts/dates still require review.
- Excel export includes the complete active ledger, detail sheets per month, and daily, weekly, monthly, yearly, category, account and transaction-group summaries. A Views sheet explains the sheets. Export runs off the UI thread, uses distinct filenames, and masks financial values and personal details when privacy mode is locked.

## Verification

Verified on 14 October 2026: `:app:testDebugUnitTest :app:assembleDebug :app:compileDebugAndroidTestJavaWithJavac` completed successfully. All 811 unit tests passed; the debug APK built and Android device tests compiled. The save-result test now constructs its repository after mock configuration to avoid a race with background initialization. New regressions cover save completion/errors/duplicate submissions, SMS replay identities, recharge and renewal parsing, one-time checkout configuration, and cross-year Excel export with privacy masking.

Device tests cover yearly selection and masked analytics entry, but have not been executed. On the 14 October recheck, no phone was connected to Android debugging and no emulator was configured. No real payment has been attempted. Merchant-enabled UPI/payment methods and end-to-end payment confirmation require a device test with the configured backend.

The existing free-plan limit of 200 transactions remains. Saves report the limit instead of crashing. Recovery of additional SMS will resume after access permits more transactions. Existing separately installed app variants are not removed by a source/build change.
