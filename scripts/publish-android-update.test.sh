#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
SCRIPT="$ROOT/scripts/publish-android-update.sh"
DEPLOY_SCRIPT="$ROOT/scripts/deploy.sh"
UPDATE_DROP_IN="$ROOT/config/diary-backend-update.conf"

grep -q 'verify-android-artifact.sh' "$SCRIPT"
grep -q 'ANDROID_UPDATE_URL=/downloads/android/' "$SCRIPT"
grep -q 'ANDROID_UPDATE_SHA256=' "$SCRIPT"
grep -q 'ANDROID_UPDATE_RESTART_SERVICE' "$SCRIPT"
grep -q 'ANDROID_UPDATE_ALLOW_REPLACE' "$SCRIPT"
grep -q 'Published Android versions are immutable' "$SCRIPT"
grep -q 'HEALTH_CHECK_ATTEMPTS.*12' "$SCRIPT"
grep -q -- '--exclude downloads/' "$DEPLOY_SCRIPT"
grep -q 'diary-backend-update.conf' "$DEPLOY_SCRIPT"
grep -q 'EnvironmentFile=-/etc/baby-diary/android-update.env' "$UPDATE_DROP_IN"

echo "Android self-hosted update publishing is guarded"
