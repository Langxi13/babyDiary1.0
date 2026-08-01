#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT

SYSTEMCTL_FAKE="$TMP_DIR/systemctl"
CURL_FAKE="$TMP_DIR/curl"
ENV_FILE="$TMP_DIR/backend.env"

cat > "$ENV_FILE" <<'ENV'
HEALTH_CHECK_BASE_URL=https://diary.example.com
HEALTH_CHECK_RESOLVE_HOST=diary.example.com:443:127.0.0.1
ENV

cat > "$SYSTEMCTL_FAKE" <<'SH'
#!/usr/bin/env bash
set -euo pipefail
if [ "$1" != "is-active" ]; then
  exit 2
fi
case "$2" in
  diary-backend|nginx)
    echo active
    ;;
  *)
    echo inactive
    exit 3
    ;;
esac
SH

cat > "$CURL_FAKE" <<'SH'
#!/usr/bin/env bash
set -euo pipefail
format=""
output_file=""
dump_headers=""
method="GET"
url=""
while [ "$#" -gt 0 ]; do
  case "$1" in
    -w)
      format="$2"
      shift 2
      ;;
    -o)
      output_file="$2"
      shift 2
      ;;
    -D)
      dump_headers="$2"
      shift 2
      ;;
    -X)
      method="$2"
      shift 2
      ;;
    http://*|https://*)
      url="$1"
      shift
      ;;
    *)
      shift
      ;;
  esac
done

path="/${url#*://*/}"
if [ "$path" = "/$url" ] || [ "$path" = "/" ]; then
  path="/"
fi

case "$path" in
  /|/album|/diaries)
    code=200
    content_type="text/html"
    body='<html></html>'
    ;;
  /api/v3/client/bootstrap)
    code=200
    content_type="application/json"
    body='{"code":200,"data":{"apiVersion":2,"nativeSessionMode":"COOKIE"}}'
    ;;
  /actuator/health)
    code=200
    content_type="application/vnd.spring-boot.actuator.v3+json"
    if [ -n "${ACTUATOR_HEALTH_BODY+x}" ]; then
      body="$ACTUATOR_HEALTH_BODY"
    else
      body='{"status":"UP"}'
    fi
    ;;
  /api/v3/account/profile)
    code="${AUTH_INFO_CODE:-401}"
    content_type="application/json"
    body='{"code":401}'
    ;;
  /manifest.webmanifest)
    code=200
    content_type="application/manifest+json"
    body='{"name":"Baby Diary"}'
    ;;
  *)
    code=404
    content_type="text/plain"
    body='not found'
    ;;
esac

if [ "$method" = "OPTIONS" ] && [ "$path" = "/api/v3/client/bootstrap" ]; then
  code=200
  content_type="text/plain"
  body=''
  cors_origin='https://localhost'
  cors_headers=$'Access-Control-Allow-Origin: '"$cors_origin"$'\r\nAccess-Control-Allow-Headers: authorization,idempotency-key,x-client-platform,x-client-version-code,x-client-version-name\r\n'
else
  cors_headers=''
fi

if [ -n "$dump_headers" ]; then
  header_output="HTTP/1.1 $code OK"$'\r\n'"Content-Type: $content_type"$'\r\n'"$cors_headers"$'\r\n'
  if [ "$dump_headers" = "-" ]; then
    printf '%s' "$header_output"
  else
    printf '%s' "$header_output" > "$dump_headers"
  fi
fi

if [ -z "$output_file" ]; then
  printf '%s' "$body"
elif [ "$output_file" != "/dev/null" ]; then
  printf '%s' "$body" > "$output_file"
fi

output="${format//\%\{http_code\}/$code}"
output="${output//\%\{content_type\}/$content_type}"
printf "%b" "$output"
SH

chmod +x "$SYSTEMCTL_FAKE" "$CURL_FAKE"

OUTPUT="$(
  SYSTEMCTL_BIN="$SYSTEMCTL_FAKE" \
  CURL_BIN="$CURL_FAKE" \
  BACKEND_ENV_FILE="$ENV_FILE" \
  "$ROOT/scripts/health-check.sh"
)"

grep -q "service diary-backend active" <<<"$OUTPUT"
grep -q "service nginx active" <<<"$OUTPUT"
grep -q "GET / 200" <<<"$OUTPUT"
grep -q "GET /album 200" <<<"$OUTPUT"
grep -q "GET /diaries 200" <<<"$OUTPUT"
grep -q "GET /api/v3/client/bootstrap 200" <<<"$OUTPUT"
grep -q "GET /actuator/health 200 application/vnd.spring-boot.actuator.v3+json" <<<"$OUTPUT"
grep -q "actuator status UP" <<<"$OUTPUT"
grep -q "GET /api/v3/account/profile 401" <<<"$OUTPUT"
grep -q "GET /manifest.webmanifest 200 application/manifest+json" <<<"$OUTPUT"
grep -q "OPTIONS /api/v3/client/bootstrap 200 Android CORS" <<<"$OUTPUT"

set +e
FAIL_OUTPUT="$(
  SYSTEMCTL_BIN="$SYSTEMCTL_FAKE" \
  CURL_BIN="$CURL_FAKE" \
  BACKEND_ENV_FILE="$ENV_FILE" \
  AUTH_INFO_CODE=502 \
  "$ROOT/scripts/health-check.sh" 2>&1
)"
FAIL_STATUS=$?
set -e

if [ "$FAIL_STATUS" -eq 0 ]; then
  echo "expected health check to fail when a path returns the wrong status" >&2
  exit 1
fi

grep -q "expected /api/v3/account/profile to return 401, got 502" <<<"$FAIL_OUTPUT"

set +e
DOWN_OUTPUT="$(
  SYSTEMCTL_BIN="$SYSTEMCTL_FAKE" \
  CURL_BIN="$CURL_FAKE" \
  BACKEND_ENV_FILE="$ENV_FILE" \
  ACTUATOR_HEALTH_BODY='{"status":"DOWN"}' \
  "$ROOT/scripts/health-check.sh" 2>&1
)"
DOWN_STATUS=$?
set -e

if [ "$DOWN_STATUS" -eq 0 ]; then
  echo "expected health check to fail when Actuator reports DOWN" >&2
  exit 1
fi

grep -q "expected /actuator/health top-level status to be UP" <<<"$DOWN_OUTPUT"
