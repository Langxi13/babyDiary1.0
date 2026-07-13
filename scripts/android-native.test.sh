#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
MANIFEST="$ROOT/frontend/android/app/src/main/AndroidManifest.xml"
CONFIG="$ROOT/frontend/capacitor.config.json"
PLUGIN="$ROOT/frontend/android/app/src/main/java/io/github/langxi13/babydiary/NativeShareReceiverPlugin.java"

grep -q 'io.github.langxi13.babydiary' "$CONFIG"
grep -q '"androidScheme": "https"' "$CONFIG"
grep -q '"CapacitorCookies"' "$CONFIG"
grep -q 'android:allowBackup="false"' "$MANIFEST"
grep -q '^google-services\.json$' "$ROOT/frontend/android/.gitignore"
grep -q 'android.intent.action.SEND_MULTIPLE' "$MANIFEST"
grep -q 'android:mimeType="image/\*"' "$MANIFEST"
! grep -q '<external-path' "$ROOT/frontend/android/app/src/main/res/xml/file_paths.xml"
grep -q 'MAX_FILES = 20' "$PLUGIN"
grep -q 'MAX_FILE_BYTES = 10L \* 1024L \* 1024L' "$PLUGIN"
grep -q 'ImageDecoder.decodeBitmap' "$PLUGIN"
grep -q '"image/jpeg"' "$PLUGIN"
grep -q 'Java 21' "$ROOT/scripts/build-android.sh"
grep -q -- '--no-daemon --max-workers' "$ROOT/scripts/build-android.sh"

echo "native Android shell, secure session, and image sharing are tracked"
