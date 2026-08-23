#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="${PROJECT_ROOT:-$(cd "$SCRIPT_DIR/.." && pwd)}"
DEPLOY_ROOT="${DEPLOY_ROOT:-/srv/baby-diary}"
CONFIG_ROOT="${CONFIG_ROOT:-/etc/baby-diary}"
SERVICE_NAME="${SERVICE_NAME:-diary-backend}"
JAR_NAME="${JAR_NAME:-Baby-Diary.jar}"
SERVICE_USER="${SERVICE_USER:-baby-diary}"
SERVICE_GROUP="${SERVICE_GROUP:-$SERVICE_USER}"
SYSTEMD_RUNTIME_OVERRIDE="/etc/systemd/system/diary-backend.service.d/30-baby-diary-runtime.conf"
SYSTEMD_RETIRED_OVERRIDE="/etc/systemd/system/diary-backend.service.d/30-baby-diary-v3.conf"

cd "$PROJECT_ROOT"

scripts/disk-audit.sh --enforce

source scripts/java-env.sh

mvn "${MAVEN_SETTINGS_ARGS[@]}" -q -DskipTests clean package -f backend/pom.xml
npm --prefix frontend run build

chmod +x scripts/ensure-ai-env.sh
scripts/ensure-ai-env.sh

chmod +x scripts/ensure-invitation-env.sh
scripts/ensure-invitation-env.sh

chmod +x scripts/ensure-redis.sh
scripts/ensure-redis.sh

chmod +x scripts/ensure-backup-passphrase.sh scripts/ensure-object-permissions.sh
scripts/ensure-backup-passphrase.sh
scripts/ensure-object-permissions.sh
install -D -m 0644 config/nginx-security-headers.conf /etc/nginx/snippets/baby-diary-security-headers.conf
install -D -m 0644 config/nginx-resource-policy-map.conf /etc/nginx/conf.d/baby-diary-resource-policy-map.conf
install -D -m 0644 config/nginx-backend-health.conf /etc/nginx/snippets/baby-diary-backend-health.conf
install -D -m 0644 config/nginx-media-cache-path.conf /etc/nginx/conf.d/baby-diary-media-cache-path.conf
install -D -m 0644 config/nginx-media-cache-location.conf /etc/nginx/snippets/baby-diary-media-cache-location.conf
install -D -m 0644 config/diary-backend-hardening.conf /etc/systemd/system/diary-backend.service.d/10-baby-diary-hardening.conf
install -D -m 0644 config/diary-backend-update.conf /etc/systemd/system/diary-backend.service.d/20-baby-diary-update.conf
java_bin="$(command -v java)"
escaped_java_bin="${java_bin//&/\&}"
escaped_deploy_root="${DEPLOY_ROOT//&/\&}"
escaped_config_root="${CONFIG_ROOT//&/\&}"
escaped_jar_name="${JAR_NAME//&/\&}"
sed \
  -e "s|@JAVA_BIN@|$escaped_java_bin|g" \
  -e "s|@DEPLOY_ROOT@|$escaped_deploy_root|g" \
  -e "s|@CONFIG_ROOT@|$escaped_config_root|g" \
  -e "s|@JAR_NAME@|$escaped_jar_name|g" \
  config/diary-backend-runtime.conf | install -D -m 0644 /dev/stdin "$SYSTEMD_RUNTIME_OVERRIDE"
rm -f "$SYSTEMD_RETIRED_OVERRIDE"

systemctl daemon-reload

scripts/runtime-governance-check.sh
nginx -t

install -d -m 0755 -o "$SERVICE_USER" -g "$SERVICE_GROUP" \
  "$DEPLOY_ROOT" "$DEPLOY_ROOT/frontend"
install -d -m 0750 -o "$SERVICE_USER" -g "$SERVICE_GROUP" \
  "$DEPLOY_ROOT/backend" "$DEPLOY_ROOT/logs"
install -D -m 0640 -o "$SERVICE_USER" -g "$SERVICE_GROUP" \
  config/application-prod.yml "$CONFIG_ROOT/application-prod.yml"

systemctl stop "$SERVICE_NAME"
if [ -f "$DEPLOY_ROOT/backend/$JAR_NAME" ]; then
  cp "$DEPLOY_ROOT/backend/$JAR_NAME" "$DEPLOY_ROOT/backend/$JAR_NAME.previous"
fi
install -m 0640 -o "$SERVICE_USER" -g "$SERVICE_GROUP" \
  "backend/target/$JAR_NAME" "$DEPLOY_ROOT/backend/$JAR_NAME"
rsync -a --delete --exclude downloads/ frontend/dist/ "$DEPLOY_ROOT/frontend/"
systemctl start "$SERVICE_NAME"

systemctl reload nginx
HEALTH_CHECK_ATTEMPTS="${HEALTH_CHECK_ATTEMPTS:-12}" \
HEALTH_CHECK_DELAY_SECONDS="${HEALTH_CHECK_DELAY_SECONDS:-2}" \
SERVICE_NAME="$SERVICE_NAME" \
scripts/health-check.sh
