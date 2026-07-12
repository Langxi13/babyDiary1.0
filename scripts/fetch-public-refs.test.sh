#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT

REMOTE="$TMP_DIR/remote.git"
SOURCE="$TMP_DIR/source"
CLONE="$TMP_DIR/clone"

git init --bare -q "$REMOTE"
git init -q "$SOURCE"
git -C "$SOURCE" config user.name test
git -C "$SOURCE" config user.email test@example.com
printf '%s\n' baseline > "$SOURCE/README.md"
git -C "$SOURCE" add README.md
git -C "$SOURCE" commit -qm baseline
commit="$(git -C "$SOURCE" rev-parse HEAD)"
git -C "$SOURCE" branch -M main
git -C "$SOURCE" remote add origin "$REMOTE"
git -C "$SOURCE" push -q origin main
git -C "$REMOTE" symbolic-ref HEAD refs/heads/main
git -C "$REMOTE" update-ref refs/pull/1/head "$commit"
git -C "$REMOTE" update-ref refs/pull/1/merge "$commit"
git -C "$SOURCE" notes --ref=review add -m 'synthetic review note' "$commit"
note_ref="$(git -C "$SOURCE" rev-parse refs/notes/review)"
git -C "$SOURCE" push -q origin refs/notes/review
git -C "$SOURCE" tag release-test
git -C "$SOURCE" push -q origin release-test

git clone -q "$REMOTE" "$CLONE"
PROJECT_ROOT="$CLONE" bash "$ROOT/scripts/fetch-public-refs.sh" >/dev/null 2>&1

test "$(git -C "$CLONE" rev-parse refs/remotes/origin/pull/1/head)" = "$commit"
test "$(git -C "$CLONE" rev-parse refs/remotes/origin/pull/1/merge)" = "$commit"
test "$(git -C "$CLONE" rev-parse refs/notes/review)" = "$note_ref"
test "$(git -C "$CLONE" rev-parse refs/tags/release-test)" = "$commit"

echo "public branch, tag, note, and pull request refs are fetched"
