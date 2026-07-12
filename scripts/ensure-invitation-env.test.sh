#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT

ENV_FILE="$TMP_DIR/backend.env"
printf '%s\n' 'INVITATION_CODE=bootstrap-only-code' > "$ENV_FILE"

BACKEND_ENV_FILE="$ENV_FILE" "$ROOT/scripts/ensure-invitation-env.sh"

grep -Eq '^INVITATION_CODE_ENCRYPTION_KEY=.{32,}$' "$ENV_FILE"
grep -q '^INVITATION_CODE=bootstrap-only-code$' "$ENV_FILE"
[ "$(stat -c '%a' "$ENV_FILE")" = "600" ]

FIRST_KEY="$(sed -n 's/^INVITATION_CODE_ENCRYPTION_KEY=//p' "$ENV_FILE")"
BACKEND_ENV_FILE="$ENV_FILE" "$ROOT/scripts/ensure-invitation-env.sh"
SECOND_KEY="$(sed -n 's/^INVITATION_CODE_ENCRYPTION_KEY=//p' "$ENV_FILE")"

[ "$FIRST_KEY" = "$SECOND_KEY" ]
[ "$(grep -c '^INVITATION_CODE_ENCRYPTION_KEY=' "$ENV_FILE")" = "1" ]

echo "invitation encryption environment is initialized once"
