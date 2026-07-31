ALTER TABLE `media_asset`
  ADD COLUMN `derivative_version` int NOT NULL DEFAULT 0 AFTER `status`,
  ADD CONSTRAINT `ck_media_asset_derivative_version`
    CHECK (`derivative_version` BETWEEN 0 AND 100);

ALTER TABLE `media_variant`
  DROP CHECK `ck_media_variant_type`,
  ADD COLUMN `quality_score` decimal(7,6) NULL AFTER `duration_millis`,
  ADD CONSTRAINT `ck_media_variant_type`
    CHECK (`variant_type` IN ('ORIGINAL', 'THUMBNAIL', 'PREVIEW', 'POSTER', 'WAVEFORM', 'TRANSCODED')),
  ADD CONSTRAINT `ck_media_variant_quality_score`
    CHECK (`quality_score` IS NULL OR (`quality_score` BETWEEN 0 AND 1));
