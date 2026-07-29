#!/usr/bin/env bash
set -euo pipefail

BACKEND_ENV_FILE="${BACKEND_ENV_FILE:-/etc/baby-diary/backend.env}"
if [ -f "$BACKEND_ENV_FILE" ]; then
  set -a
  . "$BACKEND_ENV_FILE"
  set +a
fi

DB_NAME="${DB_NAME:-${MYSQL_DATABASE:-baby-diary}}"
DB_USERNAME="${MYSQL_USER:-${DB_USERNAME:-}}"
DB_PASSWORD="${MYSQL_PASSWORD:-${DB_PASSWORD:-}}"
DB_HOST="${DB_HOST:-127.0.0.1}"
DB_PORT="${DB_PORT:-3306}"

if [ -z "$DB_USERNAME" ] || [ -z "$DB_PASSWORD" ]; then
  echo "DB_USERNAME and DB_PASSWORD are required" >&2
  exit 1
fi

MYSQL_PWD="$DB_PASSWORD" mysql -h"$DB_HOST" -P"$DB_PORT" -u"$DB_USERNAME" "$DB_NAME" <<'SQL'
SET @user_id := COALESCE((SELECT MIN(user_id) FROM user), 0);
SET @start_date := '2026-01-01';
SET @end_date := '2026-12-31';
SET @group_id := COALESCE((SELECT MIN(group_id) FROM album_group WHERE user_id = @user_id), 0);
SET @album_id := COALESCE((SELECT MIN(album_id) FROM album WHERE user_id = @user_id), 0);

SELECT 'diary-list' AS query_name;
EXPLAIN
SELECT diary_id, user_id, title, date, LEFT(content, 512) AS content, mood_key, content_format, created_at
FROM diary
WHERE user_id = @user_id
  AND date BETWEEN @start_date AND @end_date
ORDER BY date DESC, created_at DESC
LIMIT 5 OFFSET 0;

SELECT 'timeline' AS query_name;
EXPLAIN
SELECT *
FROM diary
WHERE user_id = @user_id
  AND date >= @start_date
  AND date <= @end_date
ORDER BY date DESC, created_at DESC;

SELECT 'album-list' AS query_name;
EXPLAIN
SELECT a.album_id, a.group_id, a.user_id, a.name, a.type, a.sort, a.created_at, a.updated_at
FROM album a
WHERE a.group_id = @group_id
ORDER BY a.sort ASC, a.album_id ASC;

SELECT 'album-detail' AS query_name;
EXPLAIN
SELECT ma.asset_id, dm.diary_id, d.user_id, ma.public_id, am.sort, d.title, d.date
FROM album_media am
INNER JOIN album a ON a.album_id = am.album_id
INNER JOIN media_asset ma ON ma.asset_id = am.asset_id
LEFT JOIN diary_media dm ON dm.asset_id = ma.asset_id
INNER JOIN diary d ON d.diary_id = dm.diary_id
WHERE a.user_id = @user_id
  AND a.album_id = @album_id
ORDER BY am.sort ASC, ma.asset_id ASC
LIMIT 24 OFFSET 0;

SELECT 'favorite-photo-page' AS query_name;
EXPLAIN
SELECT ma.asset_id, dm.diary_id, d.user_id, ma.public_id, dm.sort, d.title, d.date
FROM favorite_media fm
INNER JOIN media_asset ma ON ma.asset_id = fm.asset_id
LEFT JOIN diary_media dm ON dm.asset_id = ma.asset_id
INNER JOIN diary d ON d.diary_id = dm.diary_id
WHERE fm.user_id = @user_id
  AND d.user_id = @user_id
ORDER BY d.date DESC, dm.sort ASC, ma.asset_id DESC
LIMIT 6 OFFSET 0;

SELECT 'ai-report-history' AS query_name;
EXPLAIN
SELECT *
FROM ai_report
WHERE user_id = @user_id
ORDER BY created_at DESC, report_id DESC
LIMIT 10 OFFSET 0;
SQL
