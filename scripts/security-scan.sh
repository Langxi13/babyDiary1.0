#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="${PROJECT_ROOT:-$(cd "$SCRIPT_DIR/.." && pwd)}"
SCAN_ROOT="$(mktemp -d)"
trap 'rm -rf "$SCAN_ROOT"' EXIT
TRIVY_CACHE_DIR="${TRIVY_CACHE_DIR:-$HOME/.cache/trivy}"
TRIVY_DB_REPOSITORY="${TRIVY_DB_REPOSITORY:-public.ecr.aws/aquasecurity/trivy-db:2}"
TRIVY_CHECKS_REPOSITORY="${TRIVY_CHECKS_REPOSITORY:-ghcr.io/aquasecurity/trivy-checks:1}"
TRIVY_TIMEOUT="${TRIVY_TIMEOUT:-15m}"
GITLEAKS_IMAGE="${GITLEAKS_IMAGE:-ghcr.io/gitleaks/gitleaks@sha256:cdbb7c955abce02001a9f6c9f602fb195b7fadc1e812065883f695d1eeaba854}"

cd "$PROJECT_ROOT"
bash "$SCRIPT_DIR/fetch-public-refs.sh"
bash "$SCRIPT_DIR/privacy-scan.sh"
npm audit --prefix frontend --omit=dev --audit-level=high --registry=https://registry.npmjs.org

bash "$SCRIPT_DIR/create-scan-snapshot.sh" "$SCAN_ROOT"
mkdir -p "$TRIVY_CACHE_DIR"

docker run --rm \
  -v "$SCAN_ROOT:/workspace:ro" \
  -v "$TRIVY_CACHE_DIR:/root/.cache/trivy" \
  aquasec/trivy:0.66.0 fs \
  --db-repository "$TRIVY_DB_REPOSITORY" \
  --checks-bundle-repository "$TRIVY_CHECKS_REPOSITORY" \
  --timeout "$TRIVY_TIMEOUT" \
  --skip-version-check \
  --scanners vuln,secret,misconfig \
  --severity HIGH,CRITICAL \
  --exit-code 1 \
  --no-progress \
  /workspace

docker run --rm \
  -v "$SCAN_ROOT:/workspace:ro" \
  "$GITLEAKS_IMAGE" \
  dir /workspace --redact --no-banner

docker run --rm \
  -v "$PROJECT_ROOT:/repo:ro" \
  "$GITLEAKS_IMAGE" \
  git /repo --log-opts=--all --redact --no-banner
