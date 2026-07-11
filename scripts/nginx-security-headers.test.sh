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
grep -q 'baby-diary-security-headers.conf' "$ROOT/scripts/runtime-governance-check.sh"

echo "nginx security headers are tracked and deployed"
