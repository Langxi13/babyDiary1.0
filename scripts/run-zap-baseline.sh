#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="${PROJECT_ROOT:-$(cd "$SCRIPT_DIR/.." && pwd)}"
ENV_FILE="${STAGING_ENV_FILE:-$PROJECT_ROOT/.env.staging}"
REPORT_DIR="${ZAP_REPORT_DIR:-$PROJECT_ROOT/artifacts/zap}"

if [ -f "$ENV_FILE" ]; then
  set -a
  source "$ENV_FILE"
  set +a
fi

TARGET_URL="${ZAP_TARGET_URL:-http://127.0.0.1:${STAGING_WEB_PORT:-4173}}"
ZAP_IMAGE="${ZAP_IMAGE:-ghcr.io/zaproxy/zaproxy:2.16.1}"
mkdir -p "$REPORT_DIR"
chmod 0777 "$REPORT_DIR"
cp "$PROJECT_ROOT/test-support/zap/rules.tsv" "$REPORT_DIR/rules.tsv"

docker run --rm --network host \
  -v "$REPORT_DIR:/zap/wrk/:rw" \
  "$ZAP_IMAGE" \
  zap-baseline.py \
  -t "$TARGET_URL" \
  -m "${ZAP_SPIDER_MINUTES:-2}" \
  -c rules.tsv \
  -I \
  -J report.json \
  -r report.html \
  -w report.md
