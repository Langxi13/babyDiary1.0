#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="${PROJECT_ROOT:-$(cd "$SCRIPT_DIR/.." && pwd)}"
IMAGE_DIR="${IMAGE_DIR:-$PROJECT_ROOT/data/images}"
CLASSPATH_FILE="$PROJECT_ROOT/backend/target/thumbnail-classpath.txt"

cd "$PROJECT_ROOT"

if [ -f scripts/java-env.sh ]; then
  source scripts/java-env.sh
fi

mvn -q -DskipTests -f "$PROJECT_ROOT/backend/pom.xml" compile dependency:build-classpath -Dmdep.outputFile="$CLASSPATH_FILE"

java -cp "$PROJECT_ROOT/backend/target/classes:$(cat "$CLASSPATH_FILE")" \
  com.langxi.babydiary.tools.ThumbnailBackfillTool "$IMAGE_DIR"
