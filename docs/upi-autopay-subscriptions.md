# UPI AutoPay for the SpendTracker paywall

Researched 28 September 2026. This is an implementation design; no gateway account was configured, no mandate was created, and no payment was initiated. This follow-up supersedes the earlier suggestion to launch with a one-time pass in `custom-paywall-upi-approach.md`: the requested product is now recurring access with monthly, quarterly, six-monthly, and yearly choices.

## Recommended route

Keep the existing custom paywall and use **Razorpay Subscriptions + Android Standard Checkout + an authenticated payment backend** as the first integration candidate. The Android SDK's subscription Intent flow supports Google Pay, PhonePe, Paytm and BHIM. Its Standard Checkout matrix does not list Amazon Pay Android Intent support, so the UI must not promise every installed UPI app. Provider onboarding and bank/app compatibility still determine availability. [Razorpay's subscription app and platform matrix](https://razorpay.com/docs/payments/subscriptions/supported-banks-apps).

Cashfree is a second candidate: its AutoPay product explicitly advertises monthly, quarterly, half-yearly and annual schedules and mobile SDK mandate authorization. Confirm the current native Android subscription API, merchant enablement, and six-month mandate display in a provider proof of concept before selecting it. [Cashfree AutoPay](https://www.cashfree.com/upi-autopay/).

A regular `upi://pay` transfer is insufficient: recurring collection requires an authorized AutoPay mandate, a provider subscription, and subsequent payment reconciliation. The UPI app collects the user's authorization; SpendTracker must never collect a UPI PIN. Razorpay distinguishes UPI AutoPay from ordinary payments and also offers eNACH as a separate bank-account mandate method. [Supported subscription methods](https://razorpay.com/docs/payments/subscriptions/supported-payment-methods).

## Billing periods

The backend owns product IDs, prices, tax treatment, and provider plan mappings. Proposed Razorpay plan configuration:

| Paywall choice | `period` | `interval` | User-facing billing wording |
|---|---|---:|---|
| Monthly | `monthly` | 1 | Charged every month |
| Quarterly | `monthly` | 3 | Charged every 3 months |
| Six months | `monthly` | 6 | Charged every 6 months |
| Yearly | `yearly` | 1 | Charged every year |

These mappings follow the documented period/interval model. Validate the quarterly registration mapping during onboarding: the docs also list a dedicated quarterly frequency. Six-month billing is a custom frequency; Razorpay says undefined UPI frequencies register as **as-presented**. The subscription can have a six-month schedule while the bank mandate wording differs. Verify what the user actually sees and approves; never label a six-month collection as a monthly charge. [Create-plan API](https://razorpay.com/docs/api/payments/subscriptions/create-plan/).

Use calendar periods, not 30/90/180/365-day arithmetic. Keep trial length, initial charge, mandate maximum, first debit date, end date/cycle count and access expiry as separate fields. Show the full debit amount per cycle; any monthly equivalent is secondary. Prices remain unset until the business supplies them.

## Open-source components

| Project | License / scope | Where to use it |
|---|---|---|
| [razorpay/razorpay-java](https://github.com/razorpay/razorpay-java) | MIT; official API bindings including subscriptions and signature verification | Backend only; it uses merchant secrets |
| [cashfree/cashfree-pg-sdk-java](https://github.com/cashfree/cashfree-pg-sdk-java) | Apache; official server-side payment API SDK | Backend candidate; confirm selected release exposes the current subscription APIs |
| [cashfree/pg-samples](https://github.com/cashfree/pg-samples) | Official integration examples; check each sample's license before reuse | Reference for Android checkout wiring, not an independent banking service |

These libraries do not replace a licensed provider's payment infrastructure or merchant onboarding. Open-source SDK availability also does not imply the Android checkout binary or banking network is open source. For the app itself, follow the provider's [Android Standard Checkout integration](https://razorpay.com/docs/payments/payment-gateway/android-integration/standard/integration-steps/); keep secret-bearing server SDKs out of the APK.

## Purchase and renewal flow

1. Load an authenticated, versioned catalog; show the four supported periods, exact price, renewal terms, cancellation route and restore action.
2. Establish a recoverable purchase account. Keep expense records and SMS local; billing needs account and purchase metadata only.
3. Create one idempotent subscription attempt on the server. Resolve the provider plan from the catalog; do not accept a client-supplied amount or arbitrary provider plan ID.
4. Open native checkout using the server-created subscription ID. Let the gateway offer compatible installed UPI apps and hand off mandate authorization.
5. On return, show **Checking subscription**. Submit callback evidence to the backend, which verifies it and reconciles the provider state. Persist the pending attempt so process death or an offline return resumes the same attempt.
6. Grant access for the verified paid service period (or an explicit server-owned trial). Mandate authorization alone is not proof that the subscription's full first charge was collected.
7. Signed webhooks and reconciliation update renewal payments, paid-through dates, paused/cancelled mandates, failures and refunds. Closing the app must not prevent a payment from being recorded.

Razorpay's subscription callback signature uses HMAC-SHA256 over `payment_id + "|" + server_stored_subscription_id`. This differs from ordinary order verification. Compare securely on the server and reconcile amount, currency, account ownership and payment state before changing access. [Subscription integration and verification](https://razorpay.com/docs/payments/subscriptions/integration-guide/).

Recommended backend endpoints: `GET /catalog`, `POST /subscription-attempts`, `GET /subscription-attempts/{id}`, `POST /subscription-attempts/{id}/verify`, `POST /subscriptions/{id}/cancel`, `GET /me/entitlements`, and `POST /webhooks/razorpay`. Bind every operation to the account and store unique provider IDs. Deduplicate events, tolerate out-of-order delivery, and grant each billing period once. Maintain a reconciliation job for pending payments. [Subscription webhooks](https://razorpay.com/docs/payments/subscriptions/subscribe-to-webhooks).

Do not immediately start another subscription after a timeout. Separate mandate state from payment state and entitlement state. Cancellation normally stops future charges while preserving already-paid access; refunds follow the published policy. Plan changes must avoid overlapping mandates. Restore reads backend entitlements and must not let a Play restore erase UPI access.

## Google Play distribution

For digital premium features distributed through Google Play in India, the alternative route requires enrollment, the supported user-choice flow alongside Play Billing, external transaction reporting within 24 hours of authorization, and applicable adjusted Play fees. A custom UPI button alone does not satisfy this route. A separately distributed build needs its own distribution assessment. [Google's India alternative-billing requirements](https://support.google.com/googleplay/android-developer/answer/13306652?hl=en).

## Changes needed in this repository

- `PaywallFragment` / `PaywallViewModel`: provider-neutral catalog with four periods; pending confirmation, retry/status, cancellation and restore UI.
- `PremiumPlan`: separate internal plan ID and billing period from Play product IDs; never invent prices when catalog loading fails.
- `BillingManager`: preserve Play; add the enrolled user-choice integration and gateway checkout coordinator.
- `PremiumRepository` / `EntitlementCache`: read server-verified, source-aware entitlements with bounded offline validity. Replace local-only verification for production purchases.
- New backend: recoverable accounts, subscription catalog, provider API calls, signed webhook processing, reconciliation, refunds and Play reporting.

Production prerequisites are merchant onboarding/AutoPay activation, actual prices, a backend/authentication choice, gateway credentials, and Play enrollment if applicable. None are available in this repository, so the existing paywall has not been wired to a fictitious checkout. Test all four schedules, missing/incompatible UPI apps, authorization without payment, delayed callbacks, duplicate events, cancellation, failed renewals, reinstall restore and refunds before accepting real payments.
