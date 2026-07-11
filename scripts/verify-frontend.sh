#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="${PROJECT_ROOT:-$(cd "$SCRIPT_DIR/.." && pwd)}"

cd "$PROJECT_ROOT/frontend"
mapfile -d '' FRONTEND_TESTS < <(find public src -type f -name '*.test.js' -print0 | sort -z)

node --test "${FRONTEND_TESTS[@]}"
npm run test:unit:coverage
npm run build
