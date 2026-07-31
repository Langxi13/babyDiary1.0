#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="${PROJECT_ROOT:-$(cd "$SCRIPT_DIR/.." && pwd)}"
SERVICE_USER="${SERVICE_USER:-baby-diary}"
SERVICE_GROUP="${SERVICE_GROUP:-$SERVICE_USER}"
DB_APP_USER="${DB_APP_USER:-baby_diary_app}"
SYSTEMD_SERVICE_FILE="${SYSTEMD_SERVICE_FILE:-/etc/systemd/system/diary-backend.service}"
SYSTEMD_HARDENING_FILE="${SYSTEMD_HARDENING_FILE:-/etc/systemd/system/diary-backend.service.d/10-baby-diary-hardening.conf}"
BACKEND_ENV_FILE="${BACKEND_ENV_FILE:-/etc/baby-diary/backend.env}"
OBJECT_DIR_OVERRIDE="${OBJECT_DIR:-}"
NGINX_USER="${NGINX_USER:-www-data}"
NGINX_GROUP="${NGINX_GROUP:-www-data}"
NGINX_SITE_FILE="${NGINX_SITE_FILE:-/etc/nginx/sites-available/diary}"
NGINX_HEALTH_SNIPPET_FILE="${NGINX_HEALTH_SNIPPET_FILE:-/etc/nginx/snippets/baby-diary-backend-health.conf}"
NGINX_RESOURCE_POLICY_MAP_FILE="${NGINX_RESOURCE_POLICY_MAP_FILE:-/etc/nginx/conf.d/baby-diary-resource-policy-map.conf}"
TMP_ROOT="${TMP_ROOT:-/tmp}"
CHECK_OS_USER="${CHECK_OS_USER:-true}"

require_file() {
  local path="$1"
  if [ ! -f "$path" ]; then
    echo "missing file $path" >&2
    return 1
  fi
}

require_file "$SYSTEMD_SERVICE_FILE"
require_file "$SYSTEMD_HARDENING_FILE"
require_file "$BACKEND_ENV_FILE"

grep -q "^User=$SERVICE_USER$" "$SYSTEMD_SERVICE_FILE"
grep -q "^Group=$SERVICE_GROUP$" "$SYSTEMD_SERVICE_FILE"
if grep -q '^ExecStop=/bin/kill ' "$SYSTEMD_SERVICE_FILE"; then
  echo "custom ExecStop kill should be removed; systemd default SIGTERM is safer for non-root services" >&2
  exit 1
fi
echo "service user $SERVICE_USER"
echo "service stop uses systemd default"

grep -q '^PrivateTmp=true$' "$SYSTEMD_HARDENING_FILE" || {
  echo "systemd hardening must enable PrivateTmp=true" >&2
  exit 1
}
echo "service private tmp enabled"

tmp_mode="$(stat -c '%a' "$TMP_ROOT")"
if [ "$tmp_mode" != "1777" ]; then
  echo "$TMP_ROOT mode should be 1777, got $tmp_mode" >&2
  exit 1
fi
echo "host tmp mode 1777"

env_mode="$(stat -c '%a' "$BACKEND_ENV_FILE")"
if [ "$env_mode" != "600" ]; then
  echo "backend.env mode should be 600, got $env_mode" >&2
  exit 1
fi
echo "backend.env mode 600"

set -a
. "$BACKEND_ENV_FILE"
set +a

OBJECT_DIR="${OBJECT_DIR_OVERRIDE:-${DIARY_OBJECT_PATH:-$PROJECT_ROOT/data/objects}}"

if [ "${DB_USERNAME:-}" != "$DB_APP_USER" ]; then
  echo "DB_USERNAME should be $DB_APP_USER, got ${DB_USERNAME:-<empty>}" >&2
  exit 1
fi
echo "database user $DB_APP_USER"

require_env_value() {
  local name="$1"
  local value="${!name:-}"
  if [ -z "$value" ]; then
    echo "$name is required" >&2
    exit 1
  fi
}

reject_placeholder_value() {
  local name="$1"
  local value="${!name:-}"
  case "$value" in
    replace-with-*|change-me-*|example|example-*)
      echo "$name still contains an example value" >&2
      exit 1
      ;;
  esac
}

require_env_value DB_URL
require_env_value DB_PASSWORD
require_env_value DIARY_OBJECT_PATH
require_env_value JWT_SECRET
require_env_value CORS_ALLOWED_ORIGINS
require_env_value AI_CONFIG_ENCRYPTION_KEY
require_env_value INVITATION_CODE_ENCRYPTION_KEY

reject_placeholder_value DB_PASSWORD
reject_placeholder_value JWT_SECRET
reject_placeholder_value INVITATION_CODE
reject_placeholder_value AI_CONFIG_ENCRYPTION_KEY
reject_placeholder_value INVITATION_CODE_ENCRYPTION_KEY

