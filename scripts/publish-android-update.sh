#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="${PROJECT_ROOT:-$(cd "$SCRIPT_DIR/.." && pwd)}"
VERSION_FILE="${ANDROID_RELEASE_VERSION_FILE:-$PROJECT_ROOT/config/android-release-version.properties}"
APK_PATH="${APK_PATH:-$PROJECT_ROOT/frontend/android/app/build/outputs/apk/release/app-release.apk}"
AAB_PATH="${AAB_PATH:-$PROJECT_ROOT/frontend/android/app/build/outputs/bundle/release/app-release.aab}"
DOWNLOAD_DIR="${ANDROID_DOWNLOAD_DIR:-$PROJECT_ROOT/deploy/frontend/downloads/android}"
UPDATE_ENV_FILE="${ANDROID_UPDATE_ENV_FILE:-/etc/baby-diary/android-update.env}"
MINIMUM_VERSION_CODE="${ANDROID_MINIMUM_VERSION_CODE:-1}"
MANDATORY="${ANDROID_UPDATE_MANDATORY:-false}"
RELEASE_NOTES="${ANDROID_UPDATE_RELEASE_NOTES:-增加应用版本信息、更新检测和安全的手动安装入口。}"
RESTART_SERVICE="${ANDROID_UPDATE_RESTART_SERVICE:-false}"
ALLOW_REPLACE="${ANDROID_UPDATE_ALLOW_REPLACE:-false}"
SERVER_RELEASE_VERSION="${APP_RELEASE_VERSION:-1.0.0}"
SERVICE_NAME="${SERVICE_NAME:-diary-backend}"

if [ ! -r "$VERSION_FILE" ]; then
  echo "Android release version file is not readable: $VERSION_FILE" >&2
  exit 1
fi

# shellcheck disable=SC1090
. "$VERSION_FILE"

if [[ ! "${VERSION_CODE:-}" =~ ^[1-9][0-9]*$ ]] \
  || [[ ! "${VERSION_NAME:-}" =~ ^[0-9]+(\.[0-9]+){1,3}([.-][A-Za-z0-9]+)*$ ]] \
  || [[ ! "$MINIMUM_VERSION_CODE" =~ ^[1-9][0-9]*$ ]] \
  || [[ ! "$SERVER_RELEASE_VERSION" =~ ^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$ ]] \
  || [ "$MINIMUM_VERSION_CODE" -gt "$VERSION_CODE" ]; then
  echo "Android update version configuration is invalid" >&2
  exit 1
fi

if { [ "$MANDATORY" != "true" ] && [ "$MANDATORY" != "false" ]; } \
  || { [ "$ALLOW_REPLACE" != "true" ] && [ "$ALLOW_REPLACE" != "false" ]; }; then
  echo "Android update boolean settings must be true or false" >&2
  exit 1
fi

bash "$PROJECT_ROOT/scripts/verify-android-artifact.sh" \
  "$APK_PATH" \
  "$AAB_PATH" \
  "$VERSION_CODE" \
  "$VERSION_NAME"

safe_version="$(printf '%s' "$VERSION_NAME" | tr -c 'A-Za-z0-9._-' '-')"
filename="BabyDiary-$safe_version.apk"
mkdir -p "$DOWNLOAD_DIR"
target_apk="$DOWNLOAD_DIR/$filename"
source_sha256="$(sha256sum "$APK_PATH" | cut -d' ' -f1)"
if [ -f "$target_apk" ]; then
  existing_sha256="$(sha256sum "$target_apk" | cut -d' ' -f1)"
  if [ "$existing_sha256" != "$source_sha256" ] && [ "$ALLOW_REPLACE" != "true" ]; then
    echo "Published Android versions are immutable; increment the version or set ANDROID_UPDATE_ALLOW_REPLACE=true for an explicit repair" >&2
    exit 1
  fi
fi
if [ ! -f "$target_apk" ] || [ "${existing_sha256:-}" != "$source_sha256" ]; then
  install -m 0644 "$APK_PATH" "$target_apk.tmp"
  mv -f "$target_apk.tmp" "$target_apk"
fi
sha256="$(sha256sum "$DOWNLOAD_DIR/$filename" | cut -d' ' -f1)"

quote_systemd_value() {
  local value="$1"
  value="${value//$'\r'/ }"
  value="${value//$'\n'/ }"
  value="${value//\\/\\\\}"
  value="${value//\"/\\\"}"
  printf '"%s"' "$value"
}

mkdir -p "$(dirname "$UPDATE_ENV_FILE")"
env_tmp="$(mktemp "$(dirname "$UPDATE_ENV_FILE")/.android-update.env.XXXXXX")"
trap 'rm -f "$env_tmp"' EXIT
{
  printf 'APP_RELEASE_VERSION=%s\n' "$SERVER_RELEASE_VERSION"
  printf 'ANDROID_UPDATE_ENABLED=true\n'
  printf 'ANDROID_UPDATE_DISTRIBUTION=DIRECT\n'
  printf 'ANDROID_UPDATE_VERSION_CODE=%s\n' "$VERSION_CODE"
  printf 'ANDROID_UPDATE_VERSION_NAME=%s\n' "$VERSION_NAME"
  printf 'ANDROID_MINIMUM_VERSION_CODE=%s\n' "$MINIMUM_VERSION_CODE"
  printf 'ANDROID_UPDATE_URL=/downloads/android/%s\n' "$filename"
  printf 'ANDROID_UPDATE_SHA256=%s\n' "$sha256"
  printf 'ANDROID_UPDATE_RELEASE_NOTES=%s\n' "$(quote_systemd_value "$RELEASE_NOTES")"
  printf 'ANDROID_UPDATE_MANDATORY=%s\n' "$MANDATORY"
} > "$env_tmp"
chmod 0644 "$env_tmp"
mv -f "$env_tmp" "$UPDATE_ENV_FILE"
trap - EXIT

if [ "$RESTART_SERVICE" = "true" ]; then
  systemctl daemon-reload
  systemctl restart "$SERVICE_NAME"
  HEALTH_CHECK_ATTEMPTS="${HEALTH_CHECK_ATTEMPTS:-12}" \
  HEALTH_CHECK_DELAY_SECONDS="${HEALTH_CHECK_DELAY_SECONDS:-2}" \
  SERVICE_NAME="$SERVICE_NAME" \
    "$PROJECT_ROOT/scripts/health-check.sh"
fi

echo "Android update published"
echo "version: $VERSION_NAME ($VERSION_CODE)"
echo "apk: $DOWNLOAD_DIR/$filename"
echo "sha256: $sha256"
echo "configuration: $UPDATE_ENV_FILE"
