#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="${PROJECT_ROOT:-$(cd "$SCRIPT_DIR/.." && pwd)}"
ALLOWLIST_FILE="${PRIVACY_ALLOWLIST_FILE:-$PROJECT_ROOT/config/privacy-host-allowlist.txt}"
ASSET_ALLOWLIST_FILE="${PRIVACY_ASSET_ALLOWLIST_FILE:-$PROJECT_ROOT/config/public-asset-allowlist.sha256}"
SCAN_HISTORY="${PRIVACY_SCAN_HISTORY:-true}"
SCAN_ROOT="$(mktemp -d)"
trap 'rm -rf "$SCAN_ROOT"' EXIT

if [ ! -r "$ALLOWLIST_FILE" ]; then
  echo "privacy scan: host allowlist is not readable: $ALLOWLIST_FILE" >&2
  exit 1
fi
mapfile -t ALLOWED_HOSTS < <(
  sed -E 's/[[:space:]]*#.*$//; s/^[[:space:]]+//; s/[[:space:]]+$//' "$ALLOWLIST_FILE" \
    | sed '/^$/d' \
    | tr '[:upper:]' '[:lower:]'
)

PROJECT_ROOT="$PROJECT_ROOT" \
PRIVACY_SCAN_HISTORY="$SCAN_HISTORY" \
PRIVACY_ASSET_ALLOWLIST_FILE="$ASSET_ALLOWLIST_FILE" \
  bash "$SCRIPT_DIR/public-asset-scan.sh"

CURRENT_ROOT="$SCAN_ROOT/current"
PROJECT_ROOT="$PROJECT_ROOT" bash "$SCRIPT_DIR/create-scan-snapshot.sh" "$CURRENT_ROOT"

HISTORY_PATCH="$SCAN_ROOT/git-history.patch"
HISTORY_METADATA="$SCAN_ROOT/git-metadata.txt"
: > "$HISTORY_PATCH"
: > "$HISTORY_METADATA"
if [ "$SCAN_HISTORY" = "true" ]; then
  git -C "$PROJECT_ROOT" rev-parse --is-inside-work-tree >/dev/null
  git -C "$PROJECT_ROOT" log --all --format= --no-ext-diff --no-textconv -p -- . > "$HISTORY_PATCH"
  git -C "$PROJECT_ROOT" log --all --format='%an%n%ae%n%cn%n%ce%n%B' > "$HISTORY_METADATA"
  git -C "$PROJECT_ROOT" for-each-ref refs/tags \
    --format='%(taggername)%0a%(taggeremail)%0a%(contents)' >> "$HISTORY_METADATA"
  while IFS= read -r notes_ref; do
    while read -r note_object _; do
      git -C "$PROJECT_ROOT" cat-file blob "$note_object"
      printf '\n'
    done < <(git -C "$PROJECT_ROOT" notes --ref="$notes_ref" list)
  done < <(git -C "$PROJECT_ROOT" for-each-ref refs/notes --format='%(refname)') >> "$HISTORY_METADATA"
fi

failures=0

report_finding() {
  local category="$1"
  local label="$2"
  local file="$3"
  local line="$4"
  local location

  case "$label" in
    current)
      location="${file#"$CURRENT_ROOT/"}:$line"
      ;;
    metadata)
      location="git-metadata:$line"
      ;;
    *)
      location="git-history:$line"
      ;;
  esac

  printf 'privacy scan: %s at %s\n' "$category" "$location" >&2
  failures=$((failures + 1))
}

host_is_allowed() {
  local host="${1,,}"
  local pattern

  host="${host%.}"
  case "$host" in
    ""|*'*'*|*'%'*|*'${'*|127.*|192.0.2.*|198.51.100.*|203.0.113.*)
      return 0
      ;;
  esac

  for pattern in "${ALLOWED_HOSTS[@]}"; do
    case "$host" in
      $pattern)
        return 0
        ;;
    esac
  done

  return 1
}

email_is_allowed() {
  local email="${1,,}"

  case "$email" in
    *@example.com|*@*.example.com|*@users.noreply.github.com|noreply@github.com|support@github.com)
      return 0
      ;;
  esac

  return 1
}

ip_is_allowed() {
  case "$1" in
    0.0.0.0|127.*|10.0.2.2|192.0.2.*|198.51.100.*|203.0.113.*)
      return 0
      ;;
  esac

  return 1
}

