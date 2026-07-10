CREATE INDEX idx_diary_user_date_created ON diary (user_id, date, created_at);
CREATE INDEX idx_diary_user_mood_date ON diary (user_id, mood_key, date);
CREATE INDEX idx_diary_image_diary_sort ON diary_image (diary_id, sort, image_id);
CREATE INDEX idx_diary_tag_tag_diary ON diary_tag (tag_id, diary_id);
