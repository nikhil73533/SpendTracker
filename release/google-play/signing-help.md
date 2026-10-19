# What the upload key means

A Play Console account and an upload key are different things. The upload key is a private certificate file, normally ending in `.jks` or `.keystore`, created when you first sign an Android app.

If TrackMint has already been uploaded (even to Internal testing), use that app's existing key. In Play Console, App integrity shows the upload certificate fingerprint. Compare it with your key before signing. If the key is lost, follow Google's upload-key reset flow instead of creating an unrelated signature and trying to upload it.

If this is TrackMint's first upload, create a new upload key and back it up together with its alias and passwords. Keep this private; do not add it to the store-assets ZIP, public repository or chat.

In Android Studio, use Build → Generate Signed App Bundle / APK → Android App Bundle, select the existing keystore (or create one only for a first upload), select the `playRelease` variant and generate the signed bundle.

The project also provides `scripts/sign-play-bundle.sh`, which accepts the keystore path, alias and passwords from local environment variables. It does not print the passwords or store them in source. The script signs the optimized Play bundle into `TrackMint-1.0.1-play.aab`.

An unsigned bundle is a build artifact only. Google Play will not accept it until it is signed with the correct upload key.
