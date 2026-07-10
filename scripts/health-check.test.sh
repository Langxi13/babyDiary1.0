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
url=""
while [ "$#" -gt 0 ]; do
  case "$1" in
    -w)
      format="$2"
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
    ;;
  /actuator/health)
    code=200
    content_type="application/vnd.spring-boot.actuator.v3+json"
    ;;
  /api/auth/info)
    code="${AUTH_INFO_CODE:-401}"
    content_type="application/json"
    ;;
  /manifest.webmanifest)
    code=200
    content_type="application/manifest+json"
    ;;
  *)
    code=404
    content_type="text/plain"
    ;;
esac

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
grep -q "GET /actuator/health 200" <<<"$OUTPUT"
grep -q "GET /api/auth/info 401" <<<"$OUTPUT"
grep -q "GET /manifest.webmanifest 200 application/manifest+json" <<<"$OUTPUT"

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

grep -q "expected /api/auth/info to return 401, got 502" <<<"$FAIL_OUTPUT"
