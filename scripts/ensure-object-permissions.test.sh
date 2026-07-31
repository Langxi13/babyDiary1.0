#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT
OBJECT_DIR="$TMP_DIR/data/objects"
mkdir -p "$OBJECT_DIR"
touch "$OBJECT_DIR/private.bin"
chmod 2700 "$OBJECT_DIR"
chmod 0644 "$OBJECT_DIR/private.bin"

PROJECT_ROOT="$TMP_DIR" \
OBJECT_DIR="$OBJECT_DIR" \
SERVICE_USER="$(id -un)" \
SERVICE_GROUP="$(id -gn)" \
  "$ROOT/scripts/ensure-object-permissions.sh"

[ "$(stat -c '%a' "$OBJECT_DIR")" = "700" ]
[ "$(stat -c '%a' "$OBJECT_DIR/private.bin")" = "600" ]

if OBJECT_DIR=/tmp SERVICE_USER="$(id -un)" SERVICE_GROUP="$(id -gn)" \
  "$ROOT/scripts/ensure-object-permissions.sh" >/dev/null 2>&1; then
  echo "object permission setup should reject shared system directories" >&2
  exit 1
fi
