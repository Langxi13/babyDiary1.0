#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="${PROJECT_ROOT:-$(cd "$SCRIPT_DIR/.." && pwd)}"
ENV_FILE="${STAGING_ENV_FILE:-$PROJECT_ROOT/.env.staging}"
PROJECT_NAME="${STAGING_PROJECT_NAME:-baby-diary-performance}"
FIXTURE_USERNAME="${K6_FIXTURE_USERNAME:-performance-reader}"
FIXTURE_PASSWORD="${K6_FIXTURE_PASSWORD:-synthetic-load-password}"
BASE_URL="${BASE_URL:-http://127.0.0.1:${STAGING_WEB_PORT:-4173}}"

set -a
source "$ENV_FILE"
set +a

register_status="$(curl --silent --output /dev/null --write-out '%{http_code}' \
  --header 'Content-Type: application/json' \
  --data "$(jq -cn --arg username "$FIXTURE_USERNAME" --arg password "$FIXTURE_PASSWORD" --arg invitation "$INVITATION_CODE" \
    '{username:$username,password:$password,confirmPassword:$password,invitationCode:$invitation}')" \
  "$BASE_URL/api/v3/auth/register")"
if [ "$register_status" != "204" ] && [ "$register_status" != "409" ]; then
  echo "Unable to create performance fixture account: HTTP $register_status" >&2
  exit 1
fi

COMPOSE=(docker compose --env-file "$ENV_FILE" -p "$PROJECT_NAME" -f "$PROJECT_ROOT/compose.staging.yaml")
existing_diaries="$("${COMPOSE[@]}" exec -T mysql mysql -N -uroot -p"$MYSQL_ROOT_PASSWORD" baby_diary \
  -e "SELECT COUNT(*) FROM diary d JOIN account a ON a.account_id=d.author_id WHERE a.username='$FIXTURE_USERNAME' AND d.deleted_at IS NULL")"
if [ "$existing_diaries" = "10000" ]; then
  echo "Performance fixture already contains 10000 diaries"
  exit 0
fi
if [ "$existing_diaries" != "0" ]; then
  echo "Performance fixture is partial ($existing_diaries diaries); recreate the staging volumes before seeding" >&2
  exit 1
fi

{
  printf "SET @fixture_username='%s';\n" "$FIXTURE_USERNAME"
  cat "$PROJECT_ROOT/performance/fixtures/staging-scale.sql"
} | "${COMPOSE[@]}" exec -T mysql mysql -uroot -p"$MYSQL_ROOT_PASSWORD" baby_diary

"${COMPOSE[@]}" exec -T backend sh -c '
  set -eu
  root=/data/objects/performance
  mkdir -p "$root"
  printf "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=" | base64 -d > "$root/seed.png"
  seq 1 20000 | xargs -P 4 -I{} sh -c '\''ln -f "$0/seed.png" "$0/source-{}"; ln -f "$0/seed.png" "$0/compact-{}"; ln -f "$0/seed.png" "$0/screen-{}"'\'' "$root"
'

echo "Seeded deterministic performance fixture: 10000 diaries, 20000 media assets"
