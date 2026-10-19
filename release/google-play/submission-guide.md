# TrackMint — Google Play submission package

Developer/business display name: **TrackMint**
Support email: **ng1200821@gmail.com**
Suggested listing title: **TrackMint: Expense Tracker**
Category: Finance. This is personal record keeping, not a lending or payment-transfer service.

## Files to upload

- App bundle: the signed `TrackMint-1.0.1-play.aab` when signing has been completed. Do not upload a debug APK or the direct-distribution Razorpay build.
- App icon: `assets/app-icon-512.png`, 512 × 512 RGBA PNG.
- Feature graphic: `assets/feature-graphic-1024x500.png`, 1024 × 500 RGB PNG.
- Phone screenshots: files in `screenshots/`, to be captured on a disposable emulator using com.pinchtrack.app with fictional sample records; capture is not yet complete. At least two are required.
- Short description: `short-description.txt` (76 characters).
- Full description: `long-description.txt` (under 4,000 characters).
- Privacy policy: `privacy-policy.html`. Publish this file at a public HTTPS URL you control, then enter that URL in Play Console. A local file is NOT a public policy URL. An offline copy is included inside the Play app under More → Privacy & Offline → Privacy Policy.
- App access/reviewer instructions: `reviewer-access.txt`. No demo username or password is required for local features. Do not submit your own Google account credentials.

## Owner actions before submission

1. Confirm this is the correct application ID (`com.pinchtrack.app`) for your Console app. Existing listings must retain their original ID and use an accepted upload certificate. The bundle uses version code 2; if you have already uploaded version 2 or higher, increase the version code before rebuilding.
2. Use the existing upload key for an existing Play app, or generate and safely back up a new upload key for a first upload. Enroll in Play App Signing as prompted. Never put keys/passwords in Git or the public asset ZIP.
3. Host the privacy policy and verify the public URL opens without a login. Confirm the policy and listing match the final app and any configured support website.
4. Complete Data safety using the notes below, the exact SDK versions and actual service configuration. Do not answer “no data collected” simply because financial processing is local.
5. Complete SMS permission declarations, content rating, target audience, ads and financial-features declarations. Do not claim the app is the default SMS handler.
6. Test Android automatic backup and reinstall recovery using the same account and Play signing certificate. Android controls timing and imposes a 25 MB limit. Also test password-protected phone backup files, including a wrong password and a reinstall. No app-specific Drive OAuth setup is required for Android Auto Backup.
7. Upload to Internal testing first. Run the Play pre-launch report, review native-library/page-size checks, and test on Android 16 and a 16 KB device/emulator. A successful local build is not Play approval.
8. Meet any testing and identity-verification requirements shown in your existing Console account before requesting production access.

## SMS declaration draft

Core use case: SMS-based money management. TrackMint uses READ_SMS and RECEIVE_SMS to detect financial transactions and bill reminders on the device, with user consent. It reads only the new-message recovery window after tracking is enabled on a fresh installation. SMS content is not transmitted to a TrackMint server for parsing. Users may decline SMS access and use manual records. Provide a review video showing disclosure, consent, new-message detection, and manual mode. Google decides whether the exception is approved.

The first-install fix does not delete transactions imported by older versions. Those are user records and are preserved. Users can remove unwanted old records themselves.

## Data safety preparation

| Flow | Data and destination | Action for Console |
| --- | --- | --- |
| Local records and SMS parsing | Financial/SMS content stays in app storage for core processing | Describe on-device use; distinguish from optional exports and backups |
| Android backup and user-selected folders | Financial records, learning model and recovery key are transferred through protected Android backup or password-encrypted documents | Review platform-backup exceptions and user-initiated document-provider rules; disclose these flows in the policy |
| Bundled ML Kit OCR | Device/app information, installation identifiers, diagnostics, performance, configuration and feature events sent to Google | Include SDK telemetry; verify exact data-type selections using the SDK disclosure |
| Help and support | Browser requests and content the user chooses to submit | Account for the actual hosted help/feedback service and email handling |
| Play launch payments | Paid checkout disabled; Razorpay startup components and checkout activities removed | No in-app purchase in this launch build; re-review before adding payments |

Do not guess final yes/no answers for Data safety, ads, content rating or financial features. The account owner must confirm the final behavior, audience and external services.

## Verified source references

- Target API policy: https://support.google.com/googleplay/android-developer/answer/11926878?hl=en — new submissions target API 36 from August 31, 2026.
- Store assets: https://support.google.com/googleplay/android-developer/answer/9866151?hl=en
- SMS permission policy: https://support.google.com/googleplay/android-developer/answer/10208820?hl=en
- ML Kit data disclosure: https://developers.google.com/ml-kit/android-data-disclosure
- SQLCipher Android integration: https://github.com/sqlcipher/sqlcipher-android

Sources checked on 17 October 2026. Recheck Console notices when uploading.

## Package identity and recovery
All build types use `com.pinchtrack.app`. This cannot update an existing Play listing under another package ID. Existing old-package data is not automatically accessible to the new package. Keep the old app until records have been safely transferred. Test runners use the required separate instrumentation package; they are not another consumer app.

See `reinstall-recovery.md` for setup, limits and verification.
