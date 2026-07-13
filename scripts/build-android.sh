#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="${PROJECT_ROOT:-$(cd "$SCRIPT_DIR/.." && pwd)}"
ANDROID_JAVA_HOME="${ANDROID_JAVA_HOME:-${JAVA_HOME:-/usr/lib/jvm/java-21-openjdk-amd64}}"
ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-/opt/android-sdk}}"
ANDROID_GRADLE_WORKERS="${ANDROID_GRADLE_WORKERS:-1}"

if [ ! -x "$ANDROID_JAVA_HOME/bin/java" ]; then
  echo "Android builds require Java 21. Set ANDROID_JAVA_HOME." >&2
  exit 1
fi

if ! "$ANDROID_JAVA_HOME/bin/java" -version 2>&1 | grep -q 'version "21'; then
  echo "ANDROID_JAVA_HOME must point to Java 21" >&2
  exit 1
fi

if [ ! -d "$ANDROID_SDK_ROOT/platforms/android-36" ]; then
  echo "Android SDK Platform 36 is required at ANDROID_SDK_ROOT" >&2
  exit 1
fi

export JAVA_HOME="$ANDROID_JAVA_HOME"
export ANDROID_HOME="$ANDROID_SDK_ROOT"
export ANDROID_SDK_ROOT
export PATH="$JAVA_HOME/bin:$ANDROID_SDK_ROOT/platform-tools:$PATH"

npm --prefix "$PROJECT_ROOT/frontend" run build

cd "$PROJECT_ROOT/frontend"
npx cap sync android

cd android
./gradlew --no-daemon --max-workers="$ANDROID_GRADLE_WORKERS" lintDebug testDebugUnitTest assembleDebug
