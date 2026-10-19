# AutoPay backend and Android setup

This service implements Razorpay recurring premium access for the **separate direct-install Android build** selected by the owner. The existing Google Play checkout remains in the normal debug/release builds. No gateway credentials are included; no real subscription or debit was created during implementation.

## Owner inputs still required

1. A Razorpay merchant account with Subscriptions and UPI AutoPay enabled, completed onboarding, settlement bank, and **test** API key ID/key secret. Put secrets directly in the server's secret store; do not send them in chat or put them in Android/Gradle.
2. Four Razorpay plan IDs in the same mode/account as those keys. Prices are confirmed below. Confirm that these are the final amounts customers pay, including any applicable taxes. This implementation has no add-ons, discounts, trial, or prorated upgrades.
3. A backend host with HTTPS, a persistent local disk, outbound access to Google identity certificates and Razorpay, backups, and monitoring. A small always-on VM/container is sufficient for the initial cohort. This implementation uses one service process and SQLite; it is **not** suitable for ephemeral/serverless storage or multiple replicas.
4. A Google Cloud OAuth web client ID and Android OAuth client for `com.example.spendtracker.autopay` with the actual debug/release signing certificate SHA-1. The web client ID is shared by the backend and Android ID-token request. Configure the consent screen/test users. Google sign-in is for billing identity; no Drive access is requested by this flow.
5. Published HTTPS terms, privacy, refund/cancellation policy, and support page. Current behavior: cancel the mandate immediately and preserve unrefunded paid access; **any refund, including partial, revokes the affected billing period**. Confirm this policy before launch.
6. For distribution: release signing key, controlled device cohort, and approval to perform the final provider/live-device payment tests. Keep the AutoPay APK separate from a Play-distributed APK.

## Plans

Use INR and integer paise. Copy `plans.example.json` to ignored `plans.local.json`, replace only the provider plan IDs after creating the plans in Razorpay.

| ID | Price per debit | Razorpay period | Interval | Maximum debit cycles |
|---|---:|---|---:|---:|
| monthly | ₹99 / 9,900 paise | monthly | 1 | 120 |
| quarterly | ₹149 / 14,900 paise | monthly | 3 | 40 |
| six_monthly | ₹299 / 29,900 paise | monthly | 6 | 20 |
| yearly | ₹499 / 49,900 paise | yearly | 1 | 10 |

The catalog is server-owned. Before creating a subscription, the service fetches the actual provider plan and checks its amount, currency and frequency. A mismatch blocks checkout. Amounts and provider plan IDs supplied by the Android client are rejected. Bump `BILLING_CATALOG_VERSION` when publishing changed prices; old subscriptions retain their stored amount and provider plan ID.

Razorpay's period/interval model supports these schedules. Confirm the bank mandate wording for six-monthly billing during merchant testing (custom frequencies can show as-presented). See [create-plan API](https://razorpay.com/docs/api/payments/subscriptions/create-plan/).

## Deploy

Requires Python 3.10+ and the dependencies in `requirements.txt`.

```sh
cd backend
python3 -m venv .venv
.venv/bin/pip install -r requirements.txt
# Supply variables from .env.example using the host's secret/configuration manager.
# .env is not loaded automatically.
.venv/bin/gunicorn --workers 1 --threads 4 --timeout 180 --bind 127.0.0.1:8080 server:app
```

Run under a supervisor (systemd/container restart policy). Use a reverse proxy for TLS, a 256 KiB request-body limit, and rate limits on authenticated billing requests and webhook ingestion. Do not log Authorization headers or webhook bodies. Keep the database and WAL on a persistent local volume with restrictive file permissions. Use SQLite's online backup API/snapshot procedure, including WAL consistency, and test restore. Never replace the database with a blank file while mandates exist.

Use **one worker**, no `--preload`, no horizontally scaled replicas: the service includes a serialized reconciliation worker started in the process. SQLite constraints persist ownership/idempotency across restarts. The worker checks subscriptions and paid grants every 60 seconds (plus processing time), so an app does not have to remain open for renewals/refunds. This deliberately favors straightforward correctness for an initial small cohort; migrate to queued reconciliation and a shared transactional database before scaling. Provider calls can take time, and reconciliation uses bounded network timeouts. Monitor error logs, old `creating` attempts and unprocessed webhook records; `/health` is a process liveness endpoint, not a merchant connectivity check.

`BILLING_CHECKOUT_ENABLED=false` blocks new mandates and native checkout while status, restore, cancellation and reconciliation continue. Set to `true` only after setup and staging verification. Use separate databases, keys, plans, webhook secrets and URLs for test/live.

## Webhook

Configure `POST https://YOUR_HOST/v1/webhooks/razorpay` in the matching Razorpay mode, with the separate `RAZORPAY_WEBHOOK_SECRET` and matching `RAZORPAY_ACCOUNT_ID`. Subscribe to subscription lifecycle events (authenticated, activated, charged, pending, halted, paused, resumed, cancelled, completed), `invoice.paid`, `payment.captured`, `payment.failed`, and `refund.processed` where offered by the account.

