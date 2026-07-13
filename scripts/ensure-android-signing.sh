#!/usr/bin/env bash
set -euo pipefail

ANDROID_SIGNING_DIR="${ANDROID_SIGNING_DIR:-/etc/baby-diary/android-signing}"
ANDROID_SIGNING_ENV_FILE="${ANDROID_SIGNING_ENV_FILE:-/etc/baby-diary/android-signing.env}"
DEFAULT_KEYSTORE_FILE="$ANDROID_SIGNING_DIR/baby-diary-upload.jks"
DEFAULT_KEY_ALIAS="baby-diary-upload"
ANDROID_KEY_SIZE="${ANDROID_KEY_SIZE:-4096}"
ANDROID_KEY_VALIDITY_DAYS="${ANDROID_KEY_VALIDITY_DAYS:-36500}"
ANDROID_KEY_DNAME="${ANDROID_KEY_DNAME:-CN=Baby Diary Android Upload,O=Baby Diary,C=CN}"

resolve_keytool() {
  local candidate="${ANDROID_KEYTOOL:-${ANDROID_JAVA_HOME:-/usr/lib/jvm/java-21-openjdk-amd64}/bin/keytool}"
  if [ -x "$candidate" ]; then
    printf '%s\n' "$candidate"
    return
  fi
  command -v keytool
}

certificate_sha256() {
  "$KEYTOOL" -exportcert \
    -keystore "$ANDROID_KEYSTORE_FILE" \
    -storepass "$ANDROID_KEYSTORE_PASSWORD" \
    -alias "$ANDROID_KEY_ALIAS" 2>/dev/null \
    | sha256sum \
    | cut -d' ' -f1
}

require_signing_values() {
  : "${ANDROID_KEYSTORE_FILE:?ANDROID_KEYSTORE_FILE is required}"
  : "${ANDROID_KEYSTORE_PASSWORD:?ANDROID_KEYSTORE_PASSWORD is required}"
  : "${ANDROID_KEY_ALIAS:?ANDROID_KEY_ALIAS is required}"
  : "${ANDROID_KEY_PASSWORD:?ANDROID_KEY_PASSWORD is required}"
}

KEYTOOL="$(resolve_keytool)"
command -v openssl >/dev/null

if [ -f "$ANDROID_SIGNING_ENV_FILE" ]; then
  set -a
  # shellcheck disable=SC1090
  . "$ANDROID_SIGNING_ENV_FILE"
  set +a
  require_signing_values

  if [ ! -s "$ANDROID_KEYSTORE_FILE" ]; then
    echo "Android signing environment exists but its keystore is missing" >&2
    exit 1
  fi

  chmod 700 "$(dirname "$ANDROID_KEYSTORE_FILE")"
  chmod 600 "$ANDROID_SIGNING_ENV_FILE" "$ANDROID_KEYSTORE_FILE"
  "$KEYTOOL" -list \
    -keystore "$ANDROID_KEYSTORE_FILE" \
    -storepass "$ANDROID_KEYSTORE_PASSWORD" \
    -alias "$ANDROID_KEY_ALIAS" >/dev/null 2>&1

  echo "Android signing key already initialized"
  echo "certificate sha256: $(certificate_sha256)"
  exit 0
fi

ANDROID_KEYSTORE_FILE="${ANDROID_KEYSTORE_FILE:-$DEFAULT_KEYSTORE_FILE}"
ANDROID_KEY_ALIAS="${ANDROID_KEY_ALIAS:-$DEFAULT_KEY_ALIAS}"

if [ -e "$ANDROID_KEYSTORE_FILE" ]; then
  echo "Android keystore exists without its environment file; refusing to overwrite it" >&2
  exit 1
fi

install -d -m 0700 "$ANDROID_SIGNING_DIR"
install -d -m 0700 "$(dirname "$ANDROID_KEYSTORE_FILE")"
if [ ! -d "$(dirname "$ANDROID_SIGNING_ENV_FILE")" ]; then
  install -d -m 0700 "$(dirname "$ANDROID_SIGNING_ENV_FILE")"
fi

umask 077
ANDROID_KEYSTORE_PASSWORD="$(openssl rand -hex 32)"
ANDROID_KEY_PASSWORD="$(openssl rand -hex 32)"
TEMP_DIR="$(mktemp -d "$ANDROID_SIGNING_DIR/.android-signing.XXXXXX")"
TEMP_KEYSTORE="$TEMP_DIR/baby-diary-upload.jks"
TEMP_ENV="$TEMP_DIR/android-signing.env"

cleanup() {
  rm -rf "$TEMP_DIR"
}
trap cleanup EXIT

"$KEYTOOL" -genkeypair \
  -keystore "$TEMP_KEYSTORE" \
  -storetype JKS \
  -storepass "$ANDROID_KEYSTORE_PASSWORD" \
  -keypass "$ANDROID_KEY_PASSWORD" \
  -alias "$ANDROID_KEY_ALIAS" \
  -keyalg RSA \
  -keysize "$ANDROID_KEY_SIZE" \
  -validity "$ANDROID_KEY_VALIDITY_DAYS" \
  -dname "$ANDROID_KEY_DNAME" \
  -noprompt >/dev/null 2>&1

printf 'ANDROID_KEYSTORE_FILE=%q\n' "$ANDROID_KEYSTORE_FILE" > "$TEMP_ENV"
printf 'ANDROID_KEYSTORE_PASSWORD=%q\n' "$ANDROID_KEYSTORE_PASSWORD" >> "$TEMP_ENV"
printf 'ANDROID_KEY_ALIAS=%q\n' "$ANDROID_KEY_ALIAS" >> "$TEMP_ENV"
printf 'ANDROID_KEY_PASSWORD=%q\n' "$ANDROID_KEY_PASSWORD" >> "$TEMP_ENV"

install -m 0600 "$TEMP_KEYSTORE" "$ANDROID_KEYSTORE_FILE"
install -m 0600 "$TEMP_ENV" "$ANDROID_SIGNING_ENV_FILE"

echo "Android signing key initialized"
echo "keystore: $ANDROID_KEYSTORE_FILE"
echo "environment: $ANDROID_SIGNING_ENV_FILE"
echo "certificate sha256: $(certificate_sha256)"
