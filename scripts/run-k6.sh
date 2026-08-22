#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="${PROJECT_ROOT:-$(cd "$SCRIPT_DIR/.." && pwd)}"
ENV_FILE="${STAGING_ENV_FILE:-$PROJECT_ROOT/.env.staging}"
RESULT_DIR="${K6_RESULT_DIR:-$PROJECT_ROOT/artifacts/k6}"

if [ ! -f "$ENV_FILE" ]; then
  echo "Staging env file not found: $ENV_FILE" >&2
  exit 1
fi

set -a
source "$ENV_FILE"
set +a

mkdir -p "$RESULT_DIR"
BASE_URL="${BASE_URL:-http://127.0.0.1:${STAGING_WEB_PORT:-4173}}"
STAGING_PROJECT_NAME="${STAGING_PROJECT_NAME:-baby-diary-performance}" \
BASE_URL="$BASE_URL" \
K6_FIXTURE_USERNAME="${K6_FIXTURE_USERNAME:-performance-reader}" \
K6_FIXTURE_PASSWORD="${K6_FIXTURE_PASSWORD:-synthetic-load-password}" \
  "$SCRIPT_DIR/seed-performance-fixture.sh"

K6_ENV=(
  -e "BASE_URL=$BASE_URL"
  -e "K6_FIXTURE_USERNAME=${K6_FIXTURE_USERNAME:-performance-reader}"
  -e "K6_FIXTURE_PASSWORD=${K6_FIXTURE_PASSWORD:-synthetic-load-password}"
  -e "K6_PEAK_VUS=${K6_PEAK_VUS:-10}"
  -e "K6_RAMP_UP=${K6_RAMP_UP:-30s}"
  -e "K6_STEADY=${K6_STEADY:-3m}"
  -e "K6_RAMP_DOWN=${K6_RAMP_DOWN:-30s}"
)

COMPOSE=(docker compose --env-file "$ENV_FILE" -p "${STAGING_PROJECT_NAME:-baby-diary-performance}" -f "$PROJECT_ROOT/compose.staging.yaml")

collect_evidence() {
  "${COMPOSE[@]}" logs --no-color > "$RESULT_DIR/staging.log" 2>&1 || true
  "${COMPOSE[@]}" exec -T backend sh -c 'cat /sys/fs/cgroup/memory.peak 2>/dev/null || true' \
    > "$RESULT_DIR/backend-memory.peak" 2>/dev/null || true
  "${COMPOSE[@]}" exec -T redis redis-cli INFO memory \
    > "$RESULT_DIR/redis-info.txt" 2>/dev/null || true
  "${COMPOSE[@]}" exec -T mysql mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -e \
    "SHOW GLOBAL STATUS WHERE Variable_name IN ('Threads_connected','Questions','Slow_queries','Created_tmp_disk_tables');" \
    > "$RESULT_DIR/mysql-status.txt" 2>/dev/null || true
  "$SCRIPT_DIR/capture-performance-explain.sh" > "$RESULT_DIR/mysql-explain.txt" 2>&1 || true
}
trap collect_evidence EXIT

if command -v k6 >/dev/null 2>&1; then
  BASE_URL="$BASE_URL" \
  K6_FIXTURE_USERNAME="${K6_FIXTURE_USERNAME:-performance-reader}" \
  K6_FIXTURE_PASSWORD="${K6_FIXTURE_PASSWORD:-synthetic-load-password}" \
  K6_PEAK_VUS="${K6_PEAK_VUS:-10}" \
  K6_RAMP_UP="${K6_RAMP_UP:-30s}" \
  K6_STEADY="${K6_STEADY:-3m}" \
  K6_RAMP_DOWN="${K6_RAMP_DOWN:-30s}" \
    k6 run --summary-export "$RESULT_DIR/summary.json" "$PROJECT_ROOT/performance/k6/diary-load.js"
else
  chmod 0777 "$RESULT_DIR"
  docker run --rm --network host \
    "${K6_ENV[@]}" \
    -v "$PROJECT_ROOT/performance/k6:/scripts:ro" \
    -v "$RESULT_DIR:/results" \
    grafana/k6:0.57.0 run --summary-export /results/summary.json /scripts/diary-load.js
fi

if [ "${K6_REDIS_OUTAGE:-true}" = "true" ]; then
  "${COMPOSE[@]}" stop redis
  trap '"${COMPOSE[@]}" start redis >/dev/null; collect_evidence' EXIT
  if command -v k6 >/dev/null 2>&1; then
    BASE_URL="$BASE_URL" \
    K6_FIXTURE_USERNAME="${K6_FIXTURE_USERNAME:-performance-reader}" \
    K6_FIXTURE_PASSWORD="${K6_FIXTURE_PASSWORD:-synthetic-load-password}" \
    K6_PEAK_VUS="${K6_PEAK_VUS:-10}" \
      k6 run --summary-export "$RESULT_DIR/redis-outage-summary.json" "$PROJECT_ROOT/performance/k6/redis-outage.js"
  else
    docker run --rm --network host \
      -e "BASE_URL=$BASE_URL" \
      -e "K6_FIXTURE_USERNAME=${K6_FIXTURE_USERNAME:-performance-reader}" \
      -e "K6_FIXTURE_PASSWORD=${K6_FIXTURE_PASSWORD:-synthetic-load-password}" \
      -e "K6_PEAK_VUS=${K6_PEAK_VUS:-10}" \
      -v "$PROJECT_ROOT/performance/k6:/scripts:ro" \
      -v "$RESULT_DIR:/results" \
      grafana/k6:0.57.0 run --summary-export /results/redis-outage-summary.json /scripts/redis-outage.js
  fi
  "${COMPOSE[@]}" start redis >/dev/null
  trap collect_evidence EXIT
fi
