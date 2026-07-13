#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT

SIGNING_DIR="$TMP_DIR/signing"
ENV_FILE="$TMP_DIR/android-signing.env"
FIRST_OUTPUT="$TMP_DIR/first.out"
SECOND_OUTPUT="$TMP_DIR/second.out"

ANDROID_SIGNING_DIR="$SIGNING_DIR" \
ANDROID_SIGNING_ENV_FILE="$ENV_FILE" \
ANDROID_KEY_SIZE=2048 \
ANDROID_KEY_VALIDITY_DAYS=3650 \
  "$ROOT/scripts/ensure-android-signing.sh" > "$FIRST_OUTPUT"

# shellcheck disable=SC1090
. "$ENV_FILE"

test "$(stat -c '%a' "$SIGNING_DIR")" = "700"
test "$(stat -c '%a' "$ENV_FILE")" = "600"
test "$(stat -c '%a' "$ANDROID_KEYSTORE_FILE")" = "600"
test -s "$ANDROID_KEYSTORE_FILE"
grep -Eq '^certificate sha256: [0-9a-f]{64}$' "$FIRST_OUTPUT"
! grep -Fq "$ANDROID_KEYSTORE_PASSWORD" "$FIRST_OUTPUT"
! grep -Fq "$ANDROID_KEY_PASSWORD" "$FIRST_OUTPUT"

KEYSTORE_HASH_BEFORE="$(sha256sum "$ANDROID_KEYSTORE_FILE" | cut -d' ' -f1)"
ANDROID_SIGNING_DIR="$SIGNING_DIR" \
ANDROID_SIGNING_ENV_FILE="$ENV_FILE" \
  "$ROOT/scripts/ensure-android-signing.sh" > "$SECOND_OUTPUT"
KEYSTORE_HASH_AFTER="$(sha256sum "$ANDROID_KEYSTORE_FILE" | cut -d' ' -f1)"

test "$KEYSTORE_HASH_BEFORE" = "$KEYSTORE_HASH_AFTER"
grep -q '^Android signing key already initialized$' "$SECOND_OUTPUT"

CERT_FILE="$TMP_DIR/android-release-cert.sha256"
sed -n 's/^certificate sha256: //p' "$FIRST_OUTPUT" > "$CERT_FILE"
FAKE_BIN="$TMP_DIR/bin"
GH_CALL_LOG="$TMP_DIR/gh-calls.log"
mkdir -p "$FAKE_BIN"
cat > "$FAKE_BIN/gh" <<'SH'
#!/usr/bin/env bash
set -euo pipefail

if [ "${1:-}" = "auth" ] && [ "${2:-}" = "status" ]; then
  exit 0
fi

if [ "${1:-}" = "secret" ] && [ "${2:-}" = "set" ]; then
  byte_count="$(wc -c | tr -d ' ')"
  printf '%s %s\n' "${3:-}" "$byte_count" >> "$GH_CALL_LOG"
  exit 0
fi

exit 1
SH
chmod +x "$FAKE_BIN/gh"

PATH="$FAKE_BIN:$PATH" \
GH_CALL_LOG="$GH_CALL_LOG" \
ANDROID_SIGNING_ENV_FILE="$ENV_FILE" \
ANDROID_RELEASE_CERT_FILE="$CERT_FILE" \
  "$ROOT/scripts/sync-android-signing-secrets.sh" > "$TMP_DIR/sync.out"

test "$(wc -l < "$GH_CALL_LOG")" -eq 4
grep -Eq '^ANDROID_KEYSTORE_BASE64 [1-9][0-9]*$' "$GH_CALL_LOG"
grep -Eq '^ANDROID_KEYSTORE_PASSWORD [1-9][0-9]*$' "$GH_CALL_LOG"
grep -Eq '^ANDROID_KEY_ALIAS [1-9][0-9]*$' "$GH_CALL_LOG"
grep -Eq '^ANDROID_KEY_PASSWORD [1-9][0-9]*$' "$GH_CALL_LOG"

echo "Android signing initialization is private, idempotent, pinned, and GitHub-ready"
