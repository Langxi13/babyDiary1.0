#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT

BACKUP_DIR="$TMP_DIR/backup"
OBJECT_DIR="$TMP_DIR/objects"
mkdir -m 700 "$BACKUP_DIR"
mkdir "$OBJECT_DIR"
printf 'media fixture\n' > "$OBJECT_DIR/sample.bin"
tar -czf "$BACKUP_DIR/objects.tar.gz" -C "$TMP_DIR" objects
printf '%s\n' '-- MySQL dump' 'CREATE TABLE diary (diary_id int);' | gzip > "$BACKUP_DIR/baby_diary_v3.sql.gz"
cat > "$BACKUP_DIR/backup.manifest" <<'EOF'
BACKUP_FORMAT=2
DATABASE=baby_diary_v3
STORAGE_PROVIDER=LOCAL
OBJECT_FILE_COUNT=1
OBJECT_BYTES=14
EOF
printf 'ANDROID_KEYSTORE_FILE=/etc/baby-diary/android-signing/baby-diary-upload.jks\n' > "$BACKUP_DIR/android-signing.env"
printf 'test keystore\n' > "$BACKUP_DIR/android-upload.jks"
chmod 600 "$BACKUP_DIR"/*
(
  cd "$BACKUP_DIR"
  sha256sum ./* > SHA256SUMS
)
chmod 600 "$BACKUP_DIR/SHA256SUMS"

OUTPUT="$("$ROOT/scripts/verify-backup.sh" "$BACKUP_DIR")"

grep -q "backup permissions ok" <<<"$OUTPUT"
grep -q "compressed database dump ok" <<<"$OUTPUT"
grep -q "object archive ok" <<<"$OUTPUT"
grep -q "checksums ok" <<<"$OUTPUT"
grep -q "Android signing backup ok" <<<"$OUTPUT"

chmod 644 "$BACKUP_DIR/backup.manifest"
if "$ROOT/scripts/verify-backup.sh" "$BACKUP_DIR" >/dev/null 2>&1; then
  echo "verify-backup should reject a world-readable backup file" >&2
  exit 1
fi
chmod 600 "$BACKUP_DIR/backup.manifest"

grep -q 'MYSQL_PWD="$MYSQL_PASSWORD" mysqldump' "$ROOT/scripts/backup.sh"
grep -q -- '--host="$MYSQL_HOST" --port="$MYSQL_PORT"' "$ROOT/scripts/backup.sh"
if grep -q -- '-p"$MYSQL_PASSWORD"' "$ROOT/scripts/backup.sh"; then
  echo "backup should not expose the database password in process arguments" >&2
  exit 1
fi

grep -q 'objects.tar.gz' "$ROOT/scripts/backup.sh"
grep -q 'backup.manifest' "$ROOT/scripts/backup.sh"
grep -q 'android-signing.env' "$ROOT/scripts/backup.sh"
grep -q 'android-upload.jks' "$ROOT/scripts/backup.sh"
