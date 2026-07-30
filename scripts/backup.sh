#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="${PROJECT_ROOT:-$(cd "$SCRIPT_DIR/.." && pwd)}"
BACKUP_ROOT="${BACKUP_ROOT:-$PROJECT_ROOT/backups}"
PROJECT_NAME="$(basename "$PROJECT_ROOT")"

if [ -f /etc/baby-diary/backend.env ]; then
  set -a
  . /etc/baby-diary/backend.env
  set +a
fi

DATABASE_URL="${V3_DB_URL:-${DB_URL:-}}"
JDBC_HOST=""
JDBC_PORT=""
JDBC_DATABASE=""
if [[ "$DATABASE_URL" =~ ^jdbc:mysql://([^/:?]+)(:([0-9]+))?/([^?]+) ]]; then
  JDBC_HOST="${BASH_REMATCH[1]}"
  JDBC_PORT="${BASH_REMATCH[3]}"
  JDBC_DATABASE="${BASH_REMATCH[4]}"
fi

MYSQL_HOST="${MYSQL_HOST:-${JDBC_HOST:-127.0.0.1}}"
MYSQL_PORT="${MYSQL_PORT:-${JDBC_PORT:-3306}}"
MYSQL_USER="${MYSQL_USER:-${V3_DB_USERNAME:-${DB_USERNAME:-root}}}"
MYSQL_PASSWORD="${MYSQL_PASSWORD:-${V3_DB_PASSWORD:-${DB_PASSWORD:-}}}"
MYSQL_DATABASE="${MYSQL_DATABASE:-${JDBC_DATABASE:-baby-diary}}"
STAMP="$(date +%Y%m%d-%H%M%S)"
TARGET="$BACKUP_ROOT/$STAMP"

if [ -z "$MYSQL_PASSWORD" ]; then
  echo "MYSQL_PASSWORD or DB_PASSWORD is required" >&2
  exit 1
fi

mkdir -p "$TARGET"

tar \
  --exclude="$PROJECT_NAME/backups" \
  --exclude="$PROJECT_NAME/frontend/node_modules" \
  --exclude="$PROJECT_NAME/frontend/dist" \
  --exclude="$PROJECT_NAME/backend/target" \
  -czf "$TARGET/project.tgz" \
  -C "$(dirname "$PROJECT_ROOT")" "$PROJECT_NAME"

MYSQL_PWD="$MYSQL_PASSWORD" mysqldump --single-transaction --no-tablespaces \
  --protocol=TCP --host="$MYSQL_HOST" --port="$MYSQL_PORT" \
  -u"$MYSQL_USER" "$MYSQL_DATABASE" > "$TARGET/$MYSQL_DATABASE.sql"

if [ -f /etc/nginx/sites-available/diary ]; then
  cp /etc/nginx/sites-available/diary "$TARGET/nginx-diary.conf"
fi

if [ -f /etc/systemd/system/diary-backend.service ]; then
  cp /etc/systemd/system/diary-backend.service "$TARGET/diary-backend.service"
fi

if [ -f /etc/baby-diary/backend.env ]; then
  cp /etc/baby-diary/backend.env "$TARGET/backend.env"
  chmod 600 "$TARGET/backend.env"
fi

if [ -f /etc/baby-diary/android-update.env ]; then
  install -m 0600 /etc/baby-diary/android-update.env "$TARGET/android-update.env"
fi

ANDROID_SIGNING_ENV_FILE="${ANDROID_SIGNING_ENV_FILE:-/etc/baby-diary/android-signing.env}"
if [ -f "$ANDROID_SIGNING_ENV_FILE" ]; then
  set -a
  # shellcheck disable=SC1090
  . "$ANDROID_SIGNING_ENV_FILE"
  set +a
  if [ ! -s "${ANDROID_KEYSTORE_FILE:-}" ]; then
    echo "Android signing environment exists but its keystore is missing" >&2
    exit 1
  fi
  install -m 0600 "$ANDROID_SIGNING_ENV_FILE" "$TARGET/android-signing.env"
  install -m 0600 "$ANDROID_KEYSTORE_FILE" "$TARGET/android-upload.jks"
fi

sha256sum "$TARGET"/* > "$TARGET/SHA256SUMS"
bash "$PROJECT_ROOT/scripts/verify-backup.sh" "$TARGET" >/dev/null
echo "$TARGET"
