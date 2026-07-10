#!/usr/bin/env bash
set -euo pipefail

CONTAINER_NAME="${REDIS_CONTAINER_NAME:-baby-diary-redis}"
IMAGE="${REDIS_IMAGE:-redis:7-alpine}"
HOST="${REDIS_BIND_HOST:-127.0.0.1}"
PORT="${REDIS_PORT:-6380}"
VOLUME="${REDIS_VOLUME:-baby-diary-redis-data}"
MAXMEMORY="${REDIS_MAXMEMORY:-128mb}"
MAXMEMORY_POLICY="${REDIS_MAXMEMORY_POLICY:-allkeys-lru}"

configure_redis() {
  docker exec "$CONTAINER_NAME" redis-cli CONFIG SET maxmemory "$MAXMEMORY" >/dev/null
  docker exec "$CONTAINER_NAME" redis-cli CONFIG SET maxmemory-policy "$MAXMEMORY_POLICY" >/dev/null
}

if ! command -v docker >/dev/null 2>&1; then
  echo "docker is required to run ${CONTAINER_NAME}" >&2
  exit 1
fi

if docker ps --format '{{.Names}}' | grep -qx "$CONTAINER_NAME"; then
  configure_redis
  exit 0
fi

if docker ps -a --format '{{.Names}}' | grep -qx "$CONTAINER_NAME"; then
  docker start "$CONTAINER_NAME" >/dev/null
  configure_redis
  exit 0
fi

docker volume inspect "$VOLUME" >/dev/null 2>&1 || docker volume create "$VOLUME" >/dev/null
docker run -d \
  --name "$CONTAINER_NAME" \
  --restart unless-stopped \
  -p "${HOST}:${PORT}:6379" \
  -v "${VOLUME}:/data" \
  "$IMAGE" \
  redis-server \
    --appendonly yes \
    --save 60 1 \
    --maxmemory "$MAXMEMORY" \
    --maxmemory-policy "$MAXMEMORY_POLICY" >/dev/null
