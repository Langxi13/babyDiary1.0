ALTER TABLE `media_asset`
  DROP COLUMN `ocr_text`;

ALTER TABLE `background_job`
  DROP CHECK `ck_background_job_type`,
  ADD CONSTRAINT `ck_background_job_type`
    CHECK (`job_type` IN ('AI_REPORT', 'AI_ALBUM', 'MEDIA_PROCESS', 'EXPORT', 'IMPORT', 'STORAGE_GC', 'PUSH_DELIVERY')),
  ADD KEY `idx_background_job_retention` (`completed_at`, `job_id`);

ALTER TABLE `outbox_event`
  ADD COLUMN `actor_id` bigint NULL AFTER `space_id`,
  ADD COLUMN `max_attempts` int NOT NULL DEFAULT 5 AFTER `attempt_count`,
  ADD COLUMN `claimed_at` datetime(6) NULL AFTER `available_at`,
  ADD COLUMN `claimed_by` varchar(100) CHARACTER SET ascii COLLATE ascii_bin NULL AFTER `claimed_at`,
  ADD COLUMN `failed_at` datetime(6) NULL AFTER `processed_at`,
  ADD KEY `idx_outbox_event_claim` (`processed_at`, `failed_at`, `available_at`, `claimed_at`, `event_id`),
  ADD KEY `idx_outbox_event_retention` (`processed_at`, `failed_at`, `event_id`),
  ADD CONSTRAINT `outbox_event_actor_fk`
    FOREIGN KEY (`actor_id`) REFERENCES `account` (`account_id`) ON DELETE RESTRICT,
  ADD CONSTRAINT `ck_outbox_event_max_attempts`
    CHECK (`attempt_count` >= 0 AND `max_attempts` BETWEEN 1 AND 10);

-- V1/V2 wrote audit events before an Outbox consumer existed. Do not replay those historical
-- events as fresh user notifications when the V3 worker starts for the first time.
UPDATE `outbox_event`
SET `processed_at` = UTC_TIMESTAMP(6)
WHERE `processed_at` IS NULL;

CREATE TABLE `sync_retention` (
  `space_id` bigint NOT NULL,
  `baseline_cursor` bigint NOT NULL DEFAULT 0,
  `updated_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`space_id`),
  CONSTRAINT `sync_retention_space_fk`
    FOREIGN KEY (`space_id`) REFERENCES `diary_space` (`space_id`) ON DELETE CASCADE,
  CONSTRAINT `ck_sync_retention_cursor` CHECK (`baseline_cursor` >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE `auth_session`
  ADD KEY `idx_auth_session_retention` (`expires_at`, `revoked_at`, `session_id`);

ALTER TABLE `account_token`
  ADD KEY `idx_account_token_retention` (`expires_at`, `used_at`, `token_id`);

ALTER TABLE `recovery_code`
  ADD KEY `idx_recovery_code_retention` (`used_at`, `recovery_code_id`);

ALTER TABLE `diary`
  ADD KEY `idx_diary_retention` (`deleted_at`, `diary_id`);
