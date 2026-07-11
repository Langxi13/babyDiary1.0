#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="${PROJECT_ROOT:-$(cd "$SCRIPT_DIR/.." && pwd)}"
ENV_FILE="${STAGING_ENV_FILE:-$PROJECT_ROOT/.env.staging}"
PROJECT_NAME="${STAGING_PROJECT_NAME:-baby-diary-staging}"

docker compose --env-file "$ENV_FILE" -p "$PROJECT_NAME" -f "$PROJECT_ROOT/compose.staging.yaml" down --remove-orphans
