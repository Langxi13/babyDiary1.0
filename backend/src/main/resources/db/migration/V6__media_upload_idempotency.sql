ALTER TABLE `media_asset`
  ADD COLUMN `client_upload_id` binary(16) NULL AFTER `owner_id`,
  ADD UNIQUE KEY `uk_media_asset_client_upload` (`space_id`, `owner_id`, `client_upload_id`);