path_is_allowed() {
  case "$1" in
    /home/wiremock*)
      return 0
      ;;
  esac

  return 1
}

extract_url_host() {
  local authority="${1#*://}"

  authority="${authority%%/*}"
  authority="${authority%%\?*}"
  authority="${authority%%\#*}"
  authority="${authority##*@}"

  if [[ "$authority" == \[*\]* ]]; then
    authority="${authority#\[}"
    authority="${authority%%\]*}"
  else
    authority="${authority%%:*}"
  fi

  authority="${authority%%\'*}"
  authority="${authority%%\`*}"
  authority="${authority%%,*}"
  authority="${authority%%;*}"

  printf '%s' "${authority,,}"
}

scan_urls() {
  local target="$1"
  local label="$2"
  local file detail line value host
  local pattern='https?://[^[:space:]"<>)]+'

  while IFS= read -r -d '' file && IFS= read -r detail; do
    line="${detail%%:*}"
    value="${detail#*:}"
    host="$(extract_url_host "$value")"
    if ! host_is_allowed "$host"; then
      report_finding "unapproved URL host" "$label" "$file" "$line"
    fi
  done < <(LC_ALL=C grep -rInIHZ -o -E "$pattern" "$target" 2>/dev/null || true)
}

scan_emails() {
  local target="$1"
  local label="$2"
  local file detail line value
  local pattern='[A-Za-z0-9][A-Za-z0-9._%+-]*@[A-Za-z0-9.-]+\.[A-Za-z]{2,}'

  while IFS= read -r -d '' file && IFS= read -r detail; do
    line="${detail%%:*}"
    value="${detail#*:}"
    if ! email_is_allowed "$value"; then
      report_finding "unapproved email address" "$label" "$file" "$line"
    fi
  done < <(LC_ALL=C grep -rInIHZ -o -E "$pattern" "$target" 2>/dev/null || true)
}

scan_ips() {
  local target="$1"
  local label="$2"
  local file detail line value
  local pattern='([0-9]{1,3}\.){3}[0-9]{1,3}'

  while IFS= read -r -d '' file && IFS= read -r detail; do
    line="${detail%%:*}"
    value="${detail#*:}"
    if ! ip_is_allowed "$value"; then
      report_finding "unapproved IP address" "$label" "$file" "$line"
    fi
  done < <(LC_ALL=C grep -rInIHZ -o -E "$pattern" "$target" 2>/dev/null || true)
}

