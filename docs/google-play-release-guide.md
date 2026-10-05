# TrackMint Google Play release guide

Do not upload the `autopay` or `autopayRelease` variant to Google Play yet. It uses Razorpay for digital premium access but does not implement Google Play's India alternative-billing choice screen or External Transactions reporting. Use the regular Play Billing build for the first Play release, or complete enrollment and the alternative-billing APIs before including Razorpay in a Play-distributed build.

## 1. Freeze the application identity

1. Choose the permanent Play package name before the first upload. The current `com.example.spendtracker` is a development identifier and changing it after launch creates a different Play app.
2. Update `applicationId`, OAuth clients, Firebase configuration, deep links, FileProvider authority expectations, and any backend allowlists together.
3. Increase `versionCode` for every upload and use a user-facing `versionName`.

## 2. Choose the billing route

### Recommended first release: Google Play Billing

Build the normal `release` variant. Create matching subscription products/base plans in Play Console, complete purchase acknowledgement, restore, cancellation links, backend verification, and license-tester testing. Do not expose the Razorpay AutoPay entry point in this build.

### Razorpay inside the Play build: only after program completion

1. Enroll the package under **Play Console → Settings → Alternative billing** for India.
2. Keep Google Play Billing available alongside Razorpay as required for the selected program.
3. Integrate Google's alternative-billing/user-choice client APIs and Play-rendered choice flow.
4. Report every authorized alternative-billing transaction within the required window, including recurring renewals, using the External Transactions API.
5. Supply the subscription-management URL, payment method branding, fraud reporting, customer support, and required trust/safety declarations.
6. Test country gating so alternative billing is never offered where the package is not enrolled.

## 3. Prepare the release build

1. Create an upload keystore and store it outside source control. Enroll in Play App Signing and keep an offline backup of the upload key.
2. Put signing credentials in a local/CI secret store, not `build.gradle.kts`, `gradle.properties`, or Git.
3. Set production URLs and OAuth configuration. Never add Razorpay secrets or a Supabase secret/service key to the APK.
4. Build an Android App Bundle, not the debug APK: `./gradlew :app:bundleRelease`.
5. Inspect the signed bundle, run unit/instrumentation tests, lint, a release installation test, and accessibility/privacy checks on supported Android versions.

## 4. Prepare Play Console

1. Create the app with the final default language, app/game choice, free/paid status, and declarations.
2. Complete the store listing: TrackMint name, short/full description, phone screenshots, high-resolution icon, feature graphic, support email, website, and privacy-policy URL.
3. Complete App content: privacy policy, ads, app access, target audience, content rating, news status, and sensitive-permission declarations.
4. Complete Data safety from actual runtime behavior. Account for Google sign-in, billing identifiers, support submissions, optional backups, crash/analytics SDKs, and any third-party sharing. Do not infer answers only from permissions.
5. Explain the SMS permissions in the declaration and in-app disclosure. Keep message processing on-device as implemented and request the narrowest permissions at the point of use.
6. Configure countries/regions, pricing, subscription products, and license testers.

## 5. Test through Play

1. Upload the `.aab` to **Internal testing** and add tester email addresses.
2. Install only from the Play opt-in link so signing, delivery, Play Billing, updates, and restore are exercised as production will deliver them.
3. Test new purchase, pending/cancelled purchase, acknowledgement, renewal, expiry, refund, reinstall, second device, account switch, offline lease expiry, and process death.
4. Move to a closed test after internal acceptance. If the developer account is subject to Google's personal-account testing requirement, satisfy its tester count and duration before applying for production access.

## 6. Production rollout

1. Upload the final bundle, add concise release notes, and resolve every blocking policy/error item.
2. Start with a staged rollout rather than 100%. Monitor crashes/ANRs, billing verification, webhook failures, entitlement mismatches, refunds, and support volume.
3. Pause the rollout if payment or restore correctness regresses. Increase the percentage only after clean telemetry and successful purchase/restore checks.

