# Build verification — 19 October 2026

- Rebuild required after changing the application ID to `com.pinchtrack.app`; the earlier artifacts were for `com.app.pinch`.
- 821 unit tests passed: 0 failures, 0 errors, 0 skipped. Includes five new backup authentication tests and existing fresh-SMS-window tests.
- Verify the installed package ID is `com.pinchtrack.app`; version 1.0.1 (2); target API 36; minimum API 26.
- Merged Play manifest enables the protected backup agent and excludes Razorpay activities/startup components and Play Billing permission.
- APK 16 KB ZIP alignment check passed; all four bundled arm64/x86_64 native libraries have ELF load alignment of at least 16 KB.
- Unit checks include valid backup round trip, random salt/nonce output, wrong password, tampering, truncation and invalid format.
- Six isolated Android recovery tests are provided for finance/model/key restoration, wrong password, invalid archive entry, interrupted backup and committed WAL records. They have not been executed on a device.
- The APK was installed successfully on Samsung Galaxy A14 (SM-A146B) on 19 October 2026. Actual uninstall/reinstall recovery, Android backup transport, UI/file-picker behavior and 16 KB runtime checks remain unverified.
- AAB is unsigned and is not upload-ready. No production upload or signing-key change was performed.
- Store screenshots are pending. Privacy policy is a local HTML file awaiting public hosting.

Checksums for the APK and AAB are in `SHA256SUMS.txt`.
