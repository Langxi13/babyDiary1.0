#!/usr/bin/env bash
set -euo pipefail

BACKUP_DIR="${1:-}"

if [ -z "$BACKUP_DIR" ]; then
  echo "usage: scripts/verify-backup.sh <backup-directory>" >&2
  exit 2
fi

if [ ! -d "$BACKUP_DIR" ]; then
  echo "backup directory not found: $BACKUP_DIR" >&2
  exit 1
fi

PROJECT_ARCHIVE="$BACKUP_DIR/project.tgz"
CHECKSUMS="$BACKUP_DIR/SHA256SUMS"
SQL_DUMPS=("$BACKUP_DIR"/*.sql)

if [ ! -s "$PROJECT_ARCHIVE" ]; then
  echo "missing or empty project archive: $PROJECT_ARCHIVE" >&2
  exit 1
fi

if [ ! -s "${SQL_DUMPS[0]}" ] || [ "${SQL_DUMPS[0]}" = "$BACKUP_DIR/*.sql" ]; then
  echo "missing database dump in $BACKUP_DIR" >&2
  exit 1
fi

if [ ! -s "$CHECKSUMS" ]; then
  echo "missing or empty checksum file: $CHECKSUMS" >&2
  exit 1
fi

tar -tzf "$PROJECT_ARCHIVE" >/dev/null
echo "project archive ok"

if ! grep -Eq '^(-- MySQL dump|CREATE TABLE|INSERT INTO|/\*!|DROP TABLE)' "${SQL_DUMPS[0]}"; then
  echo "database dump does not look like a MySQL dump: ${SQL_DUMPS[0]}" >&2
  exit 1
fi
echo "database dump ok"

(
  cd "$BACKUP_DIR"
  sha256sum -c "$(basename "$CHECKSUMS")" >/dev/null
)
echo "checksums ok"

if [ -f "$BACKUP_DIR/backend.env" ]; then
  mode="$(stat -c '%a' "$BACKUP_DIR/backend.env")"
  if [ "$mode" != "600" ]; then
    echo "backend.env mode should be 600, got $mode" >&2
    exit 1
  fi
  echo "environment file permissions ok"
fi

if [ -f "$BACKUP_DIR/android-update.env" ]; then
  mode="$(stat -c '%a' "$BACKUP_DIR/android-update.env")"
  if [ "$mode" != "600" ]; then
    echo "android-update.env mode should be 600, got $mode" >&2
    exit 1
  fi
  echo "Android update configuration permissions ok"
fi

if [ -f "$BACKUP_DIR/android-signing.env" ] || [ -f "$BACKUP_DIR/android-upload.jks" ]; then
  if [ ! -s "$BACKUP_DIR/android-signing.env" ] || [ ! -s "$BACKUP_DIR/android-upload.jks" ]; then
    echo "Android signing backup is incomplete" >&2
    exit 1
  fi
  for signing_file in android-signing.env android-upload.jks; do
    mode="$(stat -c '%a' "$BACKUP_DIR/$signing_file")"
    if [ "$mode" != "600" ]; then
      echo "$signing_file mode should be 600, got $mode" >&2
      exit 1
    fi
  done
  echo "Android signing backup permissions ok"
fi
