#!/usr/bin/env bash
set -euo pipefail
# Supply secrets through the environment or your secure local shell, never command arguments.
: "${TRACKMINT_KEYSTORE:?Set TRACKMINT_KEYSTORE to your existing upload keystore path}"
: "${TRACKMINT_KEY_ALIAS:?Set TRACKMINT_KEY_ALIAS to the upload key alias}"
: "${TRACKMINT_STORE_PASSWORD:?Set TRACKMINT_STORE_PASSWORD securely}"
: "${TRACKMINT_KEY_PASSWORD:?Set TRACKMINT_KEY_PASSWORD securely}"
project_dir="$(cd "$(dirname "$0")/.." && pwd)"
java_bin="${JAVA_HOME:-/home/mockingj/Downloads/android-studio-quail2-patch1-linux/android-studio/jbr}/bin"
input="$project_dir/app/build/outputs/bundle/playRelease/app-playRelease.aab"
output="${TRACKMINT_SIGNED_BUNDLE:-$project_dir/release/google-play/TrackMint-1.0.3-play.aab}"
[[ -f "$input" ]] || { echo "Build :app:bundlePlayRelease first." >&2; exit 1; }
"$java_bin/jarsigner" -keystore "$TRACKMINT_KEYSTORE" -storepass:env TRACKMINT_STORE_PASSWORD \
  -keypass:env TRACKMINT_KEY_PASSWORD -digestalg SHA-256 -signedjar "$output" "$input" "$TRACKMINT_KEY_ALIAS"
"$java_bin/jarsigner" -verify "$output"
echo "Signed bundle: $output"
