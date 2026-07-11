#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"

mapfile -d '' TEST_SCRIPTS < <(find "$SCRIPT_DIR" -maxdepth 1 -type f -name '*.test.sh' -print0 | sort -z)

for test_script in "${TEST_SCRIPTS[@]}"; do
  bash "$test_script"
done
