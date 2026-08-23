#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PATH_SNIPPET="$ROOT/config/nginx-media-cache-path.conf"
LOCATION_SNIPPET="$ROOT/config/nginx-media-cache-location.conf"

grep -q 'keys_zone=baby_diary_media_cache:4m' "$PATH_SNIPPET"
grep -q 'max_size=128m' "$PATH_SNIPPET"
grep -q 'location \^~ /api/v3/public/media/' "$LOCATION_SNIPPET"
grep -q 'proxy_cache_key.*request_uri.*http_range' "$LOCATION_SNIPPET"
grep -q 'proxy_cache_convert_head off' "$LOCATION_SNIPPET"
grep -q 'proxy_cache_valid 200 206 5m' "$LOCATION_SNIPPET"
grep -q 'config/nginx-media-cache-path.conf' "$ROOT/scripts/deploy.sh"
grep -q 'config/nginx-media-cache-location.conf' "$ROOT/scripts/deploy.sh"

echo "signed media cache configuration passed"
