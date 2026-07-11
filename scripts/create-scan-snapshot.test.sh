#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT

REPO="$TMP_DIR/repo"
SNAPSHOT="$TMP_DIR/snapshot"
mkdir -p "$REPO"

git -C "$REPO" init -q
git -C "$REPO" config user.name test
git -C "$REPO" config user.email test@example.com

printf '%s\n' 'tracked' > "$REPO/tracked.txt"
printf '%s\n' 'deleted' > "$REPO/deleted.txt"
printf '%s\n' 'ignored.txt' > "$REPO/.gitignore"
git -C "$REPO" add tracked.txt deleted.txt .gitignore
git -C "$REPO" commit -qm baseline

rm "$REPO/deleted.txt"
printf '%s\n' 'untracked' > "$REPO/untracked file.txt"
printf '%s\n' 'ignored' > "$REPO/ignored.txt"

PROJECT_ROOT="$REPO" bash "$ROOT/scripts/create-scan-snapshot.sh" "$SNAPSHOT"

test -f "$SNAPSHOT/tracked.txt"
test -f "$SNAPSHOT/untracked file.txt"
test ! -e "$SNAPSHOT/deleted.txt"
test ! -e "$SNAPSHOT/ignored.txt"

echo "scan snapshot contains current tracked and untracked files only"
