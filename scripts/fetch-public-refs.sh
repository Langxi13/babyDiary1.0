#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="${PROJECT_ROOT:-$(cd "$SCRIPT_DIR/.." && pwd)}"
REMOTE="${PUBLIC_REFS_REMOTE:-origin}"

if [[ ! "$REMOTE" =~ ^[A-Za-z0-9._-]+$ ]]; then
  echo "public refs fetch: invalid remote name" >&2
  exit 1
fi

git -C "$PROJECT_ROOT" remote get-url "$REMOTE" >/dev/null
git -C "$PROJECT_ROOT" fetch --prune --no-recurse-submodules "$REMOTE" \
  "+refs/heads/*:refs/remotes/$REMOTE/*" \
  "+refs/tags/*:refs/tags/*" \
  "+refs/notes/*:refs/notes/*" \
  "+refs/pull/*/head:refs/remotes/$REMOTE/pull/*/head" \
  "+refs/pull/*/merge:refs/remotes/$REMOTE/pull/*/merge"

echo "public Git refs fetched from $REMOTE"
