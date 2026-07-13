#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="${PROJECT_ROOT:-$(cd "$SCRIPT_DIR/.." && pwd)}"
ANDROID_SIGNING_ENV_FILE="${ANDROID_SIGNING_ENV_FILE:-/etc/baby-diary/android-signing.env}"
ANDROID_RELEASE_CERT_FILE="${ANDROID_RELEASE_CERT_FILE:-$PROJECT_ROOT/config/android-release-cert.sha256}"
ANDROID_RELEASE_VERSION_FILE="${ANDROID_RELEASE_VERSION_FILE:-$PROJECT_ROOT/config/android-release-version.properties}"
ANDROID_JAVA_HOME="${ANDROID_JAVA_HOME:-${JAVA_HOME:-/usr/lib/jvm/java-21-openjdk-amd64}}"
ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-/opt/android-sdk}}"
ANDROID_GRADLE_WORKERS="${ANDROID_GRADLE_WORKERS:-1}"

if [ ! -r "$ANDROID_RELEASE_VERSION_FILE" ]; then
  echo "Android release version file is not readable: $ANDROID_RELEASE_VERSION_FILE" >&2
  exit 1
fi

# shellcheck disable=SC1090
. "$ANDROID_RELEASE_VERSION_FILE"
ANDROID_VERSION_CODE="${ANDROID_VERSION_CODE:-${1:-${VERSION_CODE:-}}}"
ANDROID_VERSION_NAME="${ANDROID_VERSION_NAME:-${2:-${VERSION_NAME:-}}}"

if [[ ! "$ANDROID_VERSION_CODE" =~ ^[1-9][0-9]*$ ]] \
  || [[ ! "$ANDROID_VERSION_NAME" =~ ^[0-9]+(\.[0-9]+){1,3}([.-][A-Za-z0-9]+)*$ ]]; then
  echo "Android release version is invalid" >&2
  exit 1
fi

if [ ! -r "$ANDROID_SIGNING_ENV_FILE" ]; then
  echo "Android signing environment is not readable: $ANDROID_SIGNING_ENV_FILE" >&2
  exit 1
fi

set -a
# shellcheck disable=SC1090
. "$ANDROID_SIGNING_ENV_FILE"
set +a

: "${ANDROID_KEYSTORE_FILE:?ANDROID_KEYSTORE_FILE is required}"
: "${ANDROID_KEYSTORE_PASSWORD:?ANDROID_KEYSTORE_PASSWORD is required}"
: "${ANDROID_KEY_ALIAS:?ANDROID_KEY_ALIAS is required}"
: "${ANDROID_KEY_PASSWORD:?ANDROID_KEY_PASSWORD is required}"

if [ ! -x "$ANDROID_JAVA_HOME/bin/java" ] \
  || ! "$ANDROID_JAVA_HOME/bin/java" -version 2>&1 | grep -q 'version "21'; then
  echo "Android release builds require Java 21" >&2
  exit 1
fi

if [ ! -d "$ANDROID_SDK_ROOT/platforms/android-36" ] \
  || [ ! -x "$ANDROID_SDK_ROOT/build-tools/36.0.0/apksigner" ]; then
  echo "Android SDK Platform and Build Tools 36 are required" >&2
  exit 1
fi

EXPECTED_CERT_SHA256="$(tr -d '[:space:]' < "$ANDROID_RELEASE_CERT_FILE")"
ACTUAL_CERT_SHA256="$("$ANDROID_JAVA_HOME/bin/keytool" -exportcert \
  -keystore "$ANDROID_KEYSTORE_FILE" \
  -storepass "$ANDROID_KEYSTORE_PASSWORD" \
  -alias "$ANDROID_KEY_ALIAS" 2>/dev/null \
  | sha256sum \
  | cut -d' ' -f1)"

if [ "$ACTUAL_CERT_SHA256" != "$EXPECTED_CERT_SHA256" ]; then
  echo "Android signing certificate does not match the pinned release certificate" >&2
  exit 1
fi

export JAVA_HOME="$ANDROID_JAVA_HOME"
export ANDROID_HOME="$ANDROID_SDK_ROOT"
export ANDROID_SDK_ROOT
export ANDROID_VERSION_CODE
export ANDROID_VERSION_NAME
export PATH="$JAVA_HOME/bin:$ANDROID_SDK_ROOT/platform-tools:$PATH"

npm --prefix "$PROJECT_ROOT/frontend" run build

cd "$PROJECT_ROOT/frontend"
npx cap sync android

cd android
./gradlew --no-daemon --max-workers="$ANDROID_GRADLE_WORKERS" \
  lintRelease testDebugUnitTest assembleRelease bundleRelease

APK_PATH="$PROJECT_ROOT/frontend/android/app/build/outputs/apk/release/app-release.apk"
AAB_PATH="$PROJECT_ROOT/frontend/android/app/build/outputs/bundle/release/app-release.aab"

bash "$PROJECT_ROOT/scripts/verify-android-artifact.sh" \
  "$APK_PATH" \
  "$AAB_PATH" \
  "$ANDROID_VERSION_CODE" \
  "$ANDROID_VERSION_NAME"

echo "Android signed release build passed"
echo "version: $ANDROID_VERSION_NAME ($ANDROID_VERSION_CODE)"
echo "certificate sha256: $ACTUAL_CERT_SHA256"
echo "apk: $APK_PATH"
echo "aab: $AAB_PATH"
