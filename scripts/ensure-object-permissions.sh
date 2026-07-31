#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="${PROJECT_ROOT:-$(cd "$SCRIPT_DIR/.." && pwd)}"
BACKEND_ENV_FILE="${BACKEND_ENV_FILE:-/etc/baby-diary/backend.env}"
if [ -f "$BACKEND_ENV_FILE" ]; then
  set -a
  # shellcheck disable=SC1090
  . "$BACKEND_ENV_FILE"
  set +a
fi

OBJECT_DIR="${OBJECT_DIR:-${DIARY_OBJECT_PATH:-$PROJECT_ROOT/data/objects}}"
SERVICE_USER="${SERVICE_USER:-baby-diary}"
SERVICE_GROUP="${SERVICE_GROUP:-$SERVICE_USER}"
object_path="$(readlink -m "$OBJECT_DIR")"
case "$object_path" in
  /|/tmp|/var|/home|/usr|/usr/local)
    echo "refusing to manage a shared system directory: $object_path" >&2
    exit 1
    ;;
esac
id "$SERVICE_USER" >/dev/null
getent group "$SERVICE_GROUP" >/dev/null
install -d -o "$SERVICE_USER" -g "$SERVICE_GROUP" -m 0700 "$OBJECT_DIR"
find "$OBJECT_DIR" -type d -exec chown "$SERVICE_USER:$SERVICE_GROUP" {} +
find "$OBJECT_DIR" -type d -exec chmod 0700 {} +
find "$OBJECT_DIR" -type d -exec chmod g-s {} +
find "$OBJECT_DIR" -type f -exec chown "$SERVICE_USER:$SERVICE_GROUP" {} +
find "$OBJECT_DIR" -type f -exec chmod 0600 {} +
echo "unified object directory restricted to $SERVICE_USER"
