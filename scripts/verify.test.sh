#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TMP_DIR="$(mktemp -d)"
CALL_LOG="$TMP_DIR/calls.log"
trap 'rm -rf "$TMP_DIR"' EXIT

cat > "$TMP_DIR/mvn" <<SH
#!/usr/bin/env bash
set -euo pipefail
printf 'mvn %s\n' "\$*" >> "$CALL_LOG"
SH

cat > "$TMP_DIR/node" <<SH
#!/usr/bin/env bash
set -euo pipefail
printf 'node %s\n' "\$*" >> "$CALL_LOG"
SH

cat > "$TMP_DIR/npm" <<SH
#!/usr/bin/env bash
set -euo pipefail
printf 'npm %s\n' "\$*" >> "$CALL_LOG"
SH

chmod +x "$TMP_DIR/mvn" "$TMP_DIR/node" "$TMP_DIR/npm"

PATH="$TMP_DIR:$PATH" "$ROOT/scripts/verify-backend.sh"
PATH="$TMP_DIR:$PATH" "$ROOT/scripts/verify-frontend.sh"

grep -q '^mvn -B clean verify$' "$CALL_LOG"
grep -q '^node --test ' "$CALL_LOG"
grep -q 'src/views/home/Home.test.js' "$CALL_LOG"
grep -q '^npm run test:unit:coverage$' "$CALL_LOG"
grep -q '^npm run build$' "$CALL_LOG"