if [[ "$DB_URL" != *"/baby_diary?"* ]] \
  || [[ "$DB_URL" != *"connectionTimeZone=UTC"* ]] \
  || [[ "$DB_URL" != *"forceConnectionTimeZoneToSession=true"* ]] \
  || [[ "$DB_URL" != *"preserveInstants=true"* ]]; then
  echo "DB_URL must target baby_diary and enforce the UTC JDBC contract" >&2
  exit 1
fi
echo "database timezone configured"

for retired in V3_DB_URL V3_DB_USERNAME V3_DB_PASSWORD DIARY_FILE_PATH DIARY_PAGE_SIZE \
  JWT_EXPIRATION MEDIA_PROCESSING_ENABLED FFMPEG_BIN FFPROBE_BIN TESSERACT_BIN \
  APP_RELEASE_VERSION MYSQL_DATABASE; do
  if [ -n "${!retired:-}" ]; then
    echo "retired environment variable is still configured: $retired" >&2
    exit 1
  fi
done

if [ "${#JWT_SECRET}" -lt 32 ]; then
  echo "JWT_SECRET should contain at least 32 characters" >&2
  exit 1
fi
if [ "${#AI_CONFIG_ENCRYPTION_KEY}" -lt 32 ]; then
  echo "AI_CONFIG_ENCRYPTION_KEY should contain at least 32 characters" >&2
  exit 1
fi
if [ "${#INVITATION_CODE_ENCRYPTION_KEY}" -lt 32 ]; then
  echo "INVITATION_CODE_ENCRYPTION_KEY should contain at least 32 characters" >&2
  exit 1
fi
if tr ',' '\n' <<<"$CORS_ALLOWED_ORIGINS" | grep -Eq '^[[:space:]]*\*[[:space:]]*$'; then
  echo "CORS_ALLOWED_ORIGINS must not be '*' in production" >&2
  exit 1
fi
echo "security environment configured"

if [ "${SERVER_ADDRESS:-127.0.0.1}" != "127.0.0.1" ]; then
  echo "SERVER_ADDRESS must remain 127.0.0.1 behind the production reverse proxy" >&2
  exit 1
fi
echo "backend bound to loopback"

if [ -f "$NGINX_SITE_FILE" ]; then
  require_file "$NGINX_RESOURCE_POLICY_MAP_FILE"
  grep -q 'map $request_uri $baby_diary_resource_policy' "$NGINX_RESOURCE_POLICY_MAP_FILE" || {
    echo "nginx native resource policy map is missing" >&2
    exit 1
  }
  grep -q '~\^/api/v3/public/media/ "cross-origin"' "$NGINX_RESOURCE_POLICY_MAP_FILE" || {
    echo "nginx V3 media resource policy must allow native clients" >&2
    exit 1
  }
  echo "nginx native resource policy included"

  grep -q 'include /etc/nginx/snippets/baby-diary-security-headers.conf;' "$NGINX_SITE_FILE" || {
    echo "nginx site must include the Baby Diary security header snippet" >&2
    exit 1
  }
  echo "nginx security headers included"

  grep -q 'include /etc/nginx/snippets/baby-diary-backend-health.conf;' "$NGINX_SITE_FILE" || {
    echo "nginx site must include the Baby Diary backend health snippet" >&2
    exit 1
  }
  require_file "$NGINX_HEALTH_SNIPPET_FILE"
  grep -q 'location = /actuator/health' "$NGINX_HEALTH_SNIPPET_FILE" || {
    echo "nginx backend health location must use an exact match" >&2
    exit 1
  }
  grep -q 'proxy_pass http://127.0.0.1:10002/actuator/health;' "$NGINX_HEALTH_SNIPPET_FILE" || {
    echo "nginx backend health location must proxy to the loopback backend" >&2
    exit 1
  }
  echo "nginx backend health proxy included"
fi

if [ "${OBJECT_STORAGE_PROVIDER:-local}" = "local" ]; then
  require_env_value DIARY_OBJECT_PATH
  if [ ! -d "$OBJECT_DIR" ]; then
    echo "missing private object directory $OBJECT_DIR" >&2
    exit 1
  fi
  object_mode="$(stat -c '%a' "$OBJECT_DIR")"
  if [ "$object_mode" != "700" ]; then
    echo "private object directory mode should be 700, got $object_mode" >&2
    exit 1
  fi
  echo "private object directory isolated"
fi

if [ "$CHECK_OS_USER" = "true" ]; then
  id "$SERVICE_USER" >/dev/null
  if command -v runuser >/dev/null 2>&1; then
    runuser -u "$SERVICE_USER" -- test -w "$TMP_ROOT"
    id "$NGINX_USER" >/dev/null
    if [ "${OBJECT_STORAGE_PROVIDER:-local}" = "local" ]; then
      runuser -u "$SERVICE_USER" -- test -w "$OBJECT_DIR"
      if runuser -u "$NGINX_USER" -- test -r "$OBJECT_DIR"; then
        echo "nginx user must not read the private object directory" >&2
        exit 1
      fi
    fi
  else
    test -w "$OBJECT_DIR"
  fi
fi
echo "object directory writable"
