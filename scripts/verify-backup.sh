#!/usr/bin/env bash
set -euo pipefail

BACKUP_DIR="${1:-}"
BACKUP_PASSPHRASE_FILE="${BACKUP_PASSPHRASE_FILE:-/etc/baby-diary/backup-passphrase}"

if [ -z "$BACKUP_DIR" ]; then
  echo "usage: scripts/verify-backup.sh <backup-directory>" >&2
  exit 2
fi
if [ ! -d "$BACKUP_DIR" ]; then
  echo "backup directory not found: $BACKUP_DIR" >&2
  exit 1
fi
if [ ! -s "$BACKUP_PASSPHRASE_FILE" ]; then
  echo "backup passphrase is missing: $BACKUP_PASSPHRASE_FILE" >&2
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

MANIFEST="$BACKUP_DIR/backup.manifest"
CHECKSUMS="$BACKUP_DIR/SHA256SUMS"
grep -qx 'BACKUP_FORMAT=3' "$MANIFEST" || { echo "unsupported backup format" >&2; exit 1; }
[ -s "$CHECKSUMS" ] || { echo "missing checksums" >&2; exit 1; }

if find "$BACKUP_DIR" -maxdepth 1 -type f \
  ! -name '*.gpg' ! -name 'backup.manifest' ! -name 'SHA256SUMS' | grep -q .; then
  echo "backup contains an unexpected plaintext file" >&2
  exit 1
fi

(cd "$BACKUP_DIR" && sha256sum -c SHA256SUMS >/dev/null)
echo "checksums ok"

decrypt_stream() {
  gpg --batch --quiet --pinentry-mode loopback \
    --passphrase-file "$BACKUP_PASSPHRASE_FILE" --decrypt "$1"
}
verify_sql_stream() {
  awk 'NR <= 200 && /^(-- MySQL dump|CREATE TABLE|INSERT INTO|\/\*!|DROP TABLE)/ { found=1 } END { exit found ? 0 : 1 }'
}

shopt -s nullglob
SQL_ARCHIVES=("$BACKUP_DIR"/*.sql.gz.gpg)
if [ "${#SQL_ARCHIVES[@]}" -ne 1 ]; then
  echo "backup must contain exactly one encrypted database dump" >&2
  exit 1
fi
decrypt_stream "${SQL_ARCHIVES[0]}" | gzip -t
decrypt_stream "${SQL_ARCHIVES[0]}" | gzip -cd | verify_sql_stream
echo "encrypted database dump ok"

provider="$(sed -n 's/^STORAGE_PROVIDER=//p' "$MANIFEST")"
if [ "$provider" = "LOCAL" ]; then
  [ -s "$BACKUP_DIR/objects.tar.gz.gpg" ] || { echo "missing encrypted object archive" >&2; exit 1; }
  decrypt_stream "$BACKUP_DIR/objects.tar.gz.gpg" | tar -tzf - >/dev/null
  echo "encrypted object archive ok"
fi

if [ -f "$BACKUP_DIR/android-signing.env.gpg" ] || [ -f "$BACKUP_DIR/android-upload.jks.gpg" ]; then
  [ -s "$BACKUP_DIR/android-signing.env.gpg" ] && [ -s "$BACKUP_DIR/android-upload.jks.gpg" ] \
    || { echo "Android signing backup is incomplete" >&2; exit 1; }
  decrypt_stream "$BACKUP_DIR/android-signing.env.gpg" | grep -q '^ANDROID_'
  test "$(decrypt_stream "$BACKUP_DIR/android-upload.jks.gpg" | wc -c)" -gt 0
  echo "encrypted Android signing backup ok"
fi
