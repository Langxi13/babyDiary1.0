#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MODE="${1:-}"
JAR="${MEDIA_MIGRATION_JAR:-$ROOT/deploy/backend/Baby-Diary-0.0.1-SNAPSHOT.jar}"
SERVICE_USER="${SERVICE_USER:-baby-diary}"

case "$MODE" in
  dry-run|apply|verify) ;;
  *) echo "usage: scripts/migrate-media.sh {dry-run|apply|verify}" >&2; exit 2 ;;
esac

if [ -f /etc/baby-diary/backend.env ]; then
  set -a
  # shellcheck disable=SC1091
  . /etc/baby-diary/backend.env
  set +a
fi

if [ ! -r "$JAR" ]; then
  echo "backend jar is not readable: $JAR" >&2
  exit 1
fi

if [ "$MODE" = "apply" ] && [ "${MEDIA_MIGRATION_CONFIRM:-}" != "UNIFY_MEDIA_V15" ]; then
  echo "set MEDIA_MIGRATION_CONFIRM=UNIFY_MEDIA_V15 before applying the migration" >&2
  exit 1
fi

umask 0077
command=(
  java -jar "$JAR"
  --spring.main.web-application-type=none
  --spring.task.scheduling.enabled=false
  --app.media.migration-confirmation="${MEDIA_MIGRATION_CONFIRM:-}"
  --app.media.migration-mode="$MODE"
)

if [ "$(id -u)" -eq 0 ] && id "$SERVICE_USER" >/dev/null 2>&1; then
  exec runuser -u "$SERVICE_USER" --preserve-environment -- "${command[@]}"
fi

exec "${command[@]}"
