#!/usr/bin/env bash
set -euo pipefail

: "${AI_SMOKE_TOKEN:?set AI_SMOKE_TOKEN to a staging administrator access token}"
BASE_URL="${AI_SMOKE_BASE_URL:-http://127.0.0.1:11002}"

response="$(curl --fail --silent \
  -X POST \
  -H "Authorization: Bearer $AI_SMOKE_TOKEN" \
  "$BASE_URL/api/ai/config/test")"

grep -q '"code":200' <<<"$response"

if [ -n "${AI_REPORT_PERIOD:-}" ]; then
  report_response="$(curl --fail --silent \
    -X POST \
    -H 'Content-Type: application/json' \
    -H "Authorization: Bearer $AI_SMOKE_TOKEN" \
    -d "{\"type\":\"${AI_REPORT_TYPE:-WEEKLY}\",\"period\":\"$AI_REPORT_PERIOD\"}" \
    "$BASE_URL/api/ai/reports/generate")"
  grep -q '"code":200' <<<"$report_response"
fi

echo "AI provider smoke test passed"
