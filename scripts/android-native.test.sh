#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
MANIFEST="$ROOT/frontend/android/app/src/main/AndroidManifest.xml"
CONFIG="$ROOT/frontend/capacitor.config.json"
CI_WORKFLOW="$ROOT/.github/workflows/ci.yml"
RELEASE_WORKFLOW="$ROOT/.github/workflows/android-release.yml"

grep -q 'io.github.langxi13.babydiary' "$CONFIG"
grep -q '"androidScheme": "https"' "$CONFIG"
grep -q '"CapacitorCookies"' "$CONFIG"
grep -q 'android:allowBackup="false"' "$MANIFEST"
grep -q '^google-services\.json$' "$ROOT/frontend/android/.gitignore"
! grep -q 'android.intent.action.SEND' "$MANIFEST"
! test -e "$ROOT/frontend/android/app/src/main/java/io/github/langxi13/babydiary/NativeShareReceiverPlugin.java"
! test -e "$ROOT/frontend/src/platform/nativeShareInbox.js"
! grep -q '<external-path' "$ROOT/frontend/android/app/src/main/res/xml/file_paths.xml"
grep -q 'Java 21' "$ROOT/scripts/build-android.sh"
grep -q -- '--no-daemon --max-workers' "$ROOT/scripts/build-android.sh"
grep -q 'cmdline-tools/latest/bin/sdkmanager' "$CI_WORKFLOW"
grep -q 'cmdline-tools/latest/bin/sdkmanager' "$RELEASE_WORKFLOW"
! grep -Eq 'run: sdkmanager ' "$CI_WORKFLOW" "$RELEASE_WORKFLOW"

echo "native Android shell, secure session, gallery, and camera actions are tracked"
