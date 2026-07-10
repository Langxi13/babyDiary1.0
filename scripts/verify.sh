#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="${PROJECT_ROOT:-$(cd "$SCRIPT_DIR/.." && pwd)}"

FRONTEND_TESTS=(
  public/pwaMetadata.test.js
  src/App.test.js
  src/assets/styles/mobileUpload.test.js
  src/components/mobile/mobileNavigation.test.js
  src/components/mobile/MobileAppShell.test.js
  src/components/common/NavBar.test.js
  src/views/home/Home.test.js
  src/views/auth/Profile.test.js
  src/views/diary/Album.test.js
  src/views/diary/AiReports.test.js
  src/views/diary/Anniversaries.test.js
  src/views/diary/Calendar.test.js
  src/views/diary/Timeline.test.js
  src/views/diary/DiaryForm.test.js
  src/views/diary/DiaryList.test.js
  src/stores/auth.test.js
  src/utils/dateDisplay.test.js
  src/utils/imageUrl.test.js
  src/utils/imageUrlUsage.test.js
  src/utils/apiCache.test.js
  src/utils/diaryFormState.test.js
  src/utils/timelineGroups.test.js
  src/utils/shareTargetFiles.test.js
  src/utils/aiReportPeriod.test.js
  src/utils/markdownReport.test.js
)

cd "$PROJECT_ROOT"

source scripts/java-env.sh

bash scripts/health-check.test.sh
bash scripts/runtime-governance-check.test.sh
bash scripts/ensure-image-permissions.test.sh
bash scripts/generate-thumbnails.test.sh
bash scripts/explain-key-queries.test.sh
bash scripts/verify-backup.test.sh

(
  cd backend
  mvn -q clean test
)

(
  cd frontend
  node --test "${FRONTEND_TESTS[@]}"
  npm run build
)
