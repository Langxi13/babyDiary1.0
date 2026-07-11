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

K6_ENV=(
  -e "BASE_URL=$BASE_URL"
  -e "INVITATION_CODE=$INVITATION_CODE"
  -e "K6_USER_COUNT=${K6_USER_COUNT:-50}"
  -e "K6_PEAK_VUS=${K6_PEAK_VUS:-10}"
  -e "K6_RAMP_UP=${K6_RAMP_UP:-30s}"
  -e "K6_STEADY=${K6_STEADY:-3m}"
  -e "K6_RAMP_DOWN=${K6_RAMP_DOWN:-30s}"
)

if command -v k6 >/dev/null 2>&1; then
  BASE_URL="$BASE_URL" \
  INVITATION_CODE="$INVITATION_CODE" \
  K6_USER_COUNT="${K6_USER_COUNT:-50}" \
  K6_PEAK_VUS="${K6_PEAK_VUS:-10}" \
  K6_RAMP_UP="${K6_RAMP_UP:-30s}" \
  K6_STEADY="${K6_STEADY:-3m}" \
  K6_RAMP_DOWN="${K6_RAMP_DOWN:-30s}" \
    k6 run --summary-export "$RESULT_DIR/summary.json" "$PROJECT_ROOT/performance/k6/diary-load.js"
  exit 0
fi

chmod 0777 "$RESULT_DIR"
docker run --rm --network host \
  "${K6_ENV[@]}" \
  -v "$PROJECT_ROOT/performance/k6:/scripts:ro" \
  -v "$RESULT_DIR:/results" \
  grafana/k6:0.57.0 run --summary-export /results/summary.json /scripts/diary-load.js
