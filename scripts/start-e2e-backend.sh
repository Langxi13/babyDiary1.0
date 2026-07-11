#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
COMPOSE=(docker compose -p baby-diary-e2e -f "$PROJECT_ROOT/compose.e2e.yaml")

cleanup() {
  "${COMPOSE[@]}" down --volumes --remove-orphans >/dev/null 2>&1 || true
}

trap cleanup EXIT INT TERM
cleanup
"${COMPOSE[@]}" up -d --wait mysql redis ai-mock

export SERVER_PORT=11002
export DB_URL='jdbc:mysql://127.0.0.1:3307/baby-diary?connectionTimeZone=%2B08:00&forceConnectionTimeZoneToSession=true&useSSL=false&allowPublicKeyRetrieval=true'
export DB_USERNAME=baby_diary_e2e
export DB_PASSWORD=baby_diary_e2e
export REDIS_HOST=127.0.0.1
export REDIS_PORT=6381
export SPRING_CACHE_TYPE=redis
export CACHE_PREFIX=baby-diary:e2e:
export INVITATION_CODE=e2e-invitation-code
export JWT_SECRET=e2e-jwt-secret-that-is-longer-than-thirty-two-characters
export JWT_ACCESS_EXPIRATION=900000
export JWT_EXPIRATION=2592000000
export AUTH_SECURE_COOKIE=false
export CORS_ALLOWED_ORIGINS=http://127.0.0.1:4173
export AI_CONFIG_ENCRYPTION_KEY=test-only-key-00000000000000000000000000000000
export DIARY_FILE_PATH=/tmp/baby-diary-e2e/images/
export DIARY_OBJECT_PATH=/tmp/baby-diary-e2e/objects/
export MEDIA_PROCESSING_ENABLED=false
export MAIL_ENABLED=false
export SPRINGDOC_ENABLED=true
export REMINDER_DELIVERY_DELAY_MS=3600000

cd "$PROJECT_ROOT"
source scripts/java-env.sh
mvn -q -f backend/pom.xml spring-boot:run
