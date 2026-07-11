#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"

bash "$SCRIPT_DIR/verify-scripts.sh"
bash "$SCRIPT_DIR/verify-backend.sh"
bash "$SCRIPT_DIR/verify-frontend.sh"
