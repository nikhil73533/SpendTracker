# Razorpay setup before payment migration

Checked on 5 October 2026. Distribution target: Google Play and direct APK.

## Current project state

- The MoneyMitra Supabase project (`dwatnipgdvuftwcsgohm`) is connected.
- `billing-api` and `billing-admin` are deployed. Their deployment alone does not prove checkout is configured or tested.
- The remote `billing_plans` table currently has no rows.
- Android uses Razorpay subscription checkout in the main direct-distribution build and the AutoPay test builds. The main package no longer activates Google Play Billing at runtime.
- The checked-in configuration does no t supply `billingGoogleClientId`.
- Merchant secret values were not retrieved or displayed during this check.

## 1. Distribution decision

For premium digital features sold through Google Play in India, Google's alternative-billing program currently offers an alternative alongside Google Play Billing. It requires enrollment and the relevant billing/reporting integration. A Razorpay-only direct APK is a separate distribution flow.

Recommended implementation for the requested distribution:

- Direct APK: Razorpay only; exclude Google Play Billing from this build.
- Google Play: Google Play Billing plus Razorpay through the eligible alternative-billing program after enrollment. Regions outside India need their own eligibility checks.

In Play Console, open the app's monetization/alternative billing settings and check eligibility and enrollment. Tell the developer which countries you will distribute to and whether enrollment is complete. Existing Google Play subscribers, if any, also need their access preserved during migration.

Official requirements: https://support.google.com/googleplay/android-developer/answer/13306652?hl=en

## 2. Generate Razorpay test keys

1. Sign in at https://dashboard.razorpay.com and select Test Mode.
2. Open Account & Settings → Website and app settings → API Keys → Generate Key.
3. Save the Key ID and Key Secret securely. The secret is shown when generated.
4. Open your Supabase project's Edge Functions → Secrets:
   https://supabase.com/dashboard/project/dwatnipgdvuftwcsgohm/functions/secrets
5. Store the values as `RAZORPAY_KEY_ID` and `RAZORPAY_KEY_SECRET`. If already configured, verify they are from the same test account rather than regenerating them unnecessarily.

Do not paste secrets into chat, Android source, Gradle properties, or Git. Edge Functions use the secret internally. Android receives only the public checkout Key ID and a server-created subscription ID.

For live payments later, finish Razorpay merchant onboarding/KYC and website/app verification, confirm Subscriptions/UPI AutoPay availability, and use matching live keys and plans after testing.

Official instructions: https://razorpay.com/docs/payments/dashboard/account-settings/api-keys/

## 3. Create recurring test plans

The existing code expects the following prices. Confirm these are the intended prices before creating plans; Razorpay plans cannot be edited after creation.

| Plan | Total charge each period | Frequency | Supabase secret holding the plan ID |
| --- | --- | --- | --- |
| Monthly | ₹99 | Every 1 month | `RAZORPAY_PLAN_MONTHLY` |
| Quarterly | ₹149 | Every 3 months | `RAZORPAY_PLAN_QUARTERLY` |
| Six-monthly | ₹299 | Every 6 months | `RAZORPAY_PLAN_SIX_MONTHLY` |
| Yearly | ₹499 | Every 1 year | `RAZORPAY_PLAN_YEARLY` |

In Razorpay Test Mode, open Subscriptions → Plans → New Plan, choose INR, the amount and the frequency, then save each `plan_…` identifier under the corresponding Supabase secret name. Quarterly and six-monthly plans must use the interval that matches the table. Confirm UPI mandate support for the chosen frequencies with Razorpay before going live.

The developer will verify the provider plans and publish the Supabase catalog after configuration. Do not insert guessed plan IDs into the database.

Official instructions: https://razorpay.com/docs/payments/subscriptions/create-plans/

## 4. Configure sign-in for purchase ownership

The existing backend uses Google sign-in to identify who owns a subscription. This is independent of Google Play Billing and can still be used in the direct APK.

1. In Google Cloud Console, choose your app's project and open Google Auth Platform.
2. Configure Branding and Audience, including test users while the OAuth app is in testing.
3. Under Clients, create or locate a Web application OAuth client. Share its public client ID (ending in `.apps.googleusercontent.com`) with the developer; no client secret is needed by Android.
4. Save that same ID in Supabase Edge Function Secrets as `GOOGLE_WEB_CLIENT_ID`. The developer will configure Android's `billingGoogleClientId` with the matching value.
5. Create Android OAuth clients for the application IDs and signing-certificate SHA-1 fingerprints of the builds used. The current normal app is `com.example.spendtracker`; existing AutoPay builds use `com.example.spendtracker.autopay`. The developer will confirm final package names and provide the local signing fingerprints before this step. Play releases use the Play app-signing certificate fingerprint from Play Console.

Official sign-in setup: https://developer.android.com/identity/sign-in/credential-manager-siwg

## 5. Configure payment notifications and public support pages

In Razorpay Test Mode, open Account & Settings → Webhooks and register:

`https://dwatnipgdvuftwcsgohm.supabase.co/functions/v1/billing-api/v1/webhooks/razorpay`

Choose a strong webhook secret and store the exact same value in Supabase as `RAZORPAY_WEBHOOK_SECRET`. This is separate from the API Key Secret. Enable relevant subscription lifecycle, payment, and refund notifications; the developer will validate event handling before enabling checkout.

Store the Razorpay merchant account ID expected in webhook `account_id` as `RAZORPAY_ACCOUNT_ID`. If the dashboard does not expose it, obtain it from an authenticated test webhook/account details or Razorpay support; do not substitute the API Key ID.

Provide your public HTTPS terms, privacy, and support URLs. The backend expects `BILLING_TERMS_URL`, `BILLING_PRIVACY_URL`, and `BILLING_SUPPORT_URL`. Keep `BILLING_CHECKOUT_ENABLED=false` until the plans, sign-in, signature validation, and test checkout are verified.

Official webhook guide: https://razorpay.com/docs/payments/subscriptions/subscribe-to-webhooks/

Supabase secret storage: https://supabase.com/docs/guides/functions/secrets

## What to send back

Send configuration status, the public Google Web client ID, intended plan prices, public policy/support URLs, target Play countries, alternative-billing enrollment status, and whether existing Play subscribers need migration support. Do not send API secrets. These inputs let the payment migration proceed without guessing merchant settings or enabling untested live payments.
