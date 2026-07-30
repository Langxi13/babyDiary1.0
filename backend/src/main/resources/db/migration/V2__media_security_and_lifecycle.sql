ALTER TABLE `media_asset`
  DROP CHECK `ck_media_asset_status`,
  ADD CONSTRAINT `ck_media_asset_status`
    CHECK (`status` IN ('UPLOADING', 'PROCESSING', 'READY', 'FAILED', 'DELETE_PENDING', 'DELETED')),
  ADD KEY `idx_media_asset_space_owner_scope`
    (`space_id`, `owner_id`, `access_scope`, `library_visible`, `deleted_at`, `asset_id`);

ALTER TABLE `background_job`
  ADD COLUMN `dedupe_key` varchar(190) CHARACTER SET ascii COLLATE ascii_bin NULL AFTER `job_type`,
  ADD UNIQUE KEY `uk_background_job_dedupe` (`job_type`, `dedupe_key`);

ALTER TABLE `diary`
  ADD KEY `idx_diary_space_locked` (`space_id`, `locked`, `deleted_at`, `diary_id`);

ALTER TABLE `ai_album_candidate_media`
  ADD KEY `idx_ai_album_candidate_media_asset` (`space_id`, `asset_id`);
