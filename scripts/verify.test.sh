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

PATH="$TMP_DIR:$PATH" "$ROOT/scripts/verify.sh"

grep -q '^mvn -q clean test$' "$CALL_LOG"
grep -q '^node --test public/pwaMetadata.test.js src/App.test.js src/assets/styles/mobileUpload.test.js src/components/mobile/mobileNavigation.test.js src/components/mobile/MobileAppShell.test.js src/components/common/NavBar.test.js src/views/auth/Profile.test.js src/views/diary/Album.test.js src/views/diary/AiReports.test.js src/views/diary/Anniversaries.test.js src/views/diary/Calendar.test.js src/views/diary/Timeline.test.js src/views/diary/DiaryForm.test.js src/views/diary/DiaryList.test.js src/stores/auth.test.js src/utils/dateDisplay.test.js src/utils/imageUrl.test.js src/utils/imageUrlUsage.test.js src/utils/apiCache.test.js src/utils/diaryFormState.test.js src/utils/timelineGroups.test.js src/utils/shareTargetFiles.test.js src/utils/aiReportPeriod.test.js src/utils/markdownReport.test.js$' "$CALL_LOG"
grep -q '^npm run build$' "$CALL_LOG"
