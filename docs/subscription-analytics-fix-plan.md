# Subscription and analytics corrections — 8 October 2026

## Scope and evidence

Preserve existing local work. The connected billing database was inspected read-only: monthly, quarterly, six_monthly and yearly are all active, INR 99/149/299/499, catalog version 1. The paywall already has a local fix to stop Play catalog emissions overwriting Razorpay choices; retain and regression-test that ownership. Do not manufacture fallback prices or provider IDs.

The in/out query is hard-coded to monthly totals. Category changes render only the first category and omit categories that disappear. Inclusive previous-period queries overlap at the start boundary. Google Drive currently launches sign-in.

## Implementation sequence

1. Extract and validate the four-period Razorpay catalog; display all configured choices even when checkout is disabled, separately gate purchasing, preserve selection across refresh, and make refresh/errors recoverable. Verify catalog completeness by IDs/periods rather than row count. Keep payment signatures, ownership checks and server-only entitlement grants intact.
2. Confirm native Android UPI mandate support using Firecrawl and official Razorpay documentation. Keep the SDK's supported app discovery, put UPI first without hiding fallback methods, and explain merchant/app eligibility. Never present ordinary UPI transfers as mandates or promise unsupported CRED recurring checkout.
3. Replace Google Drive connect with a coming-soon message; keep local backup and restore available.
4. Feed selected granularity through ViewModel/repository/service to actual in/out aggregates, and restore correct headings with the retained ViewModel state.
5. Add savings metrics from recorded income minus expenses, excluding transfers. Compare adjacent non-overlapping periods and distinguish unavailable rates from zero. Show all category changes, new spending, and -100% when a category disappears. Document that surplus is recorded cash flow, not confirmed bank savings.
6. Verify with focused unit tests for catalog validation, checkout shape, granular queries, financial math and date boundaries; compile Android and run relevant existing tests/lint. Record real-device/merchant limits separately.

## Acceptance checks

- Four plans remain visible after refresh; selected quarterly/six-month/yearly choice is retained and sent unchanged to checkout.
- Incomplete/malformed catalogs cannot initiate payment; unavailable checkout explains why.
- Subscription checkout carries a server subscription ID; success remains pending until server verification.
- Google backup click displays “Feature coming soon” without OAuth.
- Daily/weekly/monthly/yearly chart labels and queries agree.
- Savings rate is unavailable with no positive recorded income; overspending remains negative.
- Adjacent ranges share no transaction; all expense category changes are visible, including new and stopped categories.

## Research

Firecrawl retrieved the full country-neutral Markdown documentation (the HTML pages redirect to US content). Razorpay's [subscription app/platform matrix](https://razorpay.com/docs/payments/subscriptions/supported-banks-apps.md) confirms Google Pay and PhonePe for Standard Checkout Android UPI Intent. CRED is not listed in that recurring matrix. Actual availability depends on merchant enablement and compatible installed apps/banks.

No live payment, mandate, deployment or billing-data mutation is required for local implementation and regression checks.

## Merchant-side UPI availability

Razorpay's [Subscriptions Settings](https://razorpay.com/docs/payments/subscriptions/settings.md), retrieved using Firecrawl, documents a separate UPI switch under **Subscriptions → Settings**. Check it in the same Test/Live mode as the app's server key. Standard Checkout cannot enable a merchant-disabled payment method through display configuration.

The app prefers UPI while retaining Razorpay's default eligible payment methods. It does not force an app package, fabricate a UPI mandate, or replace subscription checkout with an ordinary UPI payment. Google Pay and PhonePe support is documented for native Android subscription Intent. CRED is not listed in that recurring support matrix; support for ordinary CRED payments does not establish AutoPay support.

After merchant enablement, the remaining acceptance exercise is to open each duration on a device with a supported UPI app, check the full recurring debit amount/frequency, authorize a test mandate, and verify callback/webhook reconciliation, cancellation and interrupted-checkout recovery. Do not equate a local checkout-options test with a completed bank mandate.

## Verification commands

- Android app: `./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug` (with the configured JDK).
- Server catalog validation: `node supabase/functions/billing-api/catalog-validation.test.ts`.
- Materialized financial queries: `python3 scripts/verify-analytics-materialization.py` (with `JAVA_HOME`).
- Isolated phone checks: `./gradlew -I scripts/subscription-analytics-verification.init.gradle :app:connectedAutopayAndroidTest`.

The phone checks use a separate verification package and database, and an empty billing URL. They cannot create a gateway payment. The normal debug APK is built independently with its normal billing configuration.

## Verified implementation status — 9 October 2026

Implemented catalog ordering/validation and selection retention, UPI-first subscription checkout with fallback methods, the Google backup coming-soon action, granularity-aware money-flow queries/headings, savings metrics, and all-category percentage comparisons. Comparison queries run in a consistent database transaction. Existing unrelated working-tree changes were preserved.

- Focused Android unit tests: **60 passed**, including catalog, checkout options, entitlement gates, financial calculations and savings/date boundaries.
- Android debug APK: built successfully at `app/build/outputs/apk/debug/app-debug.apk`.
- Android lint: **0 errors, 587 warnings**; warnings remain and this is not a warning-free build.
- Server catalog validation: **3 passed**; billing endpoint syntax check passed.
- Financial materialization verification: **158 date ranges** passed, including mutation/backfill and rollback checks.
- Full app unit suite: **798 tests, 6 failures** in existing prediction-persistence mocks and SMS/bank-name expectations. These unrelated failures were not hidden or changed; the original results are retained in `.artifacts/subscription-analytics/full-unit-results`.
- Isolated phone-test app and test APK: compiled successfully. Execution is **blocked: no connected devices** on the resumed run. The verification-only build restricts Java test sources to the two relevant classes, avoiding an existing outdated constructor call in `ReceiptAndTransferIntegrationTest`; the normal test configuration is unchanged. Eight device tests still need execution with an unlocked, authorized Android device connected.

Production rollout remains separate: backend changes have not been deployed, the normal phone app has not been replaced, and no bank mandate/payment has been authorized. Merchant UPI enablement and an end-to-end test mandate are still required before claiming live AutoPay readiness.
