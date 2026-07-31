#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT
BACKUP_DIR="$TMP_DIR/backup"
PASSPHRASE_FILE="$TMP_DIR/passphrase"
mkdir -m 700 "$BACKUP_DIR"
printf 'test-only-backup-passphrase-that-is-long-enough\n' > "$PASSPHRASE_FILE"
chmod 600 "$PASSPHRASE_FILE"

encrypt() {
  gpg --batch --yes --quiet --pinentry-mode loopback --passphrase-file "$PASSPHRASE_FILE" \
    --symmetric --cipher-algo AES256 --compress-algo none --output "$1"
}
printf '%s\n' '-- MySQL dump' 'CREATE TABLE diary (diary_id int);' \
  | gzip | encrypt "$BACKUP_DIR/baby_diary.sql.gz.gpg"
mkdir "$TMP_DIR/objects"
printf 'media fixture\n' > "$TMP_DIR/objects/sample.bin"
tar -czf - -C "$TMP_DIR" objects | encrypt "$BACKUP_DIR/objects.tar.gz.gpg"
printf 'ANDROID_KEYSTORE_FILE=/private/android-upload.jks\n' \
  | encrypt "$BACKUP_DIR/android-signing.env.gpg"
printf 'test keystore\n' | encrypt "$BACKUP_DIR/android-upload.jks.gpg"
cat > "$BACKUP_DIR/backup.manifest" <<'EOF'
BACKUP_FORMAT=3
DATABASE=baby_diary
STORAGE_PROVIDER=LOCAL
OBJECT_FILE_COUNT=1
OBJECT_BYTES=14
EOF
chmod 600 "$BACKUP_DIR"/*
(cd "$BACKUP_DIR" && sha256sum ./*.gpg backup.manifest > SHA256SUMS)
chmod 600 "$BACKUP_DIR/SHA256SUMS"

OUTPUT="$(BACKUP_PASSPHRASE_FILE="$PASSPHRASE_FILE" "$ROOT/scripts/verify-backup.sh" "$BACKUP_DIR")"
grep -q "backup permissions ok" <<<"$OUTPUT"
grep -q "checksums ok" <<<"$OUTPUT"
grep -q "encrypted database dump ok" <<<"$OUTPUT"
grep -q "encrypted object archive ok" <<<"$OUTPUT"
grep -q "encrypted Android signing backup ok" <<<"$OUTPUT"

printf 'wrong-passphrase\n' > "$TMP_DIR/wrong"
if BACKUP_PASSPHRASE_FILE="$TMP_DIR/wrong" \
  "$ROOT/scripts/verify-backup.sh" "$BACKUP_DIR" >/dev/null 2>&1; then
  echo "verify-backup should reject the wrong passphrase" >&2
  exit 1
fi
printf 'tamper' >> "$BACKUP_DIR/baby_diary.sql.gz.gpg"
if BACKUP_PASSPHRASE_FILE="$PASSPHRASE_FILE" \
  "$ROOT/scripts/verify-backup.sh" "$BACKUP_DIR" >/dev/null 2>&1; then
  echo "verify-backup should reject a modified archive" >&2
  exit 1
fi

grep -q 'MYSQL_PWD="$MYSQL_PASSWORD" mysqldump' "$ROOT/scripts/backup.sh"
! grep -q -- '-p"$MYSQL_PASSWORD"' "$ROOT/scripts/backup.sh"
grep -q -- '--symmetric --cipher-algo AES256' "$ROOT/scripts/backup.sh"
