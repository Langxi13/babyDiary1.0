#!/usr/bin/env bash
set -euo pipefail

: "${AI_SMOKE_TOKEN:?set AI_SMOKE_TOKEN to a staging administrator access token}"
: "${AI_SMOKE_SPACE_ID:?set AI_SMOKE_SPACE_ID to a synthetic staging space UUID}"
BASE_URL="${AI_SMOKE_BASE_URL:-http://127.0.0.1:11002}"

response="$(curl --fail --silent \
  -X POST \
  -H "Authorization: Bearer $AI_SMOKE_TOKEN" \
  "$BASE_URL/api/v3/admin/ai/test")"

grep -q '"result"' <<<"$response"

if [ -n "${AI_REPORT_PERIOD:-}" ]; then
  report_response="$(curl --fail --silent \
    -X POST \
    -H 'Content-Type: application/json' \
    -H "Authorization: Bearer $AI_SMOKE_TOKEN" \
    -d "{\"type\":\"${AI_REPORT_TYPE:-WEEKLY}\",\"period\":\"$AI_REPORT_PERIOD\"}" \
    "$BASE_URL/api/v3/spaces/$AI_SMOKE_SPACE_ID/ai-reports")"
  grep -q '"id"' <<<"$report_response"
fi

echo "AI provider smoke test passed"
