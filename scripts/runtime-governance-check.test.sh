#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT

SERVICE_FILE="$TMP_DIR/diary-backend.service"
HARDENING_FILE="$TMP_DIR/10-baby-diary-hardening.conf"
ENV_FILE="$TMP_DIR/backend.env"
IMAGE_DIR="$TMP_DIR/images"
OBJECT_DIR="$TMP_DIR/objects"
HOST_TMP="$TMP_DIR/host-tmp"
NGINX_SITE_FILE="$TMP_DIR/diary.nginx"
NGINX_HEALTH_SNIPPET_FILE="$TMP_DIR/backend-health.nginx"
NGINX_RESOURCE_POLICY_MAP_FILE="$TMP_DIR/resource-policy-map.nginx"

cat > "$SERVICE_FILE" <<'SERVICE'
[Service]
User=baby-diary
Group=baby-diary
SERVICE

cat > "$HARDENING_FILE" <<'HARDENING'
[Service]
PrivateTmp=true
HARDENING

cat > "$NGINX_SITE_FILE" <<'NGINX'
server {
  include /etc/nginx/snippets/baby-diary-security-headers.conf;
  include /etc/nginx/snippets/baby-diary-backend-health.conf;
}
NGINX

cat > "$NGINX_HEALTH_SNIPPET_FILE" <<'NGINX'
location = /actuator/health {
  proxy_pass http://127.0.0.1:10002/actuator/health;
}
NGINX

cat > "$NGINX_RESOURCE_POLICY_MAP_FILE" <<'NGINX'
map $request_uri $baby_diary_resource_policy {
  default "same-origin";
  ~^/images/ "cross-origin";
  ~^/api/v2/media/ "cross-origin";
}
NGINX

cat > "$ENV_FILE" <<'ENV'
DB_URL='jdbc:mysql://127.0.0.1:3306/baby-diary?connectionTimeZone=%2B08:00&forceConnectionTimeZoneToSession=true&useSSL=false&allowPublicKeyRetrieval=true'
DB_USERNAME=baby_diary_app
DB_PASSWORD=test-database-password
DIARY_FILE_PATH=/tmp/test-images
DIARY_OBJECT_PATH=/tmp/test-objects
JWT_SECRET=aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
INVITATION_CODE=test-invitation
CORS_ALLOWED_ORIGINS=https://diary.example.com
AI_CONFIG_ENCRYPTION_KEY=bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb
INVITATION_CODE_ENCRYPTION_KEY=cccccccccccccccccccccccccccccccc
SERVER_ADDRESS=127.0.0.1
ENV

mkdir -p "$IMAGE_DIR"
mkdir -p "$OBJECT_DIR"
mkdir -p "$HOST_TMP"
chmod 600 "$ENV_FILE"
chmod 2750 "$TMP_DIR" "$IMAGE_DIR"
chmod 700 "$OBJECT_DIR"
chmod 1777 "$HOST_TMP"

OUTPUT="$(
  SYSTEMD_SERVICE_FILE="$SERVICE_FILE" \
  SYSTEMD_HARDENING_FILE="$HARDENING_FILE" \
  BACKEND_ENV_FILE="$ENV_FILE" \
  IMAGE_DIR="$IMAGE_DIR" \
  OBJECT_DIR="$OBJECT_DIR" \
  SERVICE_USER="baby-diary" \
  NGINX_GROUP="$(id -gn)" \
  DB_APP_USER="baby_diary_app" \
  NGINX_SITE_FILE="$NGINX_SITE_FILE" \
  NGINX_HEALTH_SNIPPET_FILE="$NGINX_HEALTH_SNIPPET_FILE" \
  NGINX_RESOURCE_POLICY_MAP_FILE="$NGINX_RESOURCE_POLICY_MAP_FILE" \
  TMP_ROOT="$HOST_TMP" \
  CHECK_OS_USER="false" \
  "$ROOT/scripts/runtime-governance-check.sh"
)"

grep -q "service user baby-diary" <<<"$OUTPUT"
grep -q "service stop uses systemd default" <<<"$OUTPUT"
grep -q "service private tmp enabled" <<<"$OUTPUT"
grep -q "host tmp mode 1777" <<<"$OUTPUT"
grep -q "backend.env mode 600" <<<"$OUTPUT"
grep -q "database user baby_diary_app" <<<"$OUTPUT"
grep -q "database timezone configured" <<<"$OUTPUT"
grep -q "security environment configured" <<<"$OUTPUT"
grep -q "backend bound to loopback" <<<"$OUTPUT"
grep -q "nginx native resource policy included" <<<"$OUTPUT"
grep -q "nginx backend health proxy included" <<<"$OUTPUT"
grep -q "image directory readable by nginx group" <<<"$OUTPUT"
grep -q "private object directory isolated" <<<"$OUTPUT"

printf '%s\n' 'SERVER_ADDRESS=0.0.0.0' >> "$ENV_FILE"
if SYSTEMD_SERVICE_FILE="$SERVICE_FILE" \
  SYSTEMD_HARDENING_FILE="$HARDENING_FILE" \
  BACKEND_ENV_FILE="$ENV_FILE" \
  IMAGE_DIR="$IMAGE_DIR" \
  OBJECT_DIR="$OBJECT_DIR" \
  SERVICE_USER="baby-diary" \
  NGINX_GROUP="$(id -gn)" \
  DB_APP_USER="baby_diary_app" \
  NGINX_SITE_FILE="$NGINX_SITE_FILE" \
  NGINX_HEALTH_SNIPPET_FILE="$NGINX_HEALTH_SNIPPET_FILE" \
  NGINX_RESOURCE_POLICY_MAP_FILE="$NGINX_RESOURCE_POLICY_MAP_FILE" \
  TMP_ROOT="$HOST_TMP" \
  CHECK_OS_USER="false" \
  "$ROOT/scripts/runtime-governance-check.sh" >/dev/null 2>&1; then
  echo "runtime governance should reject a public production backend bind" >&2
  exit 1
fi

sed -i 's/PrivateTmp=true/PrivateTmp=false/' "$HARDENING_FILE"
if SYSTEMD_SERVICE_FILE="$SERVICE_FILE" \
  SYSTEMD_HARDENING_FILE="$HARDENING_FILE" \
  BACKEND_ENV_FILE="$ENV_FILE" \
  IMAGE_DIR="$IMAGE_DIR" \
  OBJECT_DIR="$OBJECT_DIR" \
  SERVICE_USER="baby-diary" \
  NGINX_GROUP="$(id -gn)" \
  DB_APP_USER="baby_diary_app" \
  NGINX_SITE_FILE="$NGINX_SITE_FILE" \
  NGINX_HEALTH_SNIPPET_FILE="$NGINX_HEALTH_SNIPPET_FILE" \
  NGINX_RESOURCE_POLICY_MAP_FILE="$NGINX_RESOURCE_POLICY_MAP_FILE" \
  TMP_ROOT="$HOST_TMP" \
  CHECK_OS_USER="false" \
  "$ROOT/scripts/runtime-governance-check.sh" >/dev/null 2>&1; then
  echo "runtime governance should require PrivateTmp=true" >&2
  exit 1
fi
