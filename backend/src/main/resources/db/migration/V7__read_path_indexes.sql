CREATE INDEX idx_diary_space_visibility_date
    ON diary(space_id, deleted_at, visibility, diary_date, diary_id);

CREATE INDEX idx_diary_space_author_date
    ON diary(space_id, author_id, deleted_at, diary_date, diary_id);

CREATE INDEX idx_favorite_space_account_created
    ON favorite_media(space_id, account_id, created_at, asset_id);
