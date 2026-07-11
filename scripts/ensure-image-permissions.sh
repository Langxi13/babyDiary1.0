#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="${PROJECT_ROOT:-$(cd "$SCRIPT_DIR/.." && pwd)}"
BACKEND_ENV_FILE="${BACKEND_ENV_FILE:-/etc/baby-diary/backend.env}"
if [ -f "$BACKEND_ENV_FILE" ]; then
  set -a
  . "$BACKEND_ENV_FILE"
  set +a
fi

IMAGE_DIR="${IMAGE_DIR:-${DIARY_FILE_PATH:-$PROJECT_ROOT/data/images}}"
OBJECT_DIR="${OBJECT_DIR:-${DIARY_OBJECT_PATH:-$PROJECT_ROOT/data/objects}}"
DATA_DIR="$(dirname "$IMAGE_DIR")"
SERVICE_USER="${SERVICE_USER:-baby-diary}"
SERVICE_GROUP="${SERVICE_GROUP:-$SERVICE_USER}"
NGINX_GROUP="${NGINX_GROUP:-www-data}"

if ! id "$SERVICE_USER" >/dev/null 2>&1; then
  echo "missing service user $SERVICE_USER" >&2
  exit 1
fi

if ! getent group "$NGINX_GROUP" >/dev/null 2>&1; then
  echo "missing nginx group $NGINX_GROUP" >&2
  exit 1
fi

if ! getent group "$SERVICE_GROUP" >/dev/null 2>&1; then
  echo "missing service group $SERVICE_GROUP" >&2
  exit 1
fi

image_path="$(readlink -m "$IMAGE_DIR")"
object_path="$(readlink -m "$OBJECT_DIR")"
data_path="$(readlink -m "$DATA_DIR")"
case "$data_path" in
  /|/tmp|/var|/home|/usr|/usr/local)
    echo "refusing to change permissions on shared system directory $data_path" >&2
    exit 1
    ;;
esac
case "$object_path/" in
  "$image_path/"*)
    echo "private object directory must not be inside the public image directory" >&2
    exit 1
    ;;
esac

install -d -o "$SERVICE_USER" -g "$NGINX_GROUP" -m 2750 "$DATA_DIR" "$IMAGE_DIR"
chown "$SERVICE_USER:$NGINX_GROUP" "$DATA_DIR" "$IMAGE_DIR"
chmod 2750 "$DATA_DIR" "$IMAGE_DIR"

find "$IMAGE_DIR" -type d -exec chown "$SERVICE_USER:$NGINX_GROUP" {} +
find "$IMAGE_DIR" -type d -exec chmod 2750 {} +
find "$IMAGE_DIR" -type f -exec chown "$SERVICE_USER:$NGINX_GROUP" {} +
find "$IMAGE_DIR" -type f -exec chmod 0640 {} +

install -d -o "$SERVICE_USER" -g "$SERVICE_GROUP" -m 0700 "$OBJECT_DIR"
find "$OBJECT_DIR" -type d -exec chown "$SERVICE_USER:$SERVICE_GROUP" {} +
find "$OBJECT_DIR" -type d -exec chmod 0700 {} +
find "$OBJECT_DIR" -type d -exec chmod g-s {} +
find "$OBJECT_DIR" -type f -exec chown "$SERVICE_USER:$SERVICE_GROUP" {} +
find "$OBJECT_DIR" -type f -exec chmod 0600 {} +

echo "image directory readable by $NGINX_GROUP and private objects restricted to $SERVICE_USER"
