#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

OUTPUT="$(AVAILABLE_BYTES_OVERRIDE=6000000000 MIN_FREE_BYTES=5000000000 \
  DISK_AUDIT_DETAILS=false "$ROOT/scripts/disk-audit.sh" --enforce)"
grep -q 'disk_available_bytes=6000000000' <<<"$OUTPUT"

if AVAILABLE_BYTES_OVERRIDE=4000000000 MIN_FREE_BYTES=5000000000 \
  DISK_AUDIT_DETAILS=false "$ROOT/scripts/disk-audit.sh" --enforce >/dev/null 2>&1; then
  echo "disk audit should block a deployment below the minimum" >&2
  exit 1
fi

if "$ROOT/scripts/disk-audit.sh" --invalid >/dev/null 2>&1; then
  echo "disk audit should reject an unknown mode" >&2
  exit 1
fi
