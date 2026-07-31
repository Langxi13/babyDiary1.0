#!/usr/bin/env bash
set -euo pipefail

PASSPHRASE_FILE="${BACKUP_PASSPHRASE_FILE:-/etc/baby-diary/backup-passphrase}"
install -d -m 0700 "$(dirname "$PASSPHRASE_FILE")"
if [ ! -s "$PASSPHRASE_FILE" ]; then
  umask 077
  openssl rand -base64 48 | install -m 0600 /dev/stdin "$PASSPHRASE_FILE"
fi
chmod 0600 "$PASSPHRASE_FILE"
if [ "$(tr -d '\r\n' < "$PASSPHRASE_FILE" | wc -c)" -lt 32 ]; then
  echo "backup passphrase must contain at least 32 characters" >&2
  exit 1
fi
echo "backup passphrase ready"
