#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT
ENV_FILE="$TMP_DIR/backend.env"
MYSQL_FAKE="$TMP_DIR/mysql"
SQL_LOG="$TMP_DIR/sql.log"

cat > "$ENV_FILE" <<'ENV'
DB_URL=jdbc:mysql://127.0.0.1:3307/baby_diary?useUnicode=true
DB_USERNAME=tester
DB_PASSWORD=test-secret
ENV

cat > "$MYSQL_FAKE" <<'SH'
#!/usr/bin/env bash
set -euo pipefail
for argument in "$@"; do
  case "$argument" in
    --execute=*)
      sql="${argument#--execute=}"
      ;;
  esac
done
printf '%s\n' "$sql" >> "$SQL_LOG"
if [[ "$sql" == SELECT* ]]; then
  printf '2\n'
fi
SH
chmod +x "$MYSQL_FAKE" "$ROOT/scripts/requeue-media-processing.sh"

REPORT="$(BACKEND_ENV_FILE="$ENV_FILE" MYSQL_BIN="$MYSQL_FAKE" SQL_LOG="$SQL_LOG" \
  "$ROOT/scripts/requeue-media-processing.sh" --report)"
grep -q 'retryable_media_jobs=2' <<<"$REPORT"
test "$(wc -l < "$SQL_LOG")" -eq 1

APPLY="$(BACKEND_ENV_FILE="$ENV_FILE" MYSQL_BIN="$MYSQL_FAKE" SQL_LOG="$SQL_LOG" \
  AVAILABLE_BYTES_OVERRIDE=4000000000 "$ROOT/scripts/requeue-media-processing.sh" --apply)"
grep -q 'requeued_media_jobs=2' <<<"$APPLY"
grep -q "status='PENDING',attempt_count=0" "$SQL_LOG"

set +e
LOW_DISK="$(BACKEND_ENV_FILE="$ENV_FILE" MYSQL_BIN="$MYSQL_FAKE" SQL_LOG="$SQL_LOG" \
  AVAILABLE_BYTES_OVERRIDE=1000 "$ROOT/scripts/requeue-media-processing.sh" --apply 2>&1)"
STATUS=$?
set -e
test "$STATUS" -ne 0
grep -q 'media jobs require at least' <<<"$LOW_DISK"
