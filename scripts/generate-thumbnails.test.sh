#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TMP_DIR="$(mktemp -d)"
CALL_LOG="$TMP_DIR/calls.log"
PROJECT_ROOT="$TMP_DIR/project"
IMAGE_DIR="$TMP_DIR/images"
trap 'rm -rf "$TMP_DIR"' EXIT

mkdir -p "$PROJECT_ROOT/backend/target" "$IMAGE_DIR" "$TMP_DIR/bin"
touch "$IMAGE_DIR/photo.jpg"

cat > "$TMP_DIR/bin/mvn" <<SH
#!/usr/bin/env bash
set -euo pipefail
printf 'mvn %s\n' "\$*" >> "$CALL_LOG"
mkdir -p "$PROJECT_ROOT/backend/target"
printf '/tmp/dependency.jar' > "$PROJECT_ROOT/backend/target/thumbnail-classpath.txt"
SH

cat > "$TMP_DIR/bin/java" <<SH
#!/usr/bin/env bash
set -euo pipefail
printf 'java %s\n' "\$*" >> "$CALL_LOG"
SH

chmod +x "$TMP_DIR/bin/mvn" "$TMP_DIR/bin/java"

PATH="$TMP_DIR/bin:$PATH" \
PROJECT_ROOT="$PROJECT_ROOT" \
IMAGE_DIR="$IMAGE_DIR" \
"$ROOT/scripts/generate-thumbnails.sh"

grep -q "mvn -q -DskipTests -f $PROJECT_ROOT/backend/pom.xml compile dependency:build-classpath -Dmdep.outputFile=$PROJECT_ROOT/backend/target/thumbnail-classpath.txt" "$CALL_LOG"
grep -q "java -cp $PROJECT_ROOT/backend/target/classes:/tmp/dependency.jar com.langxi.babydiary.tools.ThumbnailBackfillTool $IMAGE_DIR" "$CALL_LOG"