scan_server_paths() {
  local target="$1"
  local label="$2"
  local file detail line value
  local unix_pattern="(^|[[:space:]=\"'():])/(home|Users|var/www|srv|usr/local/(Web-Project|www|apps?)|etc/letsencrypt/(live|archive))/[A-Za-z0-9._~\${}@%+=:,/-]+"
  local windows_pattern="(^|[[:space:]=\"'():])[A-Za-z]:\\\\Users\\\\[A-Za-z0-9._ -]+"

  while IFS= read -r -d '' file && IFS= read -r detail; do
    line="${detail%%:*}"
    value="${detail#*:}"
    if [[ "$value" != /* && ! "$value" =~ ^[A-Za-z]:\\ ]]; then
      value="${value:1}"
    fi
    if ! path_is_allowed "$value"; then
      report_finding "server-specific filesystem path" "$label" "$file" "$line"
    fi
  done < <(LC_ALL=C grep -rInIHZ -o -E "$unix_pattern|$windows_pattern" "$target" 2>/dev/null || true)
}

scan_plain_hosts() {
  local target="$1"
  local label="$2"
  local file detail line value host
  local setting_pattern='(server_name|PUBLIC_URL|CORS_ALLOWED_ORIGINS|HEALTH_CHECK_BASE_URL|APP_URL|DOMAIN|HOSTNAME|MAIL_HOST|SPRING_MAIL_HOST|S3_ENDPOINT|DB_HOST|REDIS_HOST)[[:space:]]*[:=][[:space:]]*([A-Za-z0-9-]+\.)+[A-Za-z]{2,63}'
  local host_pattern='([A-Za-z0-9-]+\.)+[A-Za-z]{2,63}'

  while IFS= read -r -d '' file && IFS= read -r detail; do
    line="${detail%%:*}"
    value="${detail#*:}"
    host="$(printf '%s' "$value" | grep -Eo "$host_pattern" | tail -n 1 | tr '[:upper:]' '[:lower:]')"
    if ! host_is_allowed "$host"; then
      report_finding "unapproved configured host" "$label" "$file" "$line"
    fi
  done < <(LC_ALL=C grep -rInIHZ -o -E "$setting_pattern" "$target" 2>/dev/null || true)
}

scan_personal_identifiers() {
  local target="$1"
  local label="$2"
  local file detail line value category
  local phone_pattern='(^|[^[:alnum:]])1[3-9][0-9]{9}([^[:alnum:]]|$)'
  local id_pattern='(^|[^[:alnum:]])[1-9][0-9]{5}(18|19|20)[0-9]{2}(0[1-9]|1[0-2])(0[1-9]|[12][0-9]|3[01])[0-9]{3}[0-9Xx]([^[:alnum:]]|$)'

  for category in "phone number:$phone_pattern" "national identifier:$id_pattern"; do
    while IFS= read -r -d '' file && IFS= read -r detail; do
      line="${detail%%:*}"
      value="${detail#*:}"
      report_finding "possible ${category%%:*}" "$label" "$file" "$line"
    done < <(LC_ALL=C grep -rInIHZ -o -E "${category#*:}" "$target" 2>/dev/null || true)
  done
}

is_sensitive_path() {
  local path="${1#./}"
  local lower="${path,,}"
  local base="${lower##*/}"

  case "$lower" in
    .env|*/.env|.env.*|*/.env.*)
      case "$lower" in
        *.example)
          return 1
          ;;
      esac
      return 0
      ;;
    *.env|*.pem|*.key|*.crt|*.cer|*.p12|*.pfx|*.jks|*.keystore|*.kdbx|*.sqlite|*.sqlite3|*.db|*.dump|*.bak|*.sql.gz)
      return 0
      ;;
    .private/*|*/.private/*|data/images/*|data/objects/*|backups/*|logs/*)
      return 0
      ;;
    *backup*.sql|*dump*.sql|*database-export*.sql)
      return 0
      ;;
  esac

  case "$base" in
    id_rsa|id_ed25519|credentials.json|service-account*.json|secrets.yml|secrets.yaml|.dockerconfigjson|.htpasswd|.netrc|.npmrc|.pypirc)
      return 0
      ;;
  esac

  return 1
}

scan_sensitive_paths() {
  local path

  while IFS= read -r -d '' path; do
    if is_sensitive_path "$path"; then
      printf 'privacy scan: sensitive file path is tracked or unignored: %s\n' "$path" >&2
      failures=$((failures + 1))
    fi
  done < <(git -C "$PROJECT_ROOT" ls-files -z --cached --others --exclude-standard)

  if [ "$SCAN_HISTORY" = "true" ]; then
    while IFS= read -r -d '' path; do
      [ -n "$path" ] || continue
      if is_sensitive_path "$path"; then
        printf 'privacy scan: sensitive file path exists in Git history (path redacted)\n' >&2
        failures=$((failures + 1))
      fi
    done < <(git -C "$PROJECT_ROOT" log --all --format= --name-only -z -- .)
  fi
}

scan_target() {
  local target="$1"
  local label="$2"

  scan_urls "$target" "$label"
  scan_emails "$target" "$label"
  scan_ips "$target" "$label"
  scan_server_paths "$target" "$label"
  scan_plain_hosts "$target" "$label"
  scan_personal_identifiers "$target" "$label"
}

scan_sensitive_paths
scan_target "$CURRENT_ROOT" current
if [ "$SCAN_HISTORY" = "true" ] && [ -s "$HISTORY_PATCH" ]; then
  scan_target "$HISTORY_PATCH" history
fi
if [ "$SCAN_HISTORY" = "true" ] && [ -s "$HISTORY_METADATA" ]; then
  scan_target "$HISTORY_METADATA" metadata
fi

if [ "$failures" -ne 0 ]; then
  printf 'privacy scan failed with %d finding(s); values were redacted from output\n' "$failures" >&2
  exit 1
fi

echo "privacy scan passed: no private identifiers, infrastructure details, or sensitive paths found"
