#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT

REPO="$TMP_DIR/repo"
ALLOWLIST="$REPO/assets.sha256"
mkdir -p "$REPO/public"
git -C "$REPO" init -q
git -C "$REPO" config user.name test
git -C "$REPO" config user.email test@example.com

printf '%s\n' 'reviewed synthetic image version one' > "$REPO/public/fixture.png"
first_hash="$(sha256sum "$REPO/public/fixture.png" | cut -c1-64)"
printf '%s  %s\n' "$first_hash" 'public/fixture.png' > "$ALLOWLIST"
git -C "$REPO" add .
git -C "$REPO" commit -qm first-version

PROJECT_ROOT="$REPO" PRIVACY_ASSET_ALLOWLIST_FILE="$ALLOWLIST" \
  bash "$ROOT/scripts/public-asset-scan.sh" >/dev/null

printf '%s\n' 'reviewed synthetic image version two' > "$REPO/public/fixture.png"
if PROJECT_ROOT="$REPO" PRIVACY_SCAN_HISTORY=false PRIVACY_ASSET_ALLOWLIST_FILE="$ALLOWLIST" \
  bash "$ROOT/scripts/public-asset-scan.sh" >"$TMP_DIR/changed.out" 2>&1; then
  echo "asset scan should reject a changed reviewed asset" >&2
  exit 1
fi
grep -q 'changed and requires approval' "$TMP_DIR/changed.out"

second_hash="$(sha256sum "$REPO/public/fixture.png" | cut -c1-64)"
printf '%s  %s\n' "$second_hash" 'public/fixture.png' >> "$ALLOWLIST"
git -C "$REPO" add .
git -C "$REPO" commit -qm second-version
PROJECT_ROOT="$REPO" PRIVACY_ASSET_ALLOWLIST_FILE="$ALLOWLIST" \
  bash "$ROOT/scripts/public-asset-scan.sh" >/dev/null

printf '%s  %s\n' "$second_hash" 'public/fixture.png' > "$ALLOWLIST"
if PROJECT_ROOT="$REPO" PRIVACY_ASSET_ALLOWLIST_FILE="$ALLOWLIST" \
  bash "$ROOT/scripts/public-asset-scan.sh" >"$TMP_DIR/history.out" 2>&1; then
  echo "asset scan should reject an unreviewed historical version" >&2
  exit 1
fi
grep -q 'version exists in Git history' "$TMP_DIR/history.out"

echo "public asset scan enforces reviewed current and historical checksums"
