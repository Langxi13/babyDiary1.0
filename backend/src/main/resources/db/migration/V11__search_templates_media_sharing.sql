ALTER TABLE `diary_space`
  ADD COLUMN `default_visibility` varchar(16) NOT NULL DEFAULT 'SHARED' AFTER `personal_owner_id`,
  ADD COLUMN `storage_quota_bytes` bigint NOT NULL DEFAULT 5368709120 AFTER `default_visibility`;

UPDATE `diary_space` SET `default_visibility` = 'PRIVATE' WHERE `type` = 'PERSONAL';

ALTER TABLE `media_asset`
  ADD COLUMN `original_filename` varchar(255) NULL AFTER `media_type`,
  ADD COLUMN `poster_key` varchar(500) NULL AFTER `thumbnail_key`,
  ADD COLUMN `waveform_key` varchar(500) NULL AFTER `poster_key`,
  ADD COLUMN `transcoded_key` varchar(500) NULL AFTER `waveform_key`,
  ADD COLUMN `ocr_text` mediumtext NULL AFTER `caption`,
  ADD COLUMN `processing_error` varchar(1000) NULL AFTER `status`,
  ADD COLUMN `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP AFTER `created_at`;

CREATE TABLE `diary_template` (
  `template_id` bigint NOT NULL AUTO_INCREMENT,
  `public_id` char(36) NOT NULL,
  `space_id` bigint NULL,
  `owner_user_id` int NULL,
  `template_key` varchar(64) NULL,
  `name` varchar(100) NOT NULL,
  `description` varchar(500) NULL,
  `icon` varchar(32) NULL,
  `prompt_text` varchar(1000) NULL,
  `content_html` mediumtext NOT NULL,
  `builtin` tinyint(1) NOT NULL DEFAULT 0,
  `active` tinyint(1) NOT NULL DEFAULT 1,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`template_id`),
  UNIQUE KEY `uk_diary_template_public_id` (`public_id`),
  UNIQUE KEY `uk_diary_template_builtin_key` (`template_key`),
  KEY `idx_diary_template_space_active` (`space_id`, `active`, `updated_at`),
  CONSTRAINT `diary_template_space_fk` FOREIGN KEY (`space_id`) REFERENCES `diary_space` (`space_id`) ON DELETE CASCADE,
  CONSTRAINT `diary_template_owner_fk` FOREIGN KEY (`owner_user_id`) REFERENCES `user` (`user_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO `diary_template` (`public_id`, `template_key`, `name`, `description`, `icon`, `prompt_text`, `content_html`, `builtin`)
VALUES
  (UUID(), 'daily_moment', '今日小记', '记录今天最值得留下的一件小事', 'Notebook', '今天最想记住的瞬间是什么？', '<h2>今天发生了什么</h2><p></p><h2>想对你说</h2><p></p>', 1),
  (UUID(), 'baby_growth', '成长记录', '记录宝宝的新变化、新能力与陪伴感受', 'Sunny', '今天发现了哪些成长变化？', '<h2>今天的新变化</h2><p></p><h2>我们的感受</h2><p></p><h2>想留给未来的话</h2><p></p>', 1),
  (UUID(), 'couple_memory', '两个人的回忆', '记录一起完成的事情与彼此的感受', 'Connection', '今天你们一起经历了什么？', '<h2>我们一起做了什么</h2><p></p><h2>最打动我的瞬间</h2><p></p>', 1),
  (UUID(), 'travel_log', '旅行手记', '按地点、见闻和照片线索整理旅程', 'Location', '这段旅程最独特的地点和体验是什么？', '<h2>今天到了哪里</h2><p></p><h2>看见与尝到</h2><p></p><h2>旅途片段</h2><p></p>', 1);

