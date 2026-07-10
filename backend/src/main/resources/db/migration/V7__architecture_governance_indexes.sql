CREATE INDEX idx_album_photo_album_sort ON album_photo (album_id, sort, image_id);
CREATE INDEX idx_ai_report_user_created_report ON ai_report (user_id, created_at, report_id);
