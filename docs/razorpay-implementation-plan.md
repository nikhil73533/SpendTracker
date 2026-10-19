# Razorpay AutoPay implementation plan

29 September 2026. Implements the four recurring periods in `upi-autopay-subscriptions.md`.

1. **Distribution and configuration:** isolate AutoPay in direct-distribution build types; preserve the existing Play build. Keep checkout unavailable until the owner supplies HTTPS backend, Google OAuth client, merchant keys, approved plan IDs/prices, and published terms. No fabricated prices or secrets in Android.
2. **Payment backend:** Python WSGI service, durable SQLite storage, verified Google identity, versioned server-owned catalog, subscription attempts with idempotency and account ownership. Ambiguous provider creation must be recovered, never blindly retried.
3. **Payment verification:** subscription-specific HMAC verification, signed raw-body webhooks, event deduplication, invoice/payment reconciliation, durable paid-period grants, refund revocation, renewal failure handling, cancellation preserving paid access, and periodic recovery independent of the app.
4. **Android:** native Razorpay checkout, Google billing sign-in without Drive permissions, four catalog plans, persistent pending attempt, status/restore and cancel actions, bounded account-specific offline entitlement, and no access based on checkout callback alone.
5. **Verification:** deterministic backend lifecycle/security/API tests; Android unit tests, compile, lint, and device checks where tools are available. Record actual results separately from merchant/device tests awaiting credentials.
6. **Owner handoff:** setup/run instructions, required account configuration, deployment topology, test-mode walkthrough, and explicit production gates.

Confirmed by owner: separate direct-install build; prices ₹99 monthly, ₹149 quarterly, ₹299 six-monthly, ₹499 yearly. Other assumptions: Google sign-in for recoverable purchase identity; no trial, coupons, upgrades, add-ons, or proration. Prices include the full configured debit amount. Refund policy in this implementation revokes the affected paid period on any refund; obtain owner approval before launch. Existing expense/SMS data stays local. Google Play alternative billing and reporting require a separate enrolled integration and are not enabled by this implementation.

Acceptance: forged callbacks and wrong accounts cannot grant access; authorization alone cannot grant access; duplicate/late events cannot duplicate grants or reverse refunds; simultaneous attempts cannot create overlapping mandates; restored access comes from the same verified Google account; cancel stops renewal and retains unrefunded paid periods; interrupted checkout can recover after process death; missing configuration fails closed for AutoPay.

## Delivery status

Implementation and local verification are complete: separate Android build, native checkout, recoverable identity, server-owned pricing, durable attempts, HMAC verification, paid-period access, cancellation, refund handling, reconciliation, restore and offline lease checks. Debug and optimized unsigned release builds and lint pass; 49 backend tests and 784 Android/prediction unit tests pass. See `razorpay-test-results.md` for precise scope and limitations.

Deployment/merchant configuration and real-payment acceptance remain pending the owner's inputs in `../backend/README.md`. Device UI tests could not run after the connected phone disconnected. No bank account was charged.
