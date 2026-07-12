#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"

while IFS= read -r action_ref; do
  case "$action_ref" in
    ./*|docker://*)
      continue
      ;;
  esac

  version="${action_ref##*@}"
  if [[ ! "$version" =~ ^[0-9a-f]{40}$ ]]; then
    echo "GitHub Action must be pinned to a full commit SHA: $action_ref" >&2
    exit 1
  fi
done < <(
  sed -nE 's/^[[:space:]]*-[[:space:]]*uses:[[:space:]]*([^[:space:]#]+).*/\1/p' \
    "$ROOT"/.github/workflows/*.yml
)

grep -q 'aquasecurity/trivy-action@ed142fd0673e97e23eac54620cfb913e5ce36c25' \
  "$ROOT/.github/workflows/ci.yml"

echo "GitHub Actions are pinned to verified commit SHAs"
