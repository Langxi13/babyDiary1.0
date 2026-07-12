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
grep -q 'scripts/fetch-public-refs.sh' "$ROOT/.github/workflows/ci.yml"
grep -q 'scripts/privacy-scan.sh' "$ROOT/.github/workflows/ci.yml"
grep -q 'ghcr.io/gitleaks/gitleaks@sha256:cdbb7c955abce02001a9f6c9f602fb195b7fadc1e812065883f695d1eeaba854' \
  "$ROOT/.github/workflows/ci.yml"
grep -q -- '--log-opts=--all' "$ROOT/.github/workflows/ci.yml"
grep -q 'fetch-public-refs.sh' "$ROOT/scripts/security-scan.sh"
grep -q 'ghcr.io/gitleaks/gitleaks@sha256:cdbb7c955abce02001a9f6c9f602fb195b7fadc1e812065883f695d1eeaba854' \
  "$ROOT/scripts/security-scan.sh"
grep -q -- '--log-opts=--all' "$ROOT/scripts/security-scan.sh"
awk '
  /^  supply-chain:/ { in_supply_chain = 1 }
  /^  dast:/ { in_supply_chain = 0 }
  in_supply_chain && /fetch-depth: 0/ { full_history = 1 }
  END { exit !full_history }
' "$ROOT/.github/workflows/ci.yml"

echo "GitHub Actions are pinned and include the privacy disclosure gate"
