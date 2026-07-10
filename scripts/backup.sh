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

MYSQL_USER="${MYSQL_USER:-${DB_USERNAME:-root}}"
MYSQL_PASSWORD="${MYSQL_PASSWORD:-${DB_PASSWORD:-}}"
MYSQL_DATABASE="${MYSQL_DATABASE:-baby-diary}"
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

mysqldump --single-transaction --no-tablespaces -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" "$MYSQL_DATABASE" > "$TARGET/$MYSQL_DATABASE.sql"

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

sha256sum "$TARGET"/* > "$TARGET/SHA256SUMS"
bash "$PROJECT_ROOT/scripts/verify-backup.sh" "$TARGET" >/dev/null
echo "$TARGET"
