#!/usr/bin/env bash
set -euo pipefail

BACKEND_ENV_FILE="${BACKEND_ENV_FILE:-/etc/baby-diary/backend.env}"
if [ -f "$BACKEND_ENV_FILE" ]; then
  set -a
  . "$BACKEND_ENV_FILE"
  set +a
fi

DB_NAME="${DB_NAME:-${MYSQL_DATABASE:-baby_diary_v3}}"
DB_USERNAME="${MYSQL_USER:-${V3_DB_USERNAME:-${DB_USERNAME:-}}}"
DB_PASSWORD="${MYSQL_PASSWORD:-${V3_DB_PASSWORD:-${DB_PASSWORD:-}}}"
DB_HOST="${DB_HOST:-127.0.0.1}"
DB_PORT="${DB_PORT:-3306}"

if [ -z "$DB_USERNAME" ] || [ -z "$DB_PASSWORD" ]; then
  echo "DB_USERNAME and DB_PASSWORD are required" >&2
  exit 1
fi

MYSQL_PWD="$DB_PASSWORD" mysql -h"$DB_HOST" -P"$DB_PORT" -u"$DB_USERNAME" "$DB_NAME" <<'SQL'
SET @account_id := COALESCE((SELECT MIN(account_id) FROM account WHERE status='ACTIVE'), 0);
SET @space_id := COALESCE((SELECT MIN(space_id) FROM space_member WHERE account_id=@account_id AND status='ACTIVE'), 0);
SET @start_date := '2026-01-01';
SET @end_date := '2026-12-31';
SET @group_id := COALESCE((SELECT MIN(group_id) FROM album_group WHERE space_id=@space_id), 0);
SET @album_id := COALESCE((SELECT MIN(album_id) FROM album WHERE space_id=@space_id AND deleted_at IS NULL), 0);

SELECT 'diary-list' AS query_name;
EXPLAIN
SELECT diary_id,public_id,title,diary_date,LEFT(content_text,512) AS content_text,mood_key,visibility,version
FROM diary
WHERE space_id=@space_id AND deleted_at IS NULL
  AND diary_date BETWEEN @start_date AND @end_date
  AND (visibility='SHARED' OR author_id=@account_id)
ORDER BY diary_date DESC,diary_id DESC
LIMIT 6;

SELECT 'timeline' AS query_name;
EXPLAIN
SELECT YEAR(diary_date) AS diary_year,MONTH(diary_date) AS diary_month,COUNT(*) AS item_count
FROM diary
WHERE space_id=@space_id AND deleted_at IS NULL
  AND (visibility='SHARED' OR author_id=@account_id)
GROUP BY YEAR(diary_date),MONTH(diary_date)
ORDER BY diary_year DESC,diary_month DESC;

SELECT 'album-list' AS query_name;
EXPLAIN
SELECT a.album_id,a.group_id,a.name,a.type,a.sort_order,a.cover_asset_id,a.created_at
FROM album a
WHERE a.space_id=@space_id AND a.deleted_at IS NULL
ORDER BY a.sort_order,a.album_id;

SELECT 'album-detail' AS query_name;
EXPLAIN
SELECT ma.asset_id,ma.public_id,ma.media_type,ma.status,am.position
FROM album_media am
JOIN album a ON a.space_id=am.space_id AND a.album_id=am.album_id
JOIN media_asset ma ON ma.space_id=am.space_id AND ma.asset_id=am.asset_id
WHERE a.space_id=@space_id AND a.album_id=@album_id AND a.deleted_at IS NULL
  AND ma.deleted_at IS NULL AND ma.status='READY'
ORDER BY am.position,ma.asset_id
LIMIT 24 OFFSET 0;

SELECT 'favorite-photo-page' AS query_name;
EXPLAIN
SELECT ma.asset_id,ma.public_id,ma.media_type,fm.created_at
FROM favorite_media fm
JOIN media_asset ma ON ma.space_id=fm.space_id AND ma.asset_id=fm.asset_id
WHERE fm.space_id=@space_id AND fm.account_id=@account_id
  AND ma.deleted_at IS NULL AND ma.status='READY'
ORDER BY fm.created_at DESC,ma.asset_id DESC
LIMIT 6 OFFSET 0;

SELECT 'ai-report-history' AS query_name;
EXPLAIN
SELECT report_id,public_id,period_type,period_start,period_end,diary_count,created_at
FROM ai_report
WHERE space_id=@space_id AND created_by=@account_id
ORDER BY created_at DESC, report_id DESC
LIMIT 10 OFFSET 0;
SQL
