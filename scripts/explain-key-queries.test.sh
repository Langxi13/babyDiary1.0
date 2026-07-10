#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TMP_DIR="$(mktemp -d)"
CALL_LOG="$TMP_DIR/mysql.log"
trap 'rm -rf "$TMP_DIR"' EXIT

cat > "$TMP_DIR/mysql" <<SH
#!/usr/bin/env bash
set -euo pipefail
cat >> "$CALL_LOG"
SH

chmod +x "$TMP_DIR/mysql"

PATH="$TMP_DIR:$PATH" \
BACKEND_ENV_FILE="$TMP_DIR/missing.env" \
DB_USERNAME=test \
DB_PASSWORD=test \
DB_NAME='baby-diary' \
"$ROOT/scripts/explain-key-queries.sh" >/dev/null

grep -q 'diary-list' "$CALL_LOG"
grep -q 'timeline' "$CALL_LOG"
grep -q 'album-list' "$CALL_LOG"
grep -q 'album-detail' "$CALL_LOG"
grep -q 'LIMIT 24 OFFSET 0' "$CALL_LOG"
grep -q 'favorite-photo-page' "$CALL_LOG"
grep -q 'ai-report-history' "$CALL_LOG"
