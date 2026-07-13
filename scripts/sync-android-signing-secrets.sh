#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="${PROJECT_ROOT:-$(cd "$SCRIPT_DIR/.." && pwd)}"
ANDROID_SIGNING_ENV_FILE="${ANDROID_SIGNING_ENV_FILE:-/etc/baby-diary/android-signing.env}"
ANDROID_RELEASE_CERT_FILE="${ANDROID_RELEASE_CERT_FILE:-$PROJECT_ROOT/config/android-release-cert.sha256}"

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

if [ ! -s "$ANDROID_KEYSTORE_FILE" ]; then
  echo "Android keystore is missing: $ANDROID_KEYSTORE_FILE" >&2
  exit 1
fi

if [ ! -r "$ANDROID_RELEASE_CERT_FILE" ]; then
  echo "Pinned Android certificate fingerprint is missing: $ANDROID_RELEASE_CERT_FILE" >&2
  exit 1
fi

KEYTOOL="${ANDROID_KEYTOOL:-${ANDROID_JAVA_HOME:-/usr/lib/jvm/java-21-openjdk-amd64}/bin/keytool}"
if [ ! -x "$KEYTOOL" ]; then
  KEYTOOL="$(command -v keytool)"
fi

EXPECTED_CERT_SHA256="$(tr -d '[:space:]' < "$ANDROID_RELEASE_CERT_FILE")"
ACTUAL_CERT_SHA256="$("$KEYTOOL" -exportcert \
  -keystore "$ANDROID_KEYSTORE_FILE" \
  -storepass "$ANDROID_KEYSTORE_PASSWORD" \
  -alias "$ANDROID_KEY_ALIAS" 2>/dev/null \
  | sha256sum \
  | cut -d' ' -f1)"

if [ "$ACTUAL_CERT_SHA256" != "$EXPECTED_CERT_SHA256" ]; then
  echo "Android signing certificate does not match the pinned release certificate" >&2
  exit 1
fi

cd "$PROJECT_ROOT"
gh auth status >/dev/null 2>&1
base64 -w0 "$ANDROID_KEYSTORE_FILE" | gh secret set ANDROID_KEYSTORE_BASE64
printf '%s' "$ANDROID_KEYSTORE_PASSWORD" | gh secret set ANDROID_KEYSTORE_PASSWORD
printf '%s' "$ANDROID_KEY_ALIAS" | gh secret set ANDROID_KEY_ALIAS
printf '%s' "$ANDROID_KEY_PASSWORD" | gh secret set ANDROID_KEY_PASSWORD

echo "Android signing secrets synchronized"
echo "certificate sha256: $ACTUAL_CERT_SHA256"
