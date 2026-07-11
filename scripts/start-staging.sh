#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="${PROJECT_ROOT:-$(cd "$SCRIPT_DIR/.." && pwd)}"
ENV_FILE="${STAGING_ENV_FILE:-$PROJECT_ROOT/.env.staging}"
PROJECT_NAME="${STAGING_PROJECT_NAME:-baby-diary-staging}"

if [ ! -f "$ENV_FILE" ]; then
  echo "Staging env file not found: $ENV_FILE" >&2
  echo "Create it from config/staging.env.example and replace every sample secret." >&2
  exit 1
fi

COMPOSE=(docker compose --env-file "$ENV_FILE" -p "$PROJECT_NAME" -f "$PROJECT_ROOT/compose.staging.yaml")
"${COMPOSE[@]}" up -d --build --wait

set -a
source "$ENV_FILE"
set +a

curl --fail --silent "http://127.0.0.1:${STAGING_API_PORT:-11002}/actuator/health" >/dev/null
curl --fail --silent "http://127.0.0.1:${STAGING_WEB_PORT:-4173}/" >/dev/null
echo "Staging is ready at http://127.0.0.1:${STAGING_WEB_PORT:-4173}"
