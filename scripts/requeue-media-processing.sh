#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="${PROJECT_ROOT:-$(cd "$SCRIPT_DIR/.." && pwd)}"
BACKEND_ENV_FILE="${BACKEND_ENV_FILE:-/etc/baby-diary/backend.env}"
MYSQL_BIN="${MYSQL_BIN:-mysql}"
MIN_FREE_BYTES="${MEDIA_REQUEUE_MIN_FREE_BYTES:-3221225472}"
MODE="${1:---report}"

if [ "$MODE" != "--report" ] && [ "$MODE" != "--apply" ]; then
  echo "usage: scripts/requeue-media-processing.sh [--report|--apply]" >&2
  exit 2
fi

if [ ! -r "$BACKEND_ENV_FILE" ]; then
  echo "backend environment is not readable: $BACKEND_ENV_FILE" >&2
  exit 1
fi

set -a
# shellcheck disable=SC1090
. "$BACKEND_ENV_FILE"
set +a

: "${DB_URL:?DB_URL is required}"
: "${DB_USERNAME:?DB_USERNAME is required}"
: "${DB_PASSWORD:?DB_PASSWORD is required}"

connection="${DB_URL#jdbc:mysql://}"
if [ "$connection" = "$DB_URL" ] || [[ "$connection" != */* ]]; then
  echo "DB_URL must use jdbc:mysql://host[:port]/database" >&2
  exit 1
fi
authority="${connection%%/*}"
database_and_query="${connection#*/}"
database="${database_and_query%%\?*}"
host="${authority%%:*}"
port="${authority##*:}"
if [ "$host" = "$port" ]; then
  port=3306
fi
if [ -z "$host" ] || [ -z "$database" ] || ! [[ "$port" =~ ^[0-9]+$ ]]; then
  echo "DB_URL contains an unsupported MySQL address" >&2
  exit 1
fi

mysql_query() {
  MYSQL_PWD="$DB_PASSWORD" "$MYSQL_BIN" \
    --batch --skip-column-names \
    --host="$host" --port="$port" --user="$DB_USERNAME" "$database" \
    --execute="$1"
}

predicate="job_type='MEDIA_PROCESS' AND status='FAILED' AND last_error LIKE '%Insufficient temporary disk space for media processing%'"
count="$(mysql_query "SELECT COUNT(*) FROM background_job WHERE $predicate;")"
if ! [[ "$count" =~ ^[0-9]+$ ]]; then
  echo "unable to count retryable media jobs" >&2
  exit 1
fi
echo "retryable_media_jobs=$count"

if [ "$MODE" = "--report" ] || [ "$count" -eq 0 ]; then
  exit 0
fi

available="${AVAILABLE_BYTES_OVERRIDE:-$(df -PB1 "$PROJECT_ROOT" | awk 'NR==2 {print $4}')}"
if ! [[ "$available" =~ ^[0-9]+$ ]] || [ "$available" -lt "$MIN_FREE_BYTES" ]; then
  echo "media jobs require at least $MIN_FREE_BYTES available bytes; current=$available" >&2
  exit 1
fi

mysql_query "UPDATE background_job SET status='PENDING',attempt_count=0,available_at=UTC_TIMESTAMP(6),claimed_at=NULL,claimed_by=NULL,completed_at=NULL,last_error='磁盘空间恢复后由维护脚本重新排队',updated_at=UTC_TIMESTAMP(6) WHERE $predicate;"
echo "requeued_media_jobs=$count"
