# TrackMint — Google Play launch checklist

Prepared 19 October 2026. Logo and banner are excluded because they are already complete.

Business name: **TrackMint**
Support email: **ng1200821@gmail.com**
App package: **com.app.pinch**
Launch plan: **Free, with checkout disabled**

## Files and listing information

| Item | Status | What remains |
| --- | --- | --- |
| Android App Bundle (.aab) | Built, unsigned | Sign `TrackMint-com.app.pinch-1.0.1-UNSIGNED.aab` using the correct upload key. The development APK is for testing, not Play upload. |
| Upload signing key | Not provided/configured | Locate the existing `.jks` or `.keystore` file, key alias and locally stored passwords. See `signing-help.md`. Do not send passwords in chat. |
| App title | Suggested | Confirm **TrackMint: Expense Tracker**. |
| Short description | Ready | `short-description.txt` — 76 characters, within the 80-character limit. |
| Full description | Ready | `long-description.txt` — 2,203 characters, within the 4,000-character limit. |
| Phone screenshots | Pending | Capture at least two real app screenshots. Use portrait 9:16 images, preferably 1080 × 1920, with fictional/sample records and no personal notifications or financial information. |
| Privacy policy | Text ready; hosting pending | Publish `privacy-policy.html` at a public HTTPS address that opens without login. Add the address in Play Console. |
| Demo login / reviewer access | Ready | No TrackMint login is required. Use `reviewer-access.txt`; never supply your personal Google password. |
| Developer contact | Ready | Use TrackMint and ng1200821@gmail.com; complete any additional verified contact fields requested by Console. |
| Release notes | Draft below | Paste these with the first production release after testing. |

## Play Console setup and declarations

- [ ] Use your existing Play Console account and complete any outstanding identity, contact or device verification shown on its dashboard.
- [ ] Create/select the listing intended for **com.app.pinch**. An existing app with a different package ID cannot be updated by this bundle.
- [ ] Choose the default language, app type, **Finance** category and appropriate tags.
- [ ] Set distribution to **Free** and select the launch countries/regions.
- [ ] Configure Play App Signing and upload the correctly signed bundle. Confirm version code 2 has not already been used for this package; increase it if necessary.
- [ ] Complete **App access**: local features work without login; include the reviewer instructions.
- [ ] Complete **Data safety** using the final app behavior. Review Android backup, chosen document providers, ML Kit telemetry and support flows; do not assume “no data collected” because transaction processing is local.
- [ ] Complete the **SMS permission declaration** for READ_SMS and RECEIVE_SMS. Explain SMS-based money management, the consent screen, new-message-only processing, and manual mode. Prepare a demonstration video if the form requests one. Approval is not guaranteed.
- [ ] Complete **Content rating**, **Target audience and content**, **Ads**, and **Financial features** declarations accurately. Check the final SDKs and features before answering.
- [ ] Enter the public privacy-policy URL and support email; review the displayed store listing.

## Testing and launch

- [ ] Upload to **Internal testing** and install from Google Play, so the signing and delivery path match the eventual release.
- [ ] Verify fresh-install SMS behavior: old inbox messages are not imported; new financial messages are detected after consent.
- [ ] Verify manual tracking, categories, analytics, statement imports, exports and the free plan without payment checkout.
- [ ] Verify a phone backup saves outside app-owned storage and restores records/model using the password; wrong passwords must leave current records unchanged.
- [ ] Test an actual uninstall/reinstall with a saved phone backup on a disposable test installation. Preserve real records before testing.
- [ ] Verify Android automatic backup/recovery with the same Google account and compatible signing certificate. A backup must have completed before uninstall; Android controls scheduling and availability.
- [ ] Review the Play pre-launch report, crashes/ANRs, permission behavior, target API validation and 16 KB runtime compatibility. Local APK alignment checks have passed, but runtime testing remains necessary.
- [ ] If your account is subject to the new-personal-account rule, run a closed test with **at least 12 testers continuously opted in for at least 14 days**, then apply for production access and describe testing/feedback. Check the requirement shown in your Console dashboard.
- [ ] Resolve blocking Console issues, select countries and rollout settings, add release notes and submit the production release for review.

## Suggested first-release notes

Track income and expenses with on-device insights. Enable tracking for new transaction messages, or add records manually. Save password-protected phone backups and recover supported Android backups after reinstall. All features in this launch version are available without paid checkout.

## Current technical status

- App version: 1.0.1 (version code 2); package com.app.pinch; target API 36.
- Final APK and unsigned AAB built successfully; 821 unit tests passed.
- Latest test APK installed successfully on Samsung Galaxy A14 on 19 October 2026.
- The installable APK uses a development signature. It is not a production-signed Play release.
- Changing package IDs does not transfer old-app records automatically. Keep the old app until any needed data transfer is complete.
- This checklist does not represent Google Play approval or completed Console declarations.

## Official references

- [Store descriptions and screenshots](https://support.google.com/googleplay/android-developer/answer/9866151?hl=en)
- [Testing requirements for new personal accounts](https://support.google.com/googleplay/android-developer/answer/14151465?hl=en)
- [SMS and call-log permissions](https://support.google.com/googleplay/android-developer/answer/10208820?hl=en)
- [Target API requirements](https://support.google.com/googleplay/android-developer/answer/11926878?hl=en)
- [Android backup behavior and limits](https://developer.android.com/identity/data/autobackup)
- [ML Kit data disclosure](https://developers.google.com/ml-kit/android-data-disclosure)
