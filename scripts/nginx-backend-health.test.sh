#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
SNIPPET="$ROOT/config/nginx-backend-health.conf"
DEPLOY_SCRIPT="$ROOT/scripts/deploy.sh"

grep -q 'location = /actuator/health' "$SNIPPET"
grep -q 'proxy_pass http://127.0.0.1:10002/actuator/health;' "$SNIPPET"
grep -q 'baby-diary-security-headers.conf' "$SNIPPET"
grep -q 'config/nginx-backend-health.conf' "$DEPLOY_SCRIPT"
grep -q 'config/diary-backend-hardening.conf' "$DEPLOY_SCRIPT"

nginx_test_line="$(grep -n '^nginx -t$' "$DEPLOY_SCRIPT" | head -n 1 | cut -d: -f1)"
service_stop_line="$(grep -n '^systemctl stop ' "$DEPLOY_SCRIPT" | head -n 1 | cut -d: -f1)"

if [ -z "$nginx_test_line" ] || [ -z "$service_stop_line" ] || [ "$nginx_test_line" -ge "$service_stop_line" ]; then
  echo "deploy must validate Nginx before stopping the backend" >&2
  exit 1
fi

echo "backend health proxy and deployment ordering are tracked"
