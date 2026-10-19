# Razorpay AutoPay verification

29 September 2026. No merchant credentials were supplied. These results describe local automated checks, not a successful real Razorpay payment.

## Automated coverage

- Backend: **49 tests passed**. Combined statement/branch coverage: **91%** for billing and maintenance modules. Uses a deterministic Razorpay simulator; Google token validation uses the real Google verifier with locally generated RSA-signed JWTs/certificates.
- Android application: **769 unit tests passed**, including 10 new access-lease tests.
- Prediction library: **15 unit tests passed**.
- Android compilation, debug APK assembly, optimized release APK assembly (R8) and final lint: **passed**. Lint has **0 errors and 554 warnings** across the app, including localization suggestions and intentional synchronous recovery-state writes. Release output is unsigned until the owner configures signing.
- Actual Gunicorn 23.0.0 local HTTP smoke check: **passed** startup, health response, no-store headers, unauthenticated request rejection, durable database creation and shutdown. Used placeholder credentials/empty temporary database; no provider calls.
- Test runtime: Python 3.10, google-auth 2.40.3, cryptography 44.0.2 and coverage 7.2.7. Android uses the repository's Gradle 9.5.0/AGP 9.3.1 and Java 21 environment.
- Instrumentation APK compiled. Device execution could not start: the initially connected Samsung SM-A146B disconnected, and the runner reported `No connected devices`. Three isolated tests cover missing-configuration gates, checkout activity recreation, and disabled paywall controls; these are not yet recorded as passing.

## Scenarios checked by backend tests

1. All four catalog periods/prices and provider plan matching; client amount/plan tampering and stale catalog rejection.
2. Captured invoice/payment validation: subscription, invoice/payment IDs, full amount, INR currency and service period. Authorization-only, small deposit, unpaid/malformed period and uncaptured payment do not grant access.
3. Checkout HMAC verification, wrong-account status/verify/cancel rejection, raw-body webhook HMAC, merchant matching and conflicting event ID rejection.
4. Duplicate callbacks/events and concurrent create requests create a single subscription/grant. Unknown creation is not repeated; provider creation recovered after response loss and server restart.
5. Webhook HTTP ingestion persists before worker processing. Provider outages leave work for retry; an early webhook recovers an unattached subscription; late authorization events cannot undo a refund.
6. Cancellation is idempotent and retains paid access. New mandates are blocked during an existing mandate or remaining paid access. Failed renewal expires at the paid-through date; a subsequent paid invoice restores access.
7. Full/partial refunds revoke only their affected period, remain revoked under stale provider reads, and are found even if an invoice stops reporting paid.
8. Restore after database reopen, account separation, future-dated access, expiry boundary, kill switch, old subscription prices after catalog change, malformed/oversized HTTP input and missing authentication.
9. Valid Google JWTs; wrong audience, issuer, expiry, future issue time, missing subject, unverified email, tampered payload and malformed token rejection.
10. Provider HTTP authentication/timeout behavior, pagination and non-retry of ambiguous POST. Offline operator recovery cannot release a reservation when the provider is unavailable or a matching subscription exists.

Android lease tests cover the current account, logout/another account, exact expiry, wall-clock rollback, elapsed-clock reset, stopped wall clock, paid-expiry cap and maximum 72-hour duration. A separate direct-build instrumentation test is prepared to verify that blank configuration cannot bypass premium gates; it has not run on a device.

## Merchant/device tests still required

- Configure real test plans and OAuth, deploy HTTPS backend, register webhook, and complete each plan using the Razorpay test environment. Verify merchant invoice fields and first full debit independently from mandate authorization.
- On real devices: UPI app handoff, missing/incompatible app, user cancellation, rotation/process death, offline return, delayed success and retry/status recovery.
- Confirm six-monthly mandate wording/limits and all four renewal frequencies with the activated merchant account.
- Trigger renewals, failures, pauses, cancellation, full/partial refunds and retry webhooks; verify paid-through dates and support workflow.
- Sign in on a second phone/reinstall and verify same-account restore and account switching. Exercise 72-hour offline expiry and reboot revalidation.
- Run controlled live UPI Intent tests and refunds only after merchant setup and explicit approval for the test amounts. Validate the signed/minified release with those devices before customer distribution.

## Issues found during verification

- Razorpay checkout and core AARs share a namespace rejected by AGP 9. Added the compatibility setting and pinned the SDK/core version pair; no core downgrade.
- Release shrinking found two missing classes from PDFBox's optional JPEG2000 codec. Added two targeted suppression rules, preserving the existing unsupported-codec behavior ([PDFBox documentation](https://github.com/TomRoush/PdfBox-Android#optional-dependencies)).
- Existing paywall RecyclerView click handling captured a stale row index. It now reads the current adapter position when clicked.
- Review caught potential mismatch between an old pending subscription price and an updated catalog. The backend now returns the reserved amount, and Android requires explicit consent to the saved price if different.

See `../backend/README.md` for reproducible commands, credential setup, the operating limits of the initial single-process service, and deployment requirements.

## Built artifacts

- Debug: `app/build/outputs/apk/autopay/app-autopay.apk`.
- Optimized unsigned release: `app/build/outputs/apk/autopayRelease/app-autopayRelease-unsigned.apk`.
- Both use `com.example.spendtracker.autopay`, with blank deployment configuration in this verification build. They do not initiate checkout without a configured HTTPS backend and Google OAuth client.
