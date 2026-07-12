#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT

REPO="$TMP_DIR/repo"
mkdir -p "$REPO/config"
: > "$REPO/config/public-asset-allowlist.sha256"
git -C "$REPO" init -q
git -C "$REPO" config user.name test
git -C "$REPO" config user.email test@example.com

printf '%s\n' 'PUBLIC_URL=https://diary.example.com' 'MAIL_FROM=admin@example.com' > "$REPO/config/public.env.example"
printf '%s\n' 'loopback=127.0.0.1' > "$REPO/README.md"
external_host="outside-machine.$(printf '%s' 'cn')"
printf 'PUBLIC_URL=https://%s\n' "$external_host" > "$TMP_DIR/private-local-file"
ln -s "$TMP_DIR/private-local-file" "$REPO/config/local-link"
git -C "$REPO" add .
git -C "$REPO" commit -qm baseline

PROJECT_ROOT="$REPO" \
PRIVACY_ALLOWLIST_FILE="$ROOT/config/privacy-host-allowlist.txt" \
  bash "$ROOT/scripts/privacy-scan.sh" >/dev/null

private_host="family-journal.$(printf '%s' 'cn')"
printf 'PUBLIC_URL=https://%s\n' "$private_host" > "$REPO/config/runtime.txt"
if PROJECT_ROOT="$REPO" PRIVACY_SCAN_HISTORY=false \
  PRIVACY_ALLOWLIST_FILE="$ROOT/config/privacy-host-allowlist.txt" \
  bash "$ROOT/scripts/privacy-scan.sh" >"$TMP_DIR/current.out" 2>&1; then
  echo "privacy scan should reject an unapproved current host" >&2
  exit 1
fi
grep -q 'unapproved URL host' "$TMP_DIR/current.out"
if grep -q "$private_host" "$TMP_DIR/current.out"; then
  echo "privacy scan must not echo a detected private value" >&2
  exit 1
fi

git -C "$REPO" add config/runtime.txt
git -C "$REPO" commit -qm add-private-host
rm "$REPO/config/runtime.txt"
git -C "$REPO" add -u
git -C "$REPO" commit -qm remove-private-host
private_metadata_email="release@family-journal.$(printf '%s' 'cn')"
git -C "$REPO" tag -a private-metadata -m "Contact $private_metadata_email"

if PROJECT_ROOT="$REPO" \
  PRIVACY_ALLOWLIST_FILE="$ROOT/config/privacy-host-allowlist.txt" \
  bash "$ROOT/scripts/privacy-scan.sh" >"$TMP_DIR/history.out" 2>&1; then
  echo "privacy scan should reject a value retained in Git history" >&2
  exit 1
fi
grep -q 'git-history' "$TMP_DIR/history.out"
grep -q 'git-metadata' "$TMP_DIR/history.out"
if grep -qF "$private_metadata_email" "$TMP_DIR/history.out"; then
  echo "privacy scan must redact detected Git metadata values" >&2
  exit 1
fi

private_email="owner@family-journal.$(printf '%s' 'cn')"
public_ip="8.8.4.$(printf '%s' '4')"
server_path="/usr/local/$(printf '%s' 'Web-Project/private-instance')"
phone_number="138$(printf '%s' '00138000')"
printf 'MAIL_FROM=%s\nPUBLIC_IP=%s\nSERVER_PATH=%s\nPHONE=%s\n' \
  "$private_email" "$public_ip" "$server_path" "$phone_number" > "$REPO/config/private-values.txt"
touch "$REPO/.env"

if PROJECT_ROOT="$REPO" PRIVACY_SCAN_HISTORY=false \
  PRIVACY_ALLOWLIST_FILE="$ROOT/config/privacy-host-allowlist.txt" \
  bash "$ROOT/scripts/privacy-scan.sh" >"$TMP_DIR/categories.out" 2>&1; then
  echo "privacy scan should reject personal identifiers and sensitive paths" >&2
  exit 1
fi
grep -q 'unapproved email address' "$TMP_DIR/categories.out"
grep -q 'unapproved IP address' "$TMP_DIR/categories.out"
grep -q 'server-specific filesystem path' "$TMP_DIR/categories.out"
grep -q 'possible phone number' "$TMP_DIR/categories.out"
grep -q 'sensitive file path' "$TMP_DIR/categories.out"

for private_value in "$private_email" "$public_ip" "$server_path" "$phone_number"; do
  if grep -qF "$private_value" "$TMP_DIR/categories.out"; then
    echo "privacy scan must redact detected private values" >&2
    exit 1
  fi
done

grep -Fxq '.env.*' "$ROOT/.gitignore"
grep -Fxq 'data/objects/' "$ROOT/.gitignore"
grep -Fxq '.env.*' "$ROOT/.dockerignore"
grep -Fxq 'data' "$ROOT/.dockerignore"
grep -q -- '--no-ext-diff --no-textconv' "$ROOT/scripts/privacy-scan.sh"

echo "privacy scan rejects current and historical disclosures without echoing private values"
