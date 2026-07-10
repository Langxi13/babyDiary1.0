#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="${PROJECT_ROOT:-$(cd "$SCRIPT_DIR/.." && pwd)}"
IMAGE_DIR="${IMAGE_DIR:-$PROJECT_ROOT/data/images}"
DATA_DIR="$(dirname "$IMAGE_DIR")"
SERVICE_USER="${SERVICE_USER:-baby-diary}"
NGINX_GROUP="${NGINX_GROUP:-www-data}"

if ! id "$SERVICE_USER" >/dev/null 2>&1; then
  echo "missing service user $SERVICE_USER" >&2
  exit 1
fi

if ! getent group "$NGINX_GROUP" >/dev/null 2>&1; then
  echo "missing nginx group $NGINX_GROUP" >&2
  exit 1
fi

install -d -o "$SERVICE_USER" -g "$NGINX_GROUP" -m 2750 "$DATA_DIR" "$IMAGE_DIR"
chown "$SERVICE_USER:$NGINX_GROUP" "$DATA_DIR" "$IMAGE_DIR"
chmod 2750 "$DATA_DIR" "$IMAGE_DIR"

find "$IMAGE_DIR" -type d -exec chown "$SERVICE_USER:$NGINX_GROUP" {} +
find "$IMAGE_DIR" -type d -exec chmod 2750 {} +
find "$IMAGE_DIR" -type f -exec chown "$SERVICE_USER:$NGINX_GROUP" {} +
find "$IMAGE_DIR" -type f -exec chmod 0640 {} +

echo "image directory readable by $NGINX_GROUP and writable by $SERVICE_USER"
