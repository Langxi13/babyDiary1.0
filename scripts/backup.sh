#!/usr/bin/env bash
set -euo pipefail

umask 077

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="${PROJECT_ROOT:-$(cd "$SCRIPT_DIR/.." && pwd)}"
BACKUP_ROOT="${BACKUP_ROOT:-$PROJECT_ROOT/backups}"
BACKEND_ENV_FILE="${BACKEND_ENV_FILE:-/etc/baby-diary/backend.env}"
BACKUP_PASSPHRASE_FILE="${BACKUP_PASSPHRASE_FILE:-/etc/baby-diary/backup-passphrase}"

if [ -f "$BACKEND_ENV_FILE" ]; then
  set -a
  # shellcheck disable=SC1090
  . "$BACKEND_ENV_FILE"
  set +a
fi

if [ ! -s "$BACKUP_PASSPHRASE_FILE" ] || [ "$(stat -c '%a' "$BACKUP_PASSPHRASE_FILE")" != "600" ]; then
  echo "backup passphrase must be a non-empty mode 600 file: $BACKUP_PASSPHRASE_FILE" >&2
  exit 1
fi

DATABASE_URL="${DB_URL:-}"
if [[ ! "$DATABASE_URL" =~ ^jdbc:mysql://([^/:?]+)(:([0-9]+))?/([^?]+) ]]; then
  echo "DB_URL must be a valid MySQL JDBC URL" >&2
  exit 1
fi
MYSQL_HOST="${MYSQL_HOST:-${BASH_REMATCH[1]}}"
MYSQL_PORT="${MYSQL_PORT:-${BASH_REMATCH[3]:-3306}}"
MYSQL_DATABASE="${MYSQL_DATABASE:-${BASH_REMATCH[4]}}"
MYSQL_USER="${MYSQL_USER:-${DB_USERNAME:-}}"
MYSQL_PASSWORD="${MYSQL_PASSWORD:-${DB_PASSWORD:-}}"
STORAGE_PROVIDER="${OBJECT_STORAGE_PROVIDER:-local}"
OBJECT_ROOT="${STORAGE_LOCAL_ROOT:-${DIARY_OBJECT_PATH:-$PROJECT_ROOT/data/objects}}"
STAMP="$(date +%Y%m%d-%H%M%S)"
TARGET="$BACKUP_ROOT/$STAMP"
INCOMPLETE="$TARGET.incomplete"

if [ -z "$MYSQL_USER" ] || [ -z "$MYSQL_PASSWORD" ]; then
  echo "DB_USERNAME and DB_PASSWORD are required" >&2
  exit 1
fi

encrypt_stream() {
  local output="$1"
  gpg --batch --yes --quiet --pinentry-mode loopback \
    --passphrase-file "$BACKUP_PASSPHRASE_FILE" \
    --symmetric --cipher-algo AES256 --compress-algo none \
    --output "$output"
}

cleanup() {
  if [ -d "$INCOMPLETE" ]; then
    find "$INCOMPLETE" -type f -exec shred -u {} + 2>/dev/null || true
    find "$INCOMPLETE" -depth -type d -empty -delete 2>/dev/null || true
  fi
}
trap cleanup EXIT

mkdir -p "$BACKUP_ROOT"
chmod 700 "$BACKUP_ROOT"
mkdir -m 700 "$INCOMPLETE"

DATABASE_ARCHIVE="$INCOMPLETE/$MYSQL_DATABASE.sql.gz.gpg"
MYSQL_PWD="$MYSQL_PASSWORD" mysqldump --single-transaction --no-tablespaces \
  --protocol=TCP --host="$MYSQL_HOST" --port="$MYSQL_PORT" \
  -u"$MYSQL_USER" "$MYSQL_DATABASE" | gzip -9 | encrypt_stream "$DATABASE_ARCHIVE"

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
  tar -czf - -C "$(dirname "$OBJECT_ROOT")" "$(basename "$OBJECT_ROOT")" \
    | encrypt_stream "$INCOMPLETE/objects.tar.gz.gpg"
fi

PRIVATE_FILE_COUNT=0
encrypt_private_file() {
  local source="$1"
  local destination="$2"
  if [ -f "$source" ]; then
    encrypt_stream "$INCOMPLETE/$destination.gpg" < "$source"
    PRIVATE_FILE_COUNT=$((PRIVATE_FILE_COUNT + 1))
  fi
}

encrypt_private_file /etc/nginx/sites-available/diary nginx-diary.conf
encrypt_private_file /etc/systemd/system/diary-backend.service diary-backend.service
encrypt_private_file "$BACKEND_ENV_FILE" backend.env
encrypt_private_file /etc/baby-diary/android-update.env android-update.env

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
  encrypt_private_file "$ANDROID_SIGNING_ENV_FILE" android-signing.env
  encrypt_private_file "$ANDROID_KEYSTORE_FILE" android-upload.jks
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
DEPLOYED_JAR="$PROJECT_ROOT/deploy/backend/Baby-Diary.jar"
DEPLOYED_JAR_SHA256="$(test -f "$DEPLOYED_JAR" && sha256sum "$DEPLOYED_JAR" | awk '{print $1}' || printf unavailable)"

cat > "$INCOMPLETE/backup.manifest" <<EOF
BACKUP_FORMAT=3
CREATED_AT=$(date --iso-8601=seconds)
GIT_COMMIT=$GIT_COMMIT
GIT_DIRTY=$GIT_DIRTY
FLYWAY_VERSION=$FLYWAY_VERSION
DATABASE=$MYSQL_DATABASE
STORAGE_PROVIDER=${STORAGE_PROVIDER^^}
OBJECT_FILE_COUNT=$OBJECT_FILE_COUNT
OBJECT_BYTES=$OBJECT_BYTES
PRIVATE_FILE_COUNT=$PRIVATE_FILE_COUNT
DEPLOYED_JAR_SHA256=$DEPLOYED_JAR_SHA256
EOF

chmod 600 "$INCOMPLETE"/*
(
  cd "$INCOMPLETE"
  sha256sum ./*.gpg backup.manifest > SHA256SUMS
)
chmod 600 "$INCOMPLETE/SHA256SUMS"

BACKUP_PASSPHRASE_FILE="$BACKUP_PASSPHRASE_FILE" \
  bash "$PROJECT_ROOT/scripts/verify-backup.sh" "$INCOMPLETE" >/dev/null
mv "$INCOMPLETE" "$TARGET"
trap - EXIT
echo "$TARGET"