The original body is HMAC-verified, the merchant account checked, and the event ID/digest saved before HTTP success. Duplicate events are deduplicated. Event payloads do not directly grant access: the worker fetches current provider invoices/payments, so delayed events cannot reverse a recorded refund. Webhook state and grant changes are transactional. Keep the previous endpoint/secret active until older deliveries finish when rotating secrets; this version accepts one webhook secret per service. [Razorpay webhook verification](https://razorpay.com/docs/webhooks/validate-test/).

Access requires a paid invoice associated with the stored subscription, matching amount/currency, a matching captured payment, and a provider billing start/end. A successful authorization or small deposit cannot unlock the app. The checkout signature uses `payment_id|stored_subscription_id`. [Subscription integration](https://razorpay.com/docs/payments/subscriptions/integration-guide/).

## Build Android

Public configuration only:

```sh
./gradlew :app:assembleAutopay \
  -PbillingBaseUrl=https://YOUR_HOST \
  -PbillingGoogleClientId=YOUR_WEB_CLIENT_ID.apps.googleusercontent.com
```

Debug APK: `app/build/outputs/apk/autopay/app-autopay.apk`. For a signed production build configure the owner's signing credentials securely and use `assembleAutopayRelease`. Do not distribute an unsigned/debug build to customers. Blank configuration displays setup unavailable and fails closed for premium features in the AutoPay build; it does not turn every feature premium.

Android uses the existing Google sign-in dependency, requesting only account/email/ID token for billing. Signed ID tokens are validated server-side using Google's official verification library and audience/issuer/expiry checks. Stable Google subject IDs bind subscriptions; email address changes do not change ownership. Same-account sign-in restores on reinstall/another phone. No expense/SMS/statement content is transmitted by this service.

The encrypted cache is a 72-hour maximum local lease delivered over authenticated HTTPS, capped at paid expiry and bound to the current account. Elapsed time, wall-clock rollback and reboot checks prevent simple clock extensions. A reboot requires online revalidation. It is not a separately signed offline license and cannot protect a modified/rooted client. The database remains the authority. Existing Play entitlements/cache are kept separate and cannot clear AutoPay grants.

Razorpay checkout `1.6.41`, standard-core `1.7.19`, core `1.0.19` are pinned. The vendor AARs currently share `com.razorpay`; `android.uniquePackageNames=false` is the AGP 9 compatibility setting. Remove it when the vendor publishes distinct namespaces; do not downgrade the core to evade the error. See [AGP 9 behavior changes](https://developer.android.com/build/releases/agp-9-0-0-release-notes) and [vendor issue](https://github.com/razorpay/razorpay-android-sample-app/issues/389).

## Endpoints

All `/v1` endpoints except the webhook require `Authorization: Bearer <Google ID token>`.

| Method/path | Purpose |
|---|---|
| GET `/v1/catalog` | Four plans, exact debit prices, policy URLs, availability |
| POST `/v1/subscription-attempts` | `{plan_id, catalog_version}` and `Idempotency-Key` |
| GET `/v1/subscription-attempts/{id}` | Owner-only provider reconciliation/status |
| POST `/v1/subscription-attempts/{id}/verify` | Razorpay payment ID, subscription ID, signature |
| POST `/v1/subscription-attempts/{id}/cancel` | Stop mandate; preserve paid grants |
| GET `/v1/me/entitlements` | Reconcile and restore all subscriptions for this account |
| POST `/v1/webhooks/razorpay` | Signed, durable event ingestion |

## Interrupted/ambiguous creation

A reservation and idempotency key are committed **before** the subscription POST. If the response is lost, recovery lists provider subscriptions and matches the server-generated `notes.attempt_id`; the POST is never automatically repeated. The same pending attempt is recovered after process death. The account cannot create a second mandate while the first is unresolved. Cross-device recovery uses server records, not just preferences.

If the provider accepted no subscription and repeated reconciliation still finds none, the attempt remains `creating` for operator review. This is intentional: absence in one API response is not proof of failure. Do not delete the reservation or suggest another payment. Inspect the Razorpay dashboard/API and request logs, confirm there is no matching provider subscription (including delayed creation), then mark the reservation `rejected` with the maintenance command below while the service is stopped. Never mark an attempt rejected if a provider subscription exists; cancel it through the normal owner flow instead.

```sh
# Same environment as the service; stop the service first. Never use for a known provider subscription.
python3 maintenance.py ATTEMPT_ID --confirm-no-provider-subscription
```

## Verification

```sh
PYTHONPATH=backend python3 -m unittest discover -s backend/tests -v
./gradlew :app:testDebugUnitTest :prediction:testDebugUnitTest :app:assembleAutopay :app:lintAutopay
./gradlew -I scripts/autopay-verification.init.gradle :app:connectedAutopayAndroidTest
```

Install `requirements-test.txt` for the backend JWT/coverage tests. Device tests use a separate `billingverification` application ID and no credentials. They must not be pointed at the normal installed tracker. See `../docs/razorpay-test-results.md` for actual results and pending merchant tests.

Before launch, complete all four sandbox plan checkouts and exercise success, close/back, no compatible UPI app, offline return, process death, cancellation, renewal failure/retry, refund, reinstall and a second device. Validate merchant-generated invoice/payment fields against the recorded test expectations. UPI Intent requires a controlled real-device/live-mode test according to [Razorpay Android testing guidance](https://razorpay.com/docs/payments/payment-gateway/android-integration/standard/integration-steps/). No automated test here debits a bank account. Obtain approval for specific live amounts and refunds first.

Google Play alternative billing/user-choice/reporting is not implemented in this direct-distribution build. Do not upload the AutoPay build as a substitute for that integration.
