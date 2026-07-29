#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
JAR="$PROJECT_ROOT/backend/target/Baby-Diary-0.0.1-SNAPSHOT.jar"

if [ ! -f "$JAR" ]; then
  echo "Backend package is missing. Run: mvn -T1 -DskipTests package -f backend/pom.xml" >&2
  exit 1
fi

exec java \
  -Xms64m \
  -Xmx384m \
  -Dloader.main=com.langxi.babydiary.migration.v3.V3MigrationCli \
  -cp "$JAR" \
  org.springframework.boot.loader.launch.PropertiesLauncher \
  "$@"
