ALTER TABLE `diary_revision`
  ADD COLUMN `public_id` binary(16) NULL AFTER `revision_id`;

UPDATE `diary_revision`
SET `public_id` = UUID_TO_BIN(UUID())
WHERE `public_id` IS NULL;

ALTER TABLE `diary_revision`
  MODIFY COLUMN `public_id` binary(16) NOT NULL,
  ADD UNIQUE KEY `uk_diary_revision_public_id` (`public_id`);

ALTER TABLE `sync_change`
  ADD COLUMN `visibility` varchar(16) CHARACTER SET ascii COLLATE ascii_bin NULL AFTER `revision`,
  ADD COLUMN `owner_id` bigint NULL AFTER `visibility`;

UPDATE `sync_change` c
LEFT JOIN `diary` d
  ON c.`entity_type` = 'DIARY' AND d.`space_id` = c.`space_id` AND d.`public_id` = c.`entity_public_id`
SET c.`visibility` = CASE
      WHEN c.`entity_type` = 'DIARY' THEN COALESCE(d.`visibility`, 'PRIVATE')
      ELSE 'SHARED'
    END,
    c.`owner_id` = CASE
      WHEN c.`entity_type` = 'DIARY'
        AND COALESCE(d.`visibility`, 'PRIVATE') = 'PRIVATE'
        THEN COALESCE(d.`author_id`, c.`actor_id`)
      ELSE NULL
    END
WHERE c.`visibility` IS NULL;

ALTER TABLE `sync_change`
  MODIFY COLUMN `visibility` varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  ADD CONSTRAINT `sync_change_owner_fk`
    FOREIGN KEY (`owner_id`) REFERENCES `account` (`account_id`) ON DELETE RESTRICT,
  ADD CONSTRAINT `ck_sync_change_visibility`
    CHECK (`visibility` IN ('PRIVATE', 'SHARED')),
  ADD CONSTRAINT `ck_sync_change_owner_scope`
    CHECK ((`visibility` = 'PRIVATE' AND `owner_id` IS NOT NULL)
      OR (`visibility` = 'SHARED' AND `owner_id` IS NULL));

CREATE INDEX `idx_ai_report_creator_history`
  ON `ai_report` (`space_id`, `created_by`, `period_type`, `created_at`, `report_id`);

CREATE INDEX `idx_ai_report_creator_history_all`
  ON `ai_report` (`space_id`, `created_by`, `created_at`, `report_id`);
