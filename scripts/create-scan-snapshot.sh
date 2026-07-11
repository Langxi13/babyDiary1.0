#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="${PROJECT_ROOT:-$(cd "$SCRIPT_DIR/.." && pwd)}"
OUTPUT_DIR="${1:?usage: create-scan-snapshot.sh OUTPUT_DIR}"

mkdir -p "$OUTPUT_DIR"
cd "$PROJECT_ROOT"

git ls-files -z --cached --others --exclude-standard \
  | while IFS= read -r -d '' path; do
      if [ -e "$path" ] || [ -L "$path" ]; then
        printf '%s\0' "$path"
      fi
    done \
  | tar --null -T - -cf - \
  | tar -xf - -C "$OUTPUT_DIR"
