# Premium subscriptions: implementation and launch guide

## What is implemented

The existing codebase already contains the main Android and server-side recurring billing flow:

- `app` has a Java/XML paywall backed by Razorpay in the main direct-distribution package and the separate `autopay` test package. Each fixed-duration plan offers either a recurring UPI AutoPay mandate or a non-renewing one-time prepaid payment. The main package does not initialize Google Play Billing at runtime.
- `supabase/functions/billing-api` is the payment authority at `https://dwatnipgdvuftwcsgohm.supabase.co/functions/v1/billing-api`. It owns Razorpay plan/order lookups, durable subscription and one-time attempts, callback verification, signed-webhook ingestion, deduplication, reconciliation, grants, cancellation, refunds and restore. Checkout callbacks never grant access by themselves.
- The earlier Python service remains as a tested reference implementation and local simulator. It is no longer the intended hosted endpoint.
- The Android AutoPay client stores only an encrypted, account-bound three-day maximum offline lease. It restores from the verified service, survives an interrupted checkout, and fails closed when billing configuration is missing.
- The Supabase migrations add the admin-managed catalog, durable attempts, entitlement/audit tables, RLS, and server-only write boundaries. `supabase/functions/billing-admin` verifies both the administrator and Razorpay’s live plan before publishing a plan.

The schema and both Edge Functions are deployed to project `dwatnipgdvuftwcsgohm`. The function health endpoint is live. Razorpay test checkout is enabled: the stored test credentials are detected, all four fixed-price test plans are present, and the app can obtain a signed device test session without Google OAuth. Production checkout remains deliberately disabled until the live merchant setup, webhook/account validation, policy URLs, Google ownership, and a billing administrator are configured.

For device testing, `billing-api` now detects a stored `rzp_test_…` key, creates the four fixed test plans when the test catalog is empty, and issues a server-signed 30-day device test session when Google OAuth has not been configured. This path cannot activate with a live Razorpay key. Production still requires Google ownership, webhook/account validation, public policy URLs, an administrator-published catalog, and the explicit checkout switch.

## Free plan decision and enforcement

The intended free limit is **200 transactions over the lifetime of the local database**, including soft-deleted records; deletion must not reset the quota. Free users may have one transaction group and must not use backup/secondary-database backup, advanced analytics, budgets, bill detection, report export/sharing, or ad removal. Premium removes those limits.

This change makes `FeatureGate` fail closed, enforces the lifetime transaction cap at insertion/import, permits one group and rejects further group creation unless entitled, and sends Backup and Bill Alerts through the entitlement gate. Existing report and analytics gates now also apply even when a billing catalog is unavailable.

Before a production release, finish the remaining feature-specific gates: category-budget editing/notifications, the bill-detection worker itself (not only its screen), and every receipt-sharing entry point. The existing always-created cloned Room database must also be removed or made lazy/premium-only; it currently conflicts with the stated no-secondary-database free tier. These local checks are product enforcement; they cannot secure a modified APK.

## Supabase deployment

The intended Supabase project reference is `dwatnipgdvuftwcsgohm`.

1. The migrations are already applied. Grant the first administrator directly in SQL using the administrator’s `auth.users.id`; do not add a client-side “admin” switch.
2. `billing-admin` is deployed with JWT verification enabled and `billing-api` is deployed with gateway JWT verification disabled because it separately verifies Google ID tokens and Razorpay webhook HMACs. Set `RAZORPAY_KEY_ID`, `RAZORPAY_KEY_SECRET`, `RAZORPAY_WEBHOOK_SECRET`, `RAZORPAY_ACCOUNT_ID`, `RAZORPAY_PLAN_MONTHLY`, `RAZORPAY_PLAN_QUARTERLY`, `RAZORPAY_PLAN_SIX_MONTHLY`, `RAZORPAY_PLAN_YEARLY`, `GOOGLE_WEB_CLIENT_ID`, `BILLING_TERMS_URL`, `BILLING_PRIVACY_URL`, `BILLING_SUPPORT_URL`, and `BILLING_CHECKOUT_ENABLED=false` in Edge Function Secrets. Use test-mode keys and test-mode plan IDs for the test deployment; never mix modes.
3. Publish each catalog row through the function with only its TrackMint plan ID (`monthly`, `quarterly`, `six_monthly`, or `yearly`), optional `active`, and optional `catalog_version`. The function obtains the provider plan ID from secrets and rejects a Razorpay amount, currency, period, or interval mismatch.
4. Give the trusted `backend` service its own Supabase secret key and have it mirror verified grants/events to the two audit tables. It must never accept a mobile app’s assertion as an entitlement.
5. Publish all four plans, complete the sandbox matrix, and only then change `BILLING_CHECKOUT_ENABLED` to `true` for live mode. The Android build already points at the deployed Edge Function; Google OAuth is required for production but the server-signed device session is used for the isolated Razorpay test path.
6. The Supabase advisors have been run. Remaining findings are informational: server-only tables have RLS with no client policies (deny by default), and newly created indexes are naturally reported unused before traffic exists.

## Remaining external setup

- The four Razorpay **test-mode** plans are initialized and mirrored to Supabase: ₹99 monthly, ₹149 every three months, ₹299 every six months, and ₹499 yearly.
- Configure a dedicated Razorpay webhook at `https://dwatnipgdvuftwcsgohm.supabase.co/functions/v1/billing-api/v1/webhooks/razorpay` and subscribe to subscription lifecycle, invoice paid, payment captured/failed, and refund processed events.
- Add the first Supabase Auth administrator row, publish all four plans through `billing-admin`, and confirm `public.billing_plans` contains exactly four active rows.
- Configure the Google web OAuth client ID both as `GOOGLE_WEB_CLIENT_ID` in Supabase and `billingGoogleClientId` at Android build time.
- Run real-device sandbox checks for all four periods, cancellation, failed renewal, refund, restore, process death, and a second device. No automated test should make a live bank debit.

## Credential boundaries

| Credential | Location |
|---|---|
| Razorpay Key ID | May be returned by the trusted backend to checkout; do not hard-code or commit it in Android. |
| Razorpay Key Secret | Backend or Supabase Edge Function secret store only. Never Android, Gradle, source control, logs, or client responses. |
| Razorpay webhook signing secret | Backend/Edge Function secret store only; verify the original raw request body before parsing. |
| Supabase publishable key | Android only if it calls Supabase directly; RLS remains mandatory. |
| Supabase secret/service key | Trusted backend/Edge Function only. |

## Google Play policy gate

For a Google Play-distributed build in India, Razorpay is not a drop-in replacement for Play Billing. The developer must enroll in Google Play’s India alternative-billing program, follow its user-choice/UX and reporting requirements, report the authorization within 24 hours, and pay the adjusted Play fee. The repository’s AutoPay build is intentionally a separately distributed build; do not upload it to Play until the enrolled alternative-billing flow and reporting are implemented. Other markets require their own current policy assessment.

Sources: [Google Play India alternative billing](https://support.google.com/googleplay/android-developer/answer/13306652), [Razorpay subscription integration](https://razorpay.com/docs/payments/subscriptions/integration-guide/), and [Razorpay webhook validation](https://razorpay.com/docs/webhooks/validate-test/).
