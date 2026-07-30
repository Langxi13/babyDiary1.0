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

directory_mode="$(stat -c '%a' "$BACKUP_DIR")"
if [ "$directory_mode" != "700" ]; then
  echo "backup directory mode should be 700, got $directory_mode" >&2
  exit 1
fi

while IFS= read -r -d '' file; do
  mode="$(stat -c '%a' "$file")"
  if [ "$mode" != "600" ]; then
    echo "backup file mode should be 600, got $mode: $file" >&2
    exit 1
  fi
done < <(find "$BACKUP_DIR" -maxdepth 1 -type f -print0)
echo "backup permissions ok"

CHECKSUMS="$BACKUP_DIR/SHA256SUMS"
if [ ! -s "$CHECKSUMS" ]; then
  echo "missing or empty checksum file: $CHECKSUMS" >&2
  exit 1
fi

verify_sql_stream() {
  if ! awk 'NR <= 200 && /^(-- MySQL dump|CREATE TABLE|INSERT INTO|\/\*!|DROP TABLE)/ { found=1 } END { exit found ? 0 : 1 }'; then
    echo "database dump does not look like a MySQL dump" >&2
    return 1
  fi
}

if [ -s "$BACKUP_DIR/backup.manifest" ]; then
  grep -qx 'BACKUP_FORMAT=2' "$BACKUP_DIR/backup.manifest" || {
    echo "unsupported backup format" >&2
    exit 1
  }

  SQL_ARCHIVES=("$BACKUP_DIR"/*.sql.gz)
  if [ ! -s "${SQL_ARCHIVES[0]}" ] || [ "${SQL_ARCHIVES[0]}" = "$BACKUP_DIR/*.sql.gz" ]; then
    echo "missing compressed database dump in $BACKUP_DIR" >&2
    exit 1
  fi
  gzip -t "${SQL_ARCHIVES[0]}"
  gzip -cd "${SQL_ARCHIVES[0]}" | verify_sql_stream
  echo "compressed database dump ok"

  provider="$(sed -n 's/^STORAGE_PROVIDER=//p' "$BACKUP_DIR/backup.manifest")"
  if [ "$provider" = "LOCAL" ]; then
    if [ ! -s "$BACKUP_DIR/objects.tar.gz" ]; then
      echo "missing local object archive" >&2
      exit 1
    fi
    tar -tzf "$BACKUP_DIR/objects.tar.gz" >/dev/null
    echo "object archive ok"
  fi
else
  PROJECT_ARCHIVE="$BACKUP_DIR/project.tgz"
  SQL_DUMPS=("$BACKUP_DIR"/*.sql)
  if [ ! -s "$PROJECT_ARCHIVE" ]; then
    echo "missing or empty project archive: $PROJECT_ARCHIVE" >&2
    exit 1
  fi
  if [ ! -s "${SQL_DUMPS[0]}" ] || [ "${SQL_DUMPS[0]}" = "$BACKUP_DIR/*.sql" ]; then
    echo "missing database dump in $BACKUP_DIR" >&2
    exit 1
  fi
  tar -tzf "$PROJECT_ARCHIVE" >/dev/null
  echo "legacy project archive ok"
  verify_sql_stream < "${SQL_DUMPS[0]}"
  echo "legacy database dump ok"
fi

(
  cd "$BACKUP_DIR"
  sha256sum -c "$(basename "$CHECKSUMS")" >/dev/null
)
echo "checksums ok"

if [ -f "$BACKUP_DIR/android-signing.env" ] || [ -f "$BACKUP_DIR/android-upload.jks" ]; then
  if [ ! -s "$BACKUP_DIR/android-signing.env" ] || [ ! -s "$BACKUP_DIR/android-upload.jks" ]; then
    echo "Android signing backup is incomplete" >&2
    exit 1
  fi
  echo "Android signing backup ok"
fi
