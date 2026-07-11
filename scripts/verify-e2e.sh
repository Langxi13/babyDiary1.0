#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="${PROJECT_ROOT:-$(cd "$SCRIPT_DIR/.." && pwd)}"

command -v docker >/dev/null 2>&1 || { echo "Docker is required for E2E tests" >&2; exit 1; }
docker compose version >/dev/null

cd "$PROJECT_ROOT/frontend"
npm run test:e2e
