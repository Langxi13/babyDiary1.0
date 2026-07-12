#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="${PROJECT_ROOT:-$(cd "$SCRIPT_DIR/.." && pwd)}"
ALLOWLIST_FILE="${PRIVACY_ASSET_ALLOWLIST_FILE:-$PROJECT_ROOT/config/public-asset-allowlist.sha256}"
SCAN_HISTORY="${PRIVACY_SCAN_HISTORY:-true}"

if [ ! -r "$ALLOWLIST_FILE" ]; then
  echo "public asset scan: allowlist is not readable: $ALLOWLIST_FILE" >&2
  exit 1
fi

declare -A REVIEWED_PATHS=()
declare -A REVIEWED_VERSIONS=()
while read -r asset_hash asset_path; do
  [ -n "${asset_hash:-}" ] || continue
  [[ "$asset_hash" == \#* ]] && continue
  if [[ ! "$asset_hash" =~ ^[0-9a-f]{64}$ ]] \
    || [ -z "${asset_path:-}" ] \
    || [[ "$asset_path" = /* ]] \
    || [[ "/$asset_path/" == *'/../'* ]]; then
    echo "public asset scan: invalid allowlist entry" >&2
    exit 1
  fi
  REVIEWED_PATHS["$asset_path"]=1
  REVIEWED_VERSIONS["$asset_hash|$asset_path"]=1
done < "$ALLOWLIST_FILE"

requires_review() {
  local lower="${1,,}"

  case "$lower" in
    *.png|*.jpg|*.jpeg|*.gif|*.webp|*.avif|*.heic|*.heif|*.tif|*.tiff|*.bmp|*.ico|*.svg|*.pdf|*.doc|*.docx|*.xls|*.xlsx|*.ppt|*.pptx|*.zip|*.tar|*.tgz|*.gz|*.7z|*.rar|*.mp3|*.wav|*.m4a|*.aac|*.ogg|*.flac|*.mp4|*.mov|*.mkv|*.webm|*.woff|*.woff2|*.ttf|*.otf)
      return 0
      ;;
  esac

  return 1
}

failures=0
declare -A historical_paths=()
declare -A seen_objects=()

while IFS= read -r -d '' path; do
  requires_review "$path" || continue
  if [ -L "$PROJECT_ROOT/$path" ]; then
    printf 'public asset scan: reviewed asset must not be a symbolic link: %s\n' "$path" >&2
    failures=$((failures + 1))
    continue
  fi
  if [ -z "${REVIEWED_PATHS[$path]:-}" ]; then
    printf 'public asset scan: unreviewed public asset: %s\n' "$path" >&2
    failures=$((failures + 1))
    continue
  fi
  actual_hash="$(sha256sum -- "$PROJECT_ROOT/$path" | cut -c1-64)"
  if [ -z "${REVIEWED_VERSIONS[$actual_hash|$path]:-}" ]; then
    printf 'public asset scan: reviewed asset changed and requires approval: %s\n' "$path" >&2
    failures=$((failures + 1))
  fi
done < <(git -C "$PROJECT_ROOT" ls-files -z --cached --others --exclude-standard)

if [ "$SCAN_HISTORY" = "true" ]; then
  while IFS= read -r -d '' path; do
    requires_review "$path" || continue
    historical_paths["$path"]=1
  done < <(git -C "$PROJECT_ROOT" log --all --format= --name-only -z -- .)

  for path in "${!historical_paths[@]}"; do
    if [ -z "${REVIEWED_PATHS[$path]:-}" ]; then
      printf 'public asset scan: unreviewed asset exists in Git history (path redacted)\n' >&2
      failures=$((failures + 1))
      continue
    fi

    seen_objects=()
    while IFS= read -r commit; do
      object_id="$(git -C "$PROJECT_ROOT" rev-parse "$commit:$path" 2>/dev/null || true)"
      [ -n "$object_id" ] || continue
      [ -z "${seen_objects[$object_id]:-}" ] || continue
      seen_objects["$object_id"]=1
      actual_hash="$(git -C "$PROJECT_ROOT" cat-file blob "$object_id" | sha256sum | cut -c1-64)"
      if [ -z "${REVIEWED_VERSIONS[$actual_hash|$path]:-}" ]; then
        printf 'public asset scan: unreviewed asset version exists in Git history (path redacted)\n' >&2
        failures=$((failures + 1))
      fi
    done < <(git -C "$PROJECT_ROOT" log --all --format='%H' -- "$path")
  done
fi

if [ "$failures" -ne 0 ]; then
  printf 'public asset scan failed with %d finding(s)\n' "$failures" >&2
  exit 1
fi

echo "public asset scan passed: every current and historical asset version is reviewed"
