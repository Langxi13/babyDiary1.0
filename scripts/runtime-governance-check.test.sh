#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT

SERVICE_FILE="$TMP_DIR/diary-backend.service"
ENV_FILE="$TMP_DIR/backend.env"
IMAGE_DIR="$TMP_DIR/images"

cat > "$SERVICE_FILE" <<'SERVICE'
[Service]
User=baby-diary
Group=baby-diary
SERVICE

cat > "$ENV_FILE" <<'ENV'
DB_URL='jdbc:mysql://127.0.0.1:3306/baby-diary?serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true'
DB_USERNAME=baby_diary_app
DB_PASSWORD=test-database-password
DIARY_FILE_PATH=/tmp/test-images
JWT_SECRET=aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
INVITATION_CODE=test-invitation
CORS_ALLOWED_ORIGINS=https://diary.example.com
AI_CONFIG_ENCRYPTION_KEY=bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb
ENV

mkdir -p "$IMAGE_DIR"
chmod 600 "$ENV_FILE"
chmod 2750 "$TMP_DIR" "$IMAGE_DIR"

OUTPUT="$(
  SYSTEMD_SERVICE_FILE="$SERVICE_FILE" \
  BACKEND_ENV_FILE="$ENV_FILE" \
  IMAGE_DIR="$IMAGE_DIR" \
  SERVICE_USER="baby-diary" \
  NGINX_GROUP="$(id -gn)" \
  DB_APP_USER="baby_diary_app" \
  CHECK_OS_USER="false" \
  "$ROOT/scripts/runtime-governance-check.sh"
)"

grep -q "service user baby-diary" <<<"$OUTPUT"
grep -q "service stop uses systemd default" <<<"$OUTPUT"
grep -q "backend.env mode 600" <<<"$OUTPUT"
grep -q "database user baby_diary_app" <<<"$OUTPUT"
grep -q "security environment configured" <<<"$OUTPUT"
grep -q "image directory readable by nginx group" <<<"$OUTPUT"
