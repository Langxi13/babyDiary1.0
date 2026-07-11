ALTER TABLE `user`
  ADD COLUMN `email` varchar(255) NULL AFTER `username`,
  ADD COLUMN `email_verified` tinyint(1) NOT NULL DEFAULT 0 AFTER `email`,
  ADD COLUMN `system_role` varchar(16) NOT NULL DEFAULT 'USER' AFTER `token_version`,
  ADD COLUMN `timezone` varchar(64) NOT NULL DEFAULT 'Asia/Shanghai' AFTER `system_role`,
  ADD UNIQUE KEY `uk_user_email` (`email`);

UPDATE `user`
SET `system_role` = 'ADMIN'
WHERE `user_id` = (
  SELECT `first_user_id`
  FROM (SELECT MIN(`user_id`) AS `first_user_id` FROM `user`) first_user
);

CREATE TABLE `diary_space` (
  `space_id` bigint NOT NULL AUTO_INCREMENT,
  `public_id` char(36) NOT NULL,
  `name` varchar(100) NOT NULL,
  `type` varchar(16) NOT NULL,
  `created_by` int NOT NULL,
  `personal_owner_id` int NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`space_id`),
  UNIQUE KEY `uk_space_public_id` (`public_id`),
  UNIQUE KEY `uk_space_personal_owner` (`personal_owner_id`),
  KEY `idx_space_created_by` (`created_by`),
  CONSTRAINT `space_created_by_fk` FOREIGN KEY (`created_by`) REFERENCES `user` (`user_id`) ON DELETE RESTRICT,
  CONSTRAINT `space_personal_owner_fk` FOREIGN KEY (`personal_owner_id`) REFERENCES `user` (`user_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `space_member` (
  `space_id` bigint NOT NULL,
  `user_id` int NOT NULL,
  `role` varchar(16) NOT NULL,
  `status` varchar(16) NOT NULL DEFAULT 'ACTIVE',
  `joined_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`space_id`, `user_id`),
  KEY `idx_space_member_user` (`user_id`, `status`),
  CONSTRAINT `space_member_space_fk` FOREIGN KEY (`space_id`) REFERENCES `diary_space` (`space_id`) ON DELETE CASCADE,
  CONSTRAINT `space_member_user_fk` FOREIGN KEY (`user_id`) REFERENCES `user` (`user_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO `diary_space` (`public_id`, `name`, `type`, `created_by`, `personal_owner_id`)
SELECT UUID(), CONCAT(`username`, '的个人空间'), 'PERSONAL', `user_id`, `user_id`
FROM `user`;

INSERT INTO `space_member` (`space_id`, `user_id`, `role`, `status`)
SELECT `space_id`, `personal_owner_id`, 'OWNER', 'ACTIVE'
FROM `diary_space`
WHERE `type` = 'PERSONAL';

ALTER TABLE `diary`
  ADD COLUMN `space_id` bigint NULL AFTER `user_id`,
  ADD COLUMN `public_id` char(36) NULL AFTER `diary_id`,
  ADD COLUMN `visibility` varchar(16) NOT NULL DEFAULT 'PRIVATE' AFTER `content_format`,
  ADD COLUMN `locked` tinyint(1) NOT NULL DEFAULT 0 AFTER `visibility`,
  ADD COLUMN `version` int NOT NULL DEFAULT 1 AFTER `locked`,
  ADD COLUMN `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP AFTER `created_at`,
  ADD COLUMN `deleted_at` timestamp NULL AFTER `updated_at`;

UPDATE `diary` d
JOIN `diary_space` s ON s.`personal_owner_id` = d.`user_id`
SET d.`space_id` = s.`space_id`, d.`public_id` = UUID(), d.`visibility` = 'PRIVATE';

ALTER TABLE `diary`
  MODIFY COLUMN `space_id` bigint NOT NULL,
  MODIFY COLUMN `public_id` char(36) NOT NULL,
  ADD UNIQUE KEY `uk_diary_public_id` (`public_id`),
  ADD KEY `idx_diary_space_date` (`space_id`, `deleted_at`, `date`, `created_at`),
  ADD KEY `idx_diary_space_visibility` (`space_id`, `visibility`, `user_id`, `deleted_at`),
  ADD CONSTRAINT `diary_space_fk` FOREIGN KEY (`space_id`) REFERENCES `diary_space` (`space_id`) ON DELETE CASCADE;

ALTER TABLE `tag`
  ADD COLUMN `space_id` bigint NULL AFTER `user_id`;
UPDATE `tag` t JOIN `diary_space` s ON s.`personal_owner_id` = t.`user_id` SET t.`space_id` = s.`space_id`;
ALTER TABLE `tag`
  MODIFY COLUMN `space_id` bigint NOT NULL,
  ADD KEY `idx_tag_user` (`user_id`),
  DROP INDEX `uk_tag_user_name`,
  ADD UNIQUE KEY `uk_tag_space_name` (`space_id`, `name`),
  ADD CONSTRAINT `tag_space_fk` FOREIGN KEY (`space_id`) REFERENCES `diary_space` (`space_id`) ON DELETE CASCADE;

ALTER TABLE `anniversary` ADD COLUMN `space_id` bigint NULL AFTER `user_id`;
UPDATE `anniversary` a JOIN `diary_space` s ON s.`personal_owner_id` = a.`user_id` SET a.`space_id` = s.`space_id`;
ALTER TABLE `anniversary`
  MODIFY COLUMN `space_id` bigint NOT NULL,
  ADD KEY `idx_anniversary_space_date` (`space_id`, `date`),
  ADD CONSTRAINT `anniversary_space_fk` FOREIGN KEY (`space_id`) REFERENCES `diary_space` (`space_id`) ON DELETE CASCADE;

ALTER TABLE `diary_draft` ADD COLUMN `space_id` bigint NULL AFTER `user_id`;
UPDATE `diary_draft` d JOIN `diary_space` s ON s.`personal_owner_id` = d.`user_id` SET d.`space_id` = s.`space_id`;
ALTER TABLE `diary_draft`
  MODIFY COLUMN `space_id` bigint NOT NULL,
  ADD KEY `idx_draft_space_user_updated` (`space_id`, `user_id`, `updated_at`),
  ADD CONSTRAINT `draft_space_fk` FOREIGN KEY (`space_id`) REFERENCES `diary_space` (`space_id`) ON DELETE CASCADE;

ALTER TABLE `album_group` ADD COLUMN `space_id` bigint NULL AFTER `user_id`;
UPDATE `album_group` g JOIN `diary_space` s ON s.`personal_owner_id` = g.`user_id` SET g.`space_id` = s.`space_id`;
ALTER TABLE `album_group`
  MODIFY COLUMN `space_id` bigint NOT NULL,
  ADD KEY `idx_album_group_space_sort` (`space_id`, `sort`, `group_id`),
  ADD CONSTRAINT `album_group_space_fk_v2` FOREIGN KEY (`space_id`) REFERENCES `diary_space` (`space_id`) ON DELETE CASCADE;

ALTER TABLE `album` ADD COLUMN `space_id` bigint NULL AFTER `user_id`;
UPDATE `album` a JOIN `diary_space` s ON s.`personal_owner_id` = a.`user_id` SET a.`space_id` = s.`space_id`;
ALTER TABLE `album`
  MODIFY COLUMN `space_id` bigint NOT NULL,
  ADD KEY `idx_album_space_type` (`space_id`, `type`),
  ADD CONSTRAINT `album_space_fk_v2` FOREIGN KEY (`space_id`) REFERENCES `diary_space` (`space_id`) ON DELETE CASCADE;

ALTER TABLE `ai_report`
  ADD COLUMN `space_id` bigint NULL AFTER `user_id`,
  ADD COLUMN `scope` varchar(16) NOT NULL DEFAULT 'PERSONAL' AFTER `space_id`;
UPDATE `ai_report` r JOIN `diary_space` s ON s.`personal_owner_id` = r.`user_id` SET r.`space_id` = s.`space_id`;
ALTER TABLE `ai_report`
  MODIFY COLUMN `space_id` bigint NOT NULL,
  ADD KEY `idx_ai_report_space_created` (`space_id`, `created_at`, `report_id`),
  ADD CONSTRAINT `ai_report_space_fk` FOREIGN KEY (`space_id`) REFERENCES `diary_space` (`space_id`) ON DELETE CASCADE;

ALTER TABLE `ai_album_proposal` ADD COLUMN `space_id` bigint NULL AFTER `user_id`;
UPDATE `ai_album_proposal` p JOIN `diary_space` s ON s.`personal_owner_id` = p.`user_id` SET p.`space_id` = s.`space_id`;
ALTER TABLE `ai_album_proposal`
  MODIFY COLUMN `space_id` bigint NOT NULL,
  ADD KEY `idx_ai_album_proposal_space_status` (`space_id`, `status`, `created_at`),
  ADD CONSTRAINT `ai_album_proposal_space_fk` FOREIGN KEY (`space_id`) REFERENCES `diary_space` (`space_id`) ON DELETE CASCADE;
