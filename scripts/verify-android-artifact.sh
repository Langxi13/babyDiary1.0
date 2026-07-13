#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="${PROJECT_ROOT:-$(cd "$SCRIPT_DIR/.." && pwd)}"
ANDROID_RELEASE_CERT_FILE="${ANDROID_RELEASE_CERT_FILE:-$PROJECT_ROOT/config/android-release-cert.sha256}"
ANDROID_JAVA_HOME="${ANDROID_JAVA_HOME:-${JAVA_HOME:-/usr/lib/jvm/java-21-openjdk-amd64}}"
ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-/opt/android-sdk}}"
APK_PATH="${1:-}"
AAB_PATH="${2:-}"
EXPECTED_VERSION_CODE="${3:-}"
EXPECTED_VERSION_NAME="${4:-}"

if [ -z "$APK_PATH" ] || [ -z "$AAB_PATH" ] \
  || [ -z "$EXPECTED_VERSION_CODE" ] || [ -z "$EXPECTED_VERSION_NAME" ]; then
  echo "usage: scripts/verify-android-artifact.sh <apk> <aab> <version-code> <version-name>" >&2
  exit 2
fi

if [ ! -s "$APK_PATH" ] || [ ! -s "$AAB_PATH" ]; then
  echo "Android release artifact is missing or empty" >&2
  exit 1
fi

APKSIGNER="$ANDROID_SDK_ROOT/build-tools/36.0.0/apksigner"
AAPT="$ANDROID_SDK_ROOT/build-tools/36.0.0/aapt"
JARSIGNER="$ANDROID_JAVA_HOME/bin/jarsigner"
KEYTOOL="$ANDROID_JAVA_HOME/bin/keytool"

if [ ! -x "$APKSIGNER" ] || [ ! -x "$AAPT" ] \
  || [ ! -x "$JARSIGNER" ] || [ ! -x "$KEYTOOL" ]; then
  echo "Android Build Tools 36 and Java 21 signing tools are required" >&2
  exit 1
fi

SIGNATURE_OUTPUT="$("$APKSIGNER" verify --verbose --print-certs "$APK_PATH")"
grep -q '^Verified using v2 scheme (APK Signature Scheme v2): true$' <<<"$SIGNATURE_OUTPUT"
ACTUAL_CERT_SHA256="$(sed -n 's/^Signer #1 certificate SHA-256 digest: //p' <<<"$SIGNATURE_OUTPUT")"
EXPECTED_CERT_SHA256="$(tr -d '[:space:]' < "$ANDROID_RELEASE_CERT_FILE")"
if [ "$ACTUAL_CERT_SHA256" != "$EXPECTED_CERT_SHA256" ]; then
  echo "APK certificate does not match the pinned Android release certificate" >&2
  exit 1
fi

"$JARSIGNER" -verify "$AAB_PATH" >/dev/null
AAB_CERT_SHA256="$("$KEYTOOL" -printcert -jarfile "$AAB_PATH" 2>/dev/null \
  | sed -n 's/^[[:space:]]*SHA256: //p' \
  | tr -d ':' \
  | tr '[:upper:]' '[:lower:]')"
if [ "$AAB_CERT_SHA256" != "$EXPECTED_CERT_SHA256" ]; then
  echo "AAB certificate does not match the pinned Android release certificate" >&2
  exit 1
fi

BADGING="$("$AAPT" dump badging "$APK_PATH")"
grep -Fq "package: name='io.github.langxi13.babydiary'" <<<"$BADGING"
grep -Fq "versionCode='$EXPECTED_VERSION_CODE'" <<<"$BADGING"
grep -Fq "versionName='$EXPECTED_VERSION_NAME'" <<<"$BADGING"
grep -Fq "sdkVersion:'24'" <<<"$BADGING"
grep -Fq "targetSdkVersion:'36'" <<<"$BADGING"
grep -Fq "application-label:'Baby Diary'" <<<"$BADGING"

MANIFEST_TREE="$("$AAPT" dump xmltree "$APK_PATH" AndroidManifest.xml)"
grep -Eq 'android:allowBackup.*0x0$' <<<"$MANIFEST_TREE"
grep -Eq 'android:usesCleartextTraffic.*0x0$' <<<"$MANIFEST_TREE"
grep -q 'android:dataExtractionRules' <<<"$MANIFEST_TREE"

CAPACITOR_CONFIG="$(unzip -p "$APK_PATH" assets/capacitor.config.json)"
printf '%s' "$CAPACITOR_CONFIG" | node -e '
  const fs = require("node:fs")
  const config = JSON.parse(fs.readFileSync(0, "utf8"))
  if (config.appId !== "io.github.langxi13.babydiary") process.exit(1)
  if (config.server?.url) process.exit(1)
  if (config.server?.hostname !== "localhost") process.exit(1)
  if (config.server?.androidScheme !== "https") process.exit(1)
  if (config.plugins?.CapacitorCookies?.enabled !== true) process.exit(1)
'

echo "Android release artifact verification passed"
echo "version: $EXPECTED_VERSION_NAME ($EXPECTED_VERSION_CODE)"
echo "certificate sha256: $ACTUAL_CERT_SHA256"
sha256sum "$APK_PATH" "$AAB_PATH"
