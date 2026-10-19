# TrackMint launch package — 19 October 2026

## Built and included

- A new bundle and preview APK for `com.pinchtrack.app` must be built before Play upload. The older artifacts named `com.app.pinch` are obsolete and must not be uploaded.
- Store icon (512 × 512), feature graphic (1024 × 500), short/full descriptions, privacy-policy HTML, reviewer instructions and submission guide.
- First-run SMS tracking starts with new messages; no historical inbox import.
- Free Play launch with checkout disabled.
- Protected Android backup recovery and password-protected phone backup files, including database keys and the learning model.

## Still required

- Real recovery testing remains pending. The latest test APK was installed successfully on the connected Samsung Galaxy A14 on 19 October 2026; no existing installation was removed.
- At least two actual 9:16 app screenshots using sample data. The screenshots folder is not yet populated.
- The correct upload signing key and local signing configuration. No key was generated or guessed.
- A public HTTPS URL for the included privacy policy.
- Console declarations, testing and account-specific production requirements.

Changing the app ID creates a different Android app and cannot update an old-ID Play listing. Old-ID private records do not migrate automatically; keep the old installation until data is transferred. A development-signed install and a Play-signed install may also require a phone-file transfer because their signatures differ.

See `reinstall-recovery.md` for recovery setup and limits. Automatic backups are not immediate or guaranteed; Android controls availability and timing. Phone backups survive uninstall in the chosen folder and require selecting the file and entering its password after reinstall.