CREATE TABLE `search_document` (
  `document_id` bigint NOT NULL AUTO_INCREMENT,
  `space_id` bigint NOT NULL,
  `entity_type` varchar(32) NOT NULL,
  `entity_public_id` char(36) NOT NULL,
  `owner_user_id` int NOT NULL,
  `visibility` varchar(16) NOT NULL,
  `title` varchar(255) NOT NULL,
  `body` mediumtext NOT NULL,
  `document_date` date NULL,
  `source_updated_at` timestamp NOT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`document_id`),
  UNIQUE KEY `uk_search_document_entity` (`entity_type`, `entity_public_id`),
  KEY `idx_search_document_space_date` (`space_id`, `document_date`, `document_id`),
  FULLTEXT KEY `ft_search_document_zh` (`title`, `body`) WITH PARSER ngram,
  CONSTRAINT `search_document_space_fk` FOREIGN KEY (`space_id`) REFERENCES `diary_space` (`space_id`) ON DELETE CASCADE,
  CONSTRAINT `search_document_owner_fk` FOREIGN KEY (`owner_user_id`) REFERENCES `user` (`user_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO `search_document` (`space_id`, `entity_type`, `entity_public_id`, `owner_user_id`, `visibility`, `title`, `body`, `document_date`, `source_updated_at`)
SELECT `space_id`, 'DIARY', `public_id`, `user_id`, `visibility`, `title`, `content`, `date`, `updated_at`
FROM `diary`
WHERE `deleted_at` IS NULL AND `locked` = 0;

CREATE TABLE `space_ai_schedule` (
  `space_id` bigint NOT NULL,
  `weekly_enabled` tinyint(1) NOT NULL DEFAULT 0,
  `monthly_enabled` tinyint(1) NOT NULL DEFAULT 0,
  `annual_enabled` tinyint(1) NOT NULL DEFAULT 0,
  `next_run_at` timestamp NULL,
  `last_run_at` timestamp NULL,
  `updated_by` int NOT NULL,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`space_id`),
  KEY `idx_space_ai_schedule_due` (`next_run_at`),
  CONSTRAINT `space_ai_schedule_space_fk` FOREIGN KEY (`space_id`) REFERENCES `diary_space` (`space_id`) ON DELETE CASCADE,
  CONSTRAINT `space_ai_schedule_user_fk` FOREIGN KEY (`updated_by`) REFERENCES `user` (`user_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `private_share` (
  `share_id` bigint NOT NULL AUTO_INCREMENT,
  `public_id` char(36) NOT NULL,
  `token_hash` char(64) NOT NULL,
  `space_id` bigint NOT NULL,
  `diary_id` int NOT NULL,
  `created_by` int NOT NULL,
  `password_hash` varchar(255) NULL,
  `expires_at` timestamp NOT NULL,
  `max_views` int NULL,
  `view_count` int NOT NULL DEFAULT 0,
  `revoked_at` timestamp NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`share_id`),
  UNIQUE KEY `uk_private_share_public_id` (`public_id`),
  UNIQUE KEY `uk_private_share_token_hash` (`token_hash`),
  KEY `idx_private_share_diary` (`diary_id`, `revoked_at`, `expires_at`),
  CONSTRAINT `private_share_space_fk` FOREIGN KEY (`space_id`) REFERENCES `diary_space` (`space_id`) ON DELETE CASCADE,
  CONSTRAINT `private_share_diary_fk` FOREIGN KEY (`diary_id`) REFERENCES `diary` (`diary_id`) ON DELETE CASCADE,
  CONSTRAINT `private_share_creator_fk` FOREIGN KEY (`created_by`) REFERENCES `user` (`user_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `space_storage_usage` (
  `space_id` bigint NOT NULL,
  `used_bytes` bigint NOT NULL DEFAULT 0,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`space_id`),
  CONSTRAINT `space_storage_usage_space_fk` FOREIGN KEY (`space_id`) REFERENCES `diary_space` (`space_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO `space_storage_usage` (`space_id`, `used_bytes`)
SELECT `space_id`, COALESCE(SUM(`size_bytes`), 0)
FROM `media_asset`
WHERE `deleted_at` IS NULL
GROUP BY `space_id`;

INSERT IGNORE INTO `space_storage_usage` (`space_id`, `used_bytes`)
SELECT `space_id`, 0 FROM `diary_space`;
