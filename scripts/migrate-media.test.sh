#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SCRIPT="$ROOT/scripts/migrate-media.sh"

grep -q 'MEDIA_MIGRATION_CONFIRM.*UNIFY_MEDIA_V15' "$SCRIPT"
grep -q 'app.media.migration-confirmation' "$SCRIPT"
grep -q 'umask 0077' "$SCRIPT"
grep -q 'runuser -u.*--preserve-environment' "$SCRIPT"

if MEDIA_MIGRATION_JAR="$ROOT/does-not-exist.jar" "$SCRIPT" dry-run >/dev/null 2>&1; then
  echo "migration should reject an unreadable jar" >&2
  exit 1
fi

if "$SCRIPT" unsupported >/dev/null 2>&1; then
  echo "migration should reject unsupported modes" >&2
  exit 1
fi
