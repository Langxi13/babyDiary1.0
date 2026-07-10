#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT

IMAGE_DIR="$TMP_DIR/data/images"
mkdir -p "$IMAGE_DIR"
touch "$IMAGE_DIR/photo.jpg"
chmod 700 "$TMP_DIR/data" "$IMAGE_DIR"
chmod 600 "$IMAGE_DIR/photo.jpg"

SERVICE_USER="$(id -un)"
NGINX_GROUP="$(id -gn)"

PROJECT_ROOT="$TMP_DIR" \
IMAGE_DIR="$IMAGE_DIR" \
SERVICE_USER="$SERVICE_USER" \
NGINX_GROUP="$NGINX_GROUP" \
"$ROOT/scripts/ensure-image-permissions.sh"

DATA_MODE="$(stat -c '%a' "$TMP_DIR/data")"
IMAGE_MODE="$(stat -c '%a' "$IMAGE_DIR")"
FILE_MODE="$(stat -c '%a' "$IMAGE_DIR/photo.jpg")"
DATA_GROUP="$(stat -c '%G' "$TMP_DIR/data")"
IMAGE_GROUP="$(stat -c '%G' "$IMAGE_DIR")"
FILE_GROUP="$(stat -c '%G' "$IMAGE_DIR/photo.jpg")"

[ "$DATA_MODE" = "2750" ]
[ "$IMAGE_MODE" = "2750" ]
[ "$FILE_MODE" = "640" ]
[ "$DATA_GROUP" = "$NGINX_GROUP" ]
[ "$IMAGE_GROUP" = "$NGINX_GROUP" ]
[ "$FILE_GROUP" = "$NGINX_GROUP" ]
