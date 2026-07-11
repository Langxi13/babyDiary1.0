#!/usr/bin/env bash
set -euo pipefail

SERVICE_NAME="${SERVICE_NAME:-diary-backend}"
NGINX_SERVICE="${NGINX_SERVICE:-nginx}"
BACKEND_ENV_FILE="${BACKEND_ENV_FILE:-/etc/baby-diary/backend.env}"

if [ -r "$BACKEND_ENV_FILE" ]; then
  set -a
  . "$BACKEND_ENV_FILE"
  set +a
fi

BASE_URL="${BASE_URL:-${HEALTH_CHECK_BASE_URL:-}}"
RESOLVE_HOST="${RESOLVE_HOST:-${HEALTH_CHECK_RESOLVE_HOST:-}}"
SYSTEMCTL_BIN="${SYSTEMCTL_BIN:-systemctl}"
CURL_BIN="${CURL_BIN:-curl}"
HEALTH_CHECK_ATTEMPTS="${HEALTH_CHECK_ATTEMPTS:-1}"
HEALTH_CHECK_DELAY_SECONDS="${HEALTH_CHECK_DELAY_SECONDS:-2}"
PWA_MANIFEST_PATH="${PWA_MANIFEST_PATH:-/manifest.webmanifest}"
PWA_MANIFEST_CONTENT_TYPE="${PWA_MANIFEST_CONTENT_TYPE:-application/manifest+json}"

if [ -z "$BASE_URL" ]; then
  echo "BASE_URL or HEALTH_CHECK_BASE_URL is required" >&2
  exit 2
fi

PATH_CHECKS="${PATH_CHECKS:-/ 200
/album 200
/diaries 200
/api/auth/info 401}"
ACTUATOR_HEALTH_PATH="${ACTUATOR_HEALTH_PATH:-/actuator/health}"

check_service() {
  local service="$1"
  local status
  status="$("$SYSTEMCTL_BIN" is-active "$service")"
  if [ "$status" != "active" ]; then
    echo "service $service $status" >&2
    return 1
  fi
  echo "service $service active"
}

check_http_path() {
  local path="$1"
  local expected="$2"
  local code
  local curl_args=(--noproxy '*' -s -o /dev/null -w '%{http_code}' --max-time 10)

  if [[ "$BASE_URL" == https://* ]] && [ -n "$RESOLVE_HOST" ]; then
    curl_args+=(--resolve "$RESOLVE_HOST")
  fi

  code="$("$CURL_BIN" "${curl_args[@]}" "${BASE_URL}${path}")"
  echo "GET $path $code"
  if [ "$code" != "$expected" ]; then
    echo "expected $path to return $expected, got $code" >&2
    return 1
  fi
}

check_http_content_type() {
  local path="$1"
  local expected="$2"
  local result
  local code
  local content_type
  local curl_args=(--noproxy '*' -s -o /dev/null -w '%{http_code} %{content_type}' --max-time 10)

  if [[ "$BASE_URL" == https://* ]] && [ -n "$RESOLVE_HOST" ]; then
    curl_args+=(--resolve "$RESOLVE_HOST")
  fi

  result="$("$CURL_BIN" "${curl_args[@]}" "${BASE_URL}${path}")"
  code="${result%% *}"
  content_type="${result#* }"

  echo "GET $path $code $content_type"
  if [ "$code" != "200" ]; then
    echo "expected $path to return 200, got $code" >&2
    return 1
  fi

  if [[ "$content_type" != "$expected"* ]]; then
    echo "expected $path content-type to start with $expected, got $content_type" >&2
    return 1
  fi
}

check_actuator_health() {
  local result
  local response_meta
  local body
  local code
  local content_type
  local compact_body
  local curl_args=(--noproxy '*' -sS -w $'\n%{http_code} %{content_type}' --max-time 10)

  if [[ "$BASE_URL" == https://* ]] && [ -n "$RESOLVE_HOST" ]; then
    curl_args+=(--resolve "$RESOLVE_HOST")
  fi

  if ! result="$("$CURL_BIN" "${curl_args[@]}" "${BASE_URL}${ACTUATOR_HEALTH_PATH}")"; then
    echo "failed to request $ACTUATOR_HEALTH_PATH" >&2
    return 1
  fi

  response_meta="${result##*$'\n'}"
  body="${result%$'\n'*}"
  code="${response_meta%% *}"
  content_type="${response_meta#* }"
  compact_body="$(tr -d '[:space:]' <<<"$body")"

  echo "GET $ACTUATOR_HEALTH_PATH $code $content_type"
  if [ "$code" != "200" ]; then
    echo "expected $ACTUATOR_HEALTH_PATH to return 200, got $code" >&2
    return 1
  fi

  if [[ "$content_type" != application/*json* ]]; then
    echo "expected $ACTUATOR_HEALTH_PATH to return JSON, got $content_type" >&2
    return 1
  fi

  if [[ ! "$compact_body" =~ ^\{\"status\":\"UP\"([,\}]) ]]; then
    echo "expected $ACTUATOR_HEALTH_PATH top-level status to be UP" >&2
    return 1
  fi

  echo "actuator status UP"
}

run_checks() {
  local failed=0

  check_service "$SERVICE_NAME" || failed=1
  check_service "$NGINX_SERVICE" || failed=1

  while read -r path expected; do
    [ -n "${path:-}" ] || continue
    check_http_path "$path" "$expected" || failed=1
  done <<< "$PATH_CHECKS"

  check_actuator_health || failed=1
  check_http_content_type "$PWA_MANIFEST_PATH" "$PWA_MANIFEST_CONTENT_TYPE" || failed=1

  return "$failed"
}

attempt=1
while [ "$attempt" -le "$HEALTH_CHECK_ATTEMPTS" ]; do
  if run_checks; then
    exit 0
  fi

  if [ "$attempt" -eq "$HEALTH_CHECK_ATTEMPTS" ]; then
    exit 1
  fi

  echo "health check attempt $attempt failed, retrying..." >&2
  sleep "$HEALTH_CHECK_DELAY_SECONDS"
  attempt=$((attempt + 1))
done
