#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT

BACKUP_DIR="$TMP_DIR/backup"
PROJECT_DIR="$TMP_DIR/Baby-Diary"
mkdir -p "$BACKUP_DIR" "$PROJECT_DIR/document"

printf 'backup fixture\n' > "$PROJECT_DIR/document/sample.txt"
tar -czf "$BACKUP_DIR/project.tgz" -C "$TMP_DIR" Baby-Diary
cat > "$BACKUP_DIR/baby-diary.sql" <<'SQL'
-- MySQL dump
CREATE TABLE diary (diary_id int);
SQL
sha256sum "$BACKUP_DIR/project.tgz" "$BACKUP_DIR/baby-diary.sql" > "$BACKUP_DIR/SHA256SUMS"

OUTPUT="$("$ROOT/scripts/verify-backup.sh" "$BACKUP_DIR")"

grep -q "project archive ok" <<<"$OUTPUT"
grep -q "database dump ok" <<<"$OUTPUT"
grep -q "checksums ok" <<<"$OUTPUT"

rm "$BACKUP_DIR/baby-diary.sql"
if "$ROOT/scripts/verify-backup.sh" "$BACKUP_DIR" >/dev/null 2>&1; then
  echo "verify-backup should fail when the SQL dump is missing" >&2
  exit 1
fi
