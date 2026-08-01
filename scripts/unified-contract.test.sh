#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RUNTIME_PATHS=(
  "$ROOT/backend/src/main" "$ROOT/frontend/src" "$ROOT/scripts" "$ROOT/config"
  "$ROOT/.env.example" "$ROOT/compose.yaml" "$ROOT/compose.e2e.yaml"
  "$ROOT/compose.staging.yaml" "$ROOT/backend/Dockerfile"
)

fail_if_found() {
  local pattern="$1"
  local message="$2"
  if rg -n --pcre2 "$pattern" "${RUNTIME_PATHS[@]}" \
    --glob '!db/migration/**' --glob '!*.test.sh' --glob '!*.spec.js' --glob '!*.test.js'; then
    echo "$message" >&2
    exit 1
  fi
}

if rg -n --pcre2 '\bV3_[A-Z0-9_]+' "${RUNTIME_PATHS[@]}" \
  --glob '!db/migration/**' --glob '!*.test.sh' \
  --glob '!runtime-governance-check.sh'; then
  echo 'retired V3 environment variables are forbidden' >&2
  exit 1
fi
if rg -n --pcre2 'baby_diary_v3|baby-diary:v3:(cache|rate)' "${RUNTIME_PATHS[@]}" \
  --glob '!*.test.sh'; then
  echo 'versioned runtime namespaces are forbidden' >&2
  exit 1
fi
if rg -n --pcre2 'v3Adapters|DIARY_FILE_PATH|DIARY_PAGE_SIZE|JWT_EXPIRATION|MEDIA_PROCESSING_ENABLED|FFMPEG_BIN|FFPROBE_BIN|TESSERACT_BIN|APP_RELEASE_VERSION' \
  "${RUNTIME_PATHS[@]}" --glob '!*.test.sh' \
  --glob '!runtime-governance-check.sh'; then
  echo 'retired runtime names are forbidden' >&2
  exit 1
fi
if rg -n --pcre2 '(/api/v1|/api/v2|/images/|contentUrl|thumbnailUrl|posterUrl|waveformUrl|transcodedUrl)' \
  "$ROOT/backend/src/main" "$ROOT/frontend/src" "$ROOT/config" \
  "$ROOT/compose.yaml" "$ROOT/compose.e2e.yaml" "$ROOT/compose.staging.yaml" \
  --glob '!db/migration/**' --glob '!*.test.js' --glob '!*.spec.js'; then
  echo 'retired API or media aliases are forbidden' >&2
  exit 1
fi

grep -qx 'PRODUCT_VERSION=1.0.0-beta.8' "$ROOT/config/release-version.properties"
grep -qx 'ANDROID_VERSION_CODE=8' "$ROOT/config/release-version.properties"
grep -q '<version>1.0.0-beta.8</version>' "$ROOT/backend/pom.xml"
grep -qx '  "version": "1.0.0-beta.8",' "$ROOT/frontend/package.json"
grep -q 'public static final String ROOT = "/api/v3"' \
  "$ROOT/backend/src/main/java/com/langxi/babydiary/platform/api/ApiContract.java"
grep -q 'export const API_VERSION = 3' "$ROOT/frontend/src/api/contract.js"
test ! -e "$ROOT/frontend/src/api/v3Adapters.js"
test ! -e "$ROOT/config/android-release-version.properties"
test ! -e "$ROOT/scripts/unify-runtime-data.sh"
test ! -e "$ROOT/scripts/normalize-runtime-env.sh"
test ! -e "$ROOT/scripts/verify-retirement-archive.sh"
echo "unified runtime contract passed"
