#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="${PROJECT_ROOT:-$(cd "$SCRIPT_DIR/.." && pwd)}"
ENV_FILE="${STAGING_ENV_FILE:-$PROJECT_ROOT/.env.staging}"
PROJECT_NAME="${STAGING_PROJECT_NAME:-baby-diary-performance}"

set -a
source "$ENV_FILE"
set +a

COMPOSE=(docker compose --env-file "$ENV_FILE" -p "$PROJECT_NAME" -f "$PROJECT_ROOT/compose.staging.yaml")
"${COMPOSE[@]}" exec -T mysql mysql -uroot -p"$MYSQL_ROOT_PASSWORD" baby_diary <<'SQL'
EXPLAIN ANALYZE
SELECT diary_id,diary_date,title
FROM diary
WHERE space_id=(SELECT s.space_id FROM diary_space s JOIN account a ON a.account_id=s.personal_owner_id WHERE a.username='performance-reader')
  AND deleted_at IS NULL AND (visibility='SHARED' OR author_id=(SELECT account_id FROM account WHERE username='performance-reader'))
ORDER BY diary_date DESC,diary_id DESC LIMIT 20;

EXPLAIN ANALYZE
SELECT YEAR(diary_date),MONTH(diary_date),COUNT(*)
FROM diary
WHERE space_id=(SELECT s.space_id FROM diary_space s JOIN account a ON a.account_id=s.personal_owner_id WHERE a.username='performance-reader')
  AND deleted_at IS NULL
GROUP BY YEAR(diary_date),MONTH(diary_date)
ORDER BY YEAR(diary_date) DESC,MONTH(diary_date) DESC;

EXPLAIN ANALYZE
SELECT a.asset_id
FROM media_asset a
WHERE a.space_id=(SELECT s.space_id FROM diary_space s JOIN account ac ON ac.account_id=s.personal_owner_id WHERE ac.username='performance-reader')
  AND a.media_type='IMAGE' AND a.library_visible=true AND a.deleted_at IS NULL AND a.status='READY'
ORDER BY a.created_at DESC,a.asset_id DESC LIMIT 24;
SQL
