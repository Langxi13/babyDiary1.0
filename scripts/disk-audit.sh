#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="${PROJECT_ROOT:-$(cd "$SCRIPT_DIR/.." && pwd)}"
MIN_FREE_BYTES="${MIN_FREE_BYTES:-5368709120}"
MODE="${1:---report}"

if [ "$MODE" != "--report" ] && [ "$MODE" != "--enforce" ]; then
  echo "usage: scripts/disk-audit.sh [--report|--enforce]" >&2
  exit 2
fi

AVAILABLE_BYTES="${AVAILABLE_BYTES_OVERRIDE:-$(df -PB1 "$PROJECT_ROOT" | awk 'NR==2 {print $4}')}"
if ! [[ "$AVAILABLE_BYTES" =~ ^[0-9]+$ ]]; then
  echo "unable to determine available disk space" >&2
  exit 1
fi

printf 'disk_available_bytes=%s\n' "$AVAILABLE_BYTES"
printf 'disk_required_bytes=%s\n' "$MIN_FREE_BYTES"
df -h "$PROJECT_ROOT" | tail -n 1

if [ "${DISK_AUDIT_DETAILS:-true}" = "true" ]; then
  du -sh \
    "$PROJECT_ROOT/backups" \
    "$PROJECT_ROOT/data" \
    "$PROJECT_ROOT/backend/target" \
    "$PROJECT_ROOT/frontend/node_modules" \
    "$PROJECT_ROOT/frontend/dist" \
    2>/dev/null || true
  timeout 20 docker system df 2>/dev/null || true
fi

if [ "$MODE" = "--enforce" ] && [ "$AVAILABLE_BYTES" -lt "$MIN_FREE_BYTES" ]; then
  echo "deployment requires at least $MIN_FREE_BYTES available bytes" >&2
  exit 1
fi
