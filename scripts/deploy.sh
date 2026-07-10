#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="${PROJECT_ROOT:-$(cd "$SCRIPT_DIR/.." && pwd)}"
SERVICE_NAME="${SERVICE_NAME:-diary-backend}"
JAR_NAME="${JAR_NAME:-Baby-Diary-0.0.1-SNAPSHOT.jar}"

cd "$PROJECT_ROOT"

source scripts/java-env.sh

mvn -q -DskipTests clean package -f backend/pom.xml
npm --prefix frontend run build

chmod +x scripts/ensure-ai-env.sh
scripts/ensure-ai-env.sh

chmod +x scripts/ensure-redis.sh
scripts/ensure-redis.sh

chmod +x scripts/ensure-image-permissions.sh
scripts/ensure-image-permissions.sh

chmod +x scripts/generate-thumbnails.sh
scripts/generate-thumbnails.sh

scripts/ensure-image-permissions.sh

scripts/runtime-governance-check.sh

systemctl stop "$SERVICE_NAME"
cp "backend/target/$JAR_NAME" "deploy/backend/$JAR_NAME"
rsync -a --delete frontend/dist/ deploy/frontend/
systemctl start "$SERVICE_NAME"

nginx -t
systemctl reload nginx
HEALTH_CHECK_ATTEMPTS="${HEALTH_CHECK_ATTEMPTS:-12}" \
HEALTH_CHECK_DELAY_SECONDS="${HEALTH_CHECK_DELAY_SECONDS:-2}" \
SERVICE_NAME="$SERVICE_NAME" \
scripts/health-check.sh
