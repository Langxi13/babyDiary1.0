#!/usr/bin/env bash
set -euo pipefail

ENV_FILE="${BACKEND_ENV_FILE:-/etc/baby-diary/backend.env}"

mkdir -p "$(dirname "$ENV_FILE")"
touch "$ENV_FILE"
chmod 600 "$ENV_FILE"

if grep -q '^AI_CONFIG_ENCRYPTION_KEY=' "$ENV_FILE"; then
  exit 0
fi

if command -v openssl >/dev/null 2>&1; then
  KEY="$(openssl rand -base64 32)"
else
  KEY="$(head -c 32 /dev/urandom | base64)"
fi

{
  printf '\n'
  printf 'AI_CONFIG_ENCRYPTION_KEY=%s\n' "$KEY"
} >> "$ENV_FILE"
