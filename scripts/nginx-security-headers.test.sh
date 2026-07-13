#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
SNIPPET="$ROOT/config/nginx-security-headers.conf"

for header in \
  X-Content-Type-Options \
  X-Frame-Options \
  Referrer-Policy \
  Permissions-Policy \
  Content-Security-Policy \
  Cross-Origin-Opener-Policy \
  Cross-Origin-Resource-Policy \
  Strict-Transport-Security; do
  grep -q "add_header $header" "$SNIPPET"
done

grep -q 'config/nginx-security-headers.conf' "$ROOT/scripts/deploy.sh"
grep -q 'config/nginx-resource-policy-map.conf' "$ROOT/scripts/deploy.sh"
grep -q '~\^/images/ "cross-origin"' "$ROOT/config/nginx-resource-policy-map.conf"
grep -q '~\^/api/v2/media/ "cross-origin"' "$ROOT/config/nginx-resource-policy-map.conf"
grep -q 'baby-diary-security-headers.conf' "$ROOT/scripts/runtime-governance-check.sh"
grep -q 'baby-diary-resource-policy-map.conf' "$ROOT/scripts/runtime-governance-check.sh"

echo "nginx security headers are tracked and deployed"
