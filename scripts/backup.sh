#!/usr/bin/env bash
set -euo pipefail

umask 077

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="${PROJECT_ROOT:-$(cd "$SCRIPT_DIR/.." && pwd)}"
BACKUP_ROOT="${BACKUP_ROOT:-$PROJECT_ROOT/backups}"

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
MYSQL_DATABASE="${MYSQL_DATABASE:-${JDBC_DATABASE:-baby_diary_v3}}"
STORAGE_PROVIDER="${OBJECT_STORAGE_PROVIDER:-local}"
OBJECT_ROOT="${STORAGE_LOCAL_ROOT:-${DIARY_OBJECT_PATH:-$PROJECT_ROOT/data/objects}}"
STAMP="$(date +%Y%m%d-%H%M%S)"
TARGET="$BACKUP_ROOT/$STAMP"
INCOMPLETE="$TARGET.incomplete"

if [ -z "$MYSQL_PASSWORD" ]; then
  echo "MYSQL_PASSWORD or DB_PASSWORD is required" >&2
  exit 1
fi

mkdir -p "$BACKUP_ROOT"
chmod 700 "$BACKUP_ROOT"
mkdir "$INCOMPLETE"
trap 'rm -rf "$INCOMPLETE"' EXIT

DATABASE_ARCHIVE="$INCOMPLETE/$MYSQL_DATABASE.sql.gz"
MYSQL_PWD="$MYSQL_PASSWORD" mysqldump --single-transaction --no-tablespaces \
  --protocol=TCP --host="$MYSQL_HOST" --port="$MYSQL_PORT" \
  -u"$MYSQL_USER" "$MYSQL_DATABASE" | gzip -9 > "$DATABASE_ARCHIVE"

OBJECT_FILE_COUNT=0
OBJECT_BYTES=0
if [ "${STORAGE_PROVIDER,,}" = "local" ]; then
  if [ ! -d "$OBJECT_ROOT" ]; then
    echo "local object directory not found: $OBJECT_ROOT" >&2
    exit 1
  fi
  OBJECT_ROOT="$(readlink -f "$OBJECT_ROOT")"
  OBJECT_FILE_COUNT="$(find "$OBJECT_ROOT" -type f | wc -l)"
  OBJECT_BYTES="$(find "$OBJECT_ROOT" -type f -printf '%s\n' | awk '{total += $1} END {print total + 0}')"
  tar -czf "$INCOMPLETE/objects.tar.gz" -C "$(dirname "$OBJECT_ROOT")" "$(basename "$OBJECT_ROOT")"
fi

copy_private_file() {
  local source="$1"
  local destination="$2"
  if [ -f "$source" ]; then
    install -m 0600 "$source" "$INCOMPLETE/$destination"
  fi
}

copy_private_file /etc/nginx/sites-available/diary nginx-diary.conf
copy_private_file /etc/systemd/system/diary-backend.service diary-backend.service
copy_private_file /etc/baby-diary/backend.env backend.env
copy_private_file /etc/baby-diary/android-update.env android-update.env

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
  copy_private_file "$ANDROID_SIGNING_ENV_FILE" android-signing.env
  copy_private_file "$ANDROID_KEYSTORE_FILE" android-upload.jks
fi

GIT_COMMIT="$(git -C "$PROJECT_ROOT" rev-parse HEAD 2>/dev/null || printf unknown)"
if [ -n "$(git -C "$PROJECT_ROOT" status --porcelain 2>/dev/null || true)" ]; then
  GIT_DIRTY=true
else
  GIT_DIRTY=false
fi
FLYWAY_VERSION="$(MYSQL_PWD="$MYSQL_PASSWORD" mysql --batch --skip-column-names \
  --protocol=TCP --host="$MYSQL_HOST" --port="$MYSQL_PORT" -u"$MYSQL_USER" "$MYSQL_DATABASE" \
  -e 'SELECT version FROM flyway_schema_history WHERE success=1 ORDER BY installed_rank DESC LIMIT 1' \
  2>/dev/null || printf unknown)"
DEPLOYED_JAR="$PROJECT_ROOT/deploy/backend/Baby-Diary-0.0.1-SNAPSHOT.jar"
DEPLOYED_JAR_SHA256="$(test -f "$DEPLOYED_JAR" && sha256sum "$DEPLOYED_JAR" | awk '{print $1}' || printf unavailable)"

cat > "$INCOMPLETE/backup.manifest" <<EOF
BACKUP_FORMAT=2
CREATED_AT=$(date --iso-8601=seconds)
GIT_COMMIT=$GIT_COMMIT
GIT_DIRTY=$GIT_DIRTY
FLYWAY_VERSION=$FLYWAY_VERSION
DATABASE=$MYSQL_DATABASE
STORAGE_PROVIDER=${STORAGE_PROVIDER^^}
OBJECT_FILE_COUNT=$OBJECT_FILE_COUNT
OBJECT_BYTES=$OBJECT_BYTES
DEPLOYED_JAR_SHA256=$DEPLOYED_JAR_SHA256
EOF

chmod 600 "$INCOMPLETE"/*
(
  cd "$INCOMPLETE"
  sha256sum ./* > SHA256SUMS
)
chmod 600 "$INCOMPLETE/SHA256SUMS"

bash "$PROJECT_ROOT/scripts/verify-backup.sh" "$INCOMPLETE" >/dev/null
mv "$INCOMPLETE" "$TARGET"
trap - EXIT
echo "$TARGET"
