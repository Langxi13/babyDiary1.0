CREATE TABLE `account` (
  `account_id` bigint NOT NULL AUTO_INCREMENT,
  `public_id` binary(16) NOT NULL,
  `username` varchar(100) COLLATE utf8mb4_bin NOT NULL,
  `password_hash` varchar(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `email` varchar(255) COLLATE utf8mb4_bin NULL,
  `email_verified` boolean NOT NULL DEFAULT false,
  `system_role` varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'USER',
  `timezone` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'Asia/Shanghai',
  `token_version` int NOT NULL DEFAULT 0,
  `status` varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'ACTIVE',
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  `deleted_at` datetime(6) NULL,
  PRIMARY KEY (`account_id`),
  UNIQUE KEY `uk_account_public_id` (`public_id`),
  UNIQUE KEY `uk_account_username` (`username`),
  UNIQUE KEY `uk_account_email` (`email`),
  CONSTRAINT `ck_account_role` CHECK (`system_role` IN ('USER', 'ADMIN')),
  CONSTRAINT `ck_account_status` CHECK (`status` IN ('ACTIVE', 'DISABLED', 'DELETED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `diary_space` (
  `space_id` bigint NOT NULL AUTO_INCREMENT,
  `public_id` binary(16) NOT NULL,
  `name` varchar(100) NOT NULL,
  `type` varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `created_by` bigint NOT NULL,
  `personal_owner_id` bigint NULL,
  `default_visibility` varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'SHARED',
  `storage_quota_bytes` bigint NOT NULL DEFAULT 5368709120,
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  `deleted_at` datetime(6) NULL,
  PRIMARY KEY (`space_id`),
  UNIQUE KEY `uk_space_public_id` (`public_id`),
  UNIQUE KEY `uk_space_personal_owner` (`personal_owner_id`),
  KEY `idx_space_created_by` (`created_by`, `deleted_at`),
  CONSTRAINT `space_created_by_fk` FOREIGN KEY (`created_by`) REFERENCES `account` (`account_id`) ON DELETE RESTRICT,
  CONSTRAINT `space_personal_owner_fk` FOREIGN KEY (`personal_owner_id`) REFERENCES `account` (`account_id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_space_type` CHECK (`type` IN ('PERSONAL', 'SHARED')),
  CONSTRAINT `ck_space_visibility` CHECK (`default_visibility` IN ('PRIVATE', 'SHARED')),
  CONSTRAINT `ck_space_quota` CHECK (`storage_quota_bytes` >= 0),
  CONSTRAINT `ck_space_personal_owner` CHECK ((`type` = 'PERSONAL' AND `personal_owner_id` IS NOT NULL) OR (`type` = 'SHARED' AND `personal_owner_id` IS NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `space_member` (
  `space_id` bigint NOT NULL,
  `account_id` bigint NOT NULL,
  `role` varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `status` varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'ACTIVE',
  `joined_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`space_id`, `account_id`),
  KEY `idx_space_member_account` (`account_id`, `status`, `space_id`),
  CONSTRAINT `space_member_space_fk` FOREIGN KEY (`space_id`) REFERENCES `diary_space` (`space_id`) ON DELETE CASCADE,
  CONSTRAINT `space_member_account_fk` FOREIGN KEY (`account_id`) REFERENCES `account` (`account_id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_space_member_role` CHECK (`role` IN ('OWNER', 'ADMIN', 'MEMBER', 'VIEWER')),
  CONSTRAINT `ck_space_member_status` CHECK (`status` IN ('ACTIVE', 'SUSPENDED', 'LEFT'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `auth_session` (
  `session_id` bigint NOT NULL AUTO_INCREMENT,
  `public_id` binary(16) NOT NULL,
  `account_id` bigint NOT NULL,
  `refresh_token_hash` binary(32) NOT NULL,
  `device_name` varchar(160) NULL,
  `user_agent` varchar(500) NULL,
  `ip_address` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
  `expires_at` datetime(6) NOT NULL,
  `last_seen_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `revoked_at` datetime(6) NULL,
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`session_id`),
  UNIQUE KEY `uk_auth_session_public_id` (`public_id`),
  UNIQUE KEY `uk_auth_session_refresh_hash` (`refresh_token_hash`),
  KEY `idx_auth_session_account_active` (`account_id`, `revoked_at`, `expires_at`),
  CONSTRAINT `auth_session_account_fk` FOREIGN KEY (`account_id`) REFERENCES `account` (`account_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `account_token` (
  `token_id` bigint NOT NULL AUTO_INCREMENT,
  `account_id` bigint NOT NULL,
  `type` varchar(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `token_hash` binary(32) NOT NULL,
  `expires_at` datetime(6) NOT NULL,
  `used_at` datetime(6) NULL,
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`token_id`),
  UNIQUE KEY `uk_account_token_hash` (`token_hash`),
  KEY `idx_account_token_account_type` (`account_id`, `type`, `used_at`, `expires_at`),
  CONSTRAINT `account_token_account_fk` FOREIGN KEY (`account_id`) REFERENCES `account` (`account_id`) ON DELETE CASCADE,
  CONSTRAINT `ck_account_token_type` CHECK (`type` IN ('EMAIL_VERIFY', 'PASSWORD_RESET'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `recovery_code` (
  `recovery_code_id` bigint NOT NULL AUTO_INCREMENT,
  `account_id` bigint NOT NULL,
  `code_hash` binary(32) NOT NULL,
  `used_at` datetime(6) NULL,
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`recovery_code_id`),
  UNIQUE KEY `uk_recovery_code_hash` (`code_hash`),
  KEY `idx_recovery_code_account` (`account_id`, `used_at`),
  CONSTRAINT `recovery_code_account_fk` FOREIGN KEY (`account_id`) REFERENCES `account` (`account_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `system_invitation_config` (
  `config_id` bigint NOT NULL,
  `encrypted_code` text CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `updated_by` bigint NULL,
  `updated_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`config_id`),
  CONSTRAINT `system_invitation_updated_by_fk` FOREIGN KEY (`updated_by`) REFERENCES `account` (`account_id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `space_invitation` (
  `invitation_id` bigint NOT NULL AUTO_INCREMENT,
  `public_id` binary(16) NOT NULL,
  `space_id` bigint NOT NULL,
  `invited_by` bigint NOT NULL,
  `email` varchar(255) COLLATE utf8mb4_bin NULL,
  `token_hash` binary(32) NOT NULL,
  `role` varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'MEMBER',
  `status` varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'PENDING',
  `expires_at` datetime(6) NOT NULL,
  `accepted_by` bigint NULL,
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`invitation_id`),
  UNIQUE KEY `uk_space_invitation_public_id` (`public_id`),
  UNIQUE KEY `uk_space_invitation_token` (`token_hash`),
  KEY `idx_space_invitation_status` (`space_id`, `status`, `expires_at`),
  CONSTRAINT `space_invitation_space_fk` FOREIGN KEY (`space_id`) REFERENCES `diary_space` (`space_id`) ON DELETE CASCADE,
  CONSTRAINT `space_invitation_inviter_fk` FOREIGN KEY (`invited_by`) REFERENCES `account` (`account_id`) ON DELETE RESTRICT,
  CONSTRAINT `space_invitation_acceptor_fk` FOREIGN KEY (`accepted_by`) REFERENCES `account` (`account_id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_space_invitation_role` CHECK (`role` IN ('ADMIN', 'MEMBER', 'VIEWER')),
  CONSTRAINT `ck_space_invitation_status` CHECK (`status` IN ('PENDING', 'ACCEPTED', 'REVOKED', 'EXPIRED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `tag` (
  `tag_id` bigint NOT NULL AUTO_INCREMENT,
  `public_id` binary(16) NOT NULL,
  `space_id` bigint NOT NULL,
  `name` varchar(32) NOT NULL,
  `color` char(7) CHARACTER SET ascii COLLATE ascii_bin NULL,
  `created_by` bigint NOT NULL,
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`tag_id`),
  UNIQUE KEY `uk_tag_public_id` (`public_id`),
  UNIQUE KEY `uk_tag_space_name` (`space_id`, `name`),
  UNIQUE KEY `uk_tag_space_id` (`space_id`, `tag_id`),
  CONSTRAINT `tag_space_fk` FOREIGN KEY (`space_id`) REFERENCES `diary_space` (`space_id`) ON DELETE CASCADE,
  CONSTRAINT `tag_creator_fk` FOREIGN KEY (`created_by`) REFERENCES `account` (`account_id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_tag_color` CHECK (`color` IS NULL OR `color` REGEXP '^#[0-9A-Fa-f]{6}$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `diary` (
  `diary_id` bigint NOT NULL AUTO_INCREMENT,
  `public_id` binary(16) NOT NULL,
  `space_id` bigint NOT NULL,
  `author_id` bigint NOT NULL,
  `title` varchar(255) NOT NULL,
  `diary_date` date NOT NULL,
  `content_html` mediumtext NOT NULL,
  `content_text` mediumtext NOT NULL,
  `mood_key` varchar(32) CHARACTER SET ascii COLLATE ascii_bin NULL,
  `visibility` varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'PRIVATE',
  `locked` boolean NOT NULL DEFAULT false,
  `version` int NOT NULL DEFAULT 1,
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  `deleted_at` datetime(6) NULL,
  PRIMARY KEY (`diary_id`),
  UNIQUE KEY `uk_diary_public_id` (`public_id`),
  UNIQUE KEY `uk_diary_space_id` (`space_id`, `diary_id`),
  KEY `idx_diary_space_date` (`space_id`, `deleted_at`, `diary_date`, `diary_id`),
  KEY `idx_diary_space_mood` (`space_id`, `deleted_at`, `mood_key`, `diary_date`),
  KEY `idx_diary_author` (`author_id`, `deleted_at`, `diary_date`),
  FULLTEXT KEY `ft_diary_zh` (`title`, `content_text`) WITH PARSER ngram,
  CONSTRAINT `diary_space_fk` FOREIGN KEY (`space_id`) REFERENCES `diary_space` (`space_id`) ON DELETE CASCADE,
  CONSTRAINT `diary_author_fk` FOREIGN KEY (`author_id`) REFERENCES `account` (`account_id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_diary_visibility` CHECK (`visibility` IN ('PRIVATE', 'SHARED')),
  CONSTRAINT `ck_diary_version` CHECK (`version` > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `diary_tag` (
  `space_id` bigint NOT NULL,
  `diary_id` bigint NOT NULL,
  `tag_id` bigint NOT NULL,
  PRIMARY KEY (`diary_id`, `tag_id`),
  KEY `idx_diary_tag_tag` (`space_id`, `tag_id`, `diary_id`),
  CONSTRAINT `diary_tag_diary_fk` FOREIGN KEY (`space_id`, `diary_id`) REFERENCES `diary` (`space_id`, `diary_id`) ON DELETE CASCADE,
  CONSTRAINT `diary_tag_tag_fk` FOREIGN KEY (`space_id`, `tag_id`) REFERENCES `tag` (`space_id`, `tag_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `diary_revision` (
  `revision_id` bigint NOT NULL AUTO_INCREMENT,
  `diary_id` bigint NOT NULL,
  `version` int NOT NULL,
  `editor_id` bigint NOT NULL,
  `snapshot` json NOT NULL,
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`revision_id`),
  UNIQUE KEY `uk_diary_revision_version` (`diary_id`, `version`),
  KEY `idx_diary_revision_created` (`diary_id`, `created_at`),
  CONSTRAINT `diary_revision_diary_fk` FOREIGN KEY (`diary_id`) REFERENCES `diary` (`diary_id`) ON DELETE CASCADE,
  CONSTRAINT `diary_revision_editor_fk` FOREIGN KEY (`editor_id`) REFERENCES `account` (`account_id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_diary_revision_version` CHECK (`version` > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `diary_draft` (
  `draft_id` bigint NOT NULL AUTO_INCREMENT,
  `public_id` binary(16) NOT NULL,
  `space_id` bigint NOT NULL,
  `owner_id` bigint NOT NULL,
  `diary_id` bigint NULL,
  `draft_key` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `payload` json NOT NULL,
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`draft_id`),
  UNIQUE KEY `uk_diary_draft_public_id` (`public_id`),
  UNIQUE KEY `uk_diary_draft_owner_key` (`owner_id`, `draft_key`),
  KEY `idx_diary_draft_space_owner` (`space_id`, `owner_id`, `updated_at`),
  CONSTRAINT `diary_draft_space_fk` FOREIGN KEY (`space_id`) REFERENCES `diary_space` (`space_id`) ON DELETE CASCADE,
  CONSTRAINT `diary_draft_owner_fk` FOREIGN KEY (`owner_id`) REFERENCES `account` (`account_id`) ON DELETE RESTRICT,
  CONSTRAINT `diary_draft_diary_fk` FOREIGN KEY (`diary_id`) REFERENCES `diary` (`diary_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `diary_comment` (
  `comment_id` bigint NOT NULL AUTO_INCREMENT,
  `public_id` binary(16) NOT NULL,
  `diary_id` bigint NOT NULL,
  `author_id` bigint NOT NULL,
  `content` varchar(2000) NOT NULL,
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  `deleted_at` datetime(6) NULL,
  PRIMARY KEY (`comment_id`),
  UNIQUE KEY `uk_diary_comment_public_id` (`public_id`),
  KEY `idx_diary_comment_diary` (`diary_id`, `deleted_at`, `created_at`),
  CONSTRAINT `diary_comment_diary_fk` FOREIGN KEY (`diary_id`) REFERENCES `diary` (`diary_id`) ON DELETE CASCADE,
  CONSTRAINT `diary_comment_author_fk` FOREIGN KEY (`author_id`) REFERENCES `account` (`account_id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `diary_reaction` (
  `diary_id` bigint NOT NULL,
  `account_id` bigint NOT NULL,
  `emoji` varchar(16) NOT NULL,
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`diary_id`, `account_id`, `emoji`),
  CONSTRAINT `diary_reaction_diary_fk` FOREIGN KEY (`diary_id`) REFERENCES `diary` (`diary_id`) ON DELETE CASCADE,
  CONSTRAINT `diary_reaction_account_fk` FOREIGN KEY (`account_id`) REFERENCES `account` (`account_id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `diary_template` (
  `template_id` bigint NOT NULL AUTO_INCREMENT,
  `public_id` binary(16) NOT NULL,
  `space_id` bigint NULL,
  `owner_id` bigint NULL,
  `template_key` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
  `name` varchar(100) NOT NULL,
  `description` varchar(500) NULL,
  `icon` varchar(32) CHARACTER SET ascii COLLATE ascii_bin NULL,
  `prompt_text` varchar(1000) NULL,
  `content_html` mediumtext NOT NULL,
  `builtin` boolean NOT NULL DEFAULT false,
  `active` boolean NOT NULL DEFAULT true,
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`template_id`),
  UNIQUE KEY `uk_diary_template_public_id` (`public_id`),
  UNIQUE KEY `uk_diary_template_builtin_key` (`template_key`),
  KEY `idx_diary_template_space_active` (`space_id`, `active`, `updated_at`),
  CONSTRAINT `diary_template_space_fk` FOREIGN KEY (`space_id`) REFERENCES `diary_space` (`space_id`) ON DELETE CASCADE,
  CONSTRAINT `diary_template_owner_fk` FOREIGN KEY (`owner_id`) REFERENCES `account` (`account_id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_diary_template_ownership` CHECK ((`builtin` = true AND `space_id` IS NULL AND `owner_id` IS NULL) OR (`builtin` = false AND `space_id` IS NOT NULL AND `owner_id` IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `media_asset` (
  `asset_id` bigint NOT NULL AUTO_INCREMENT,
  `public_id` binary(16) NOT NULL,
  `space_id` bigint NOT NULL,
  `owner_id` bigint NOT NULL,
  `media_type` varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `original_filename` varchar(255) NULL,
  `caption` varchar(500) NULL,
  `ocr_text` mediumtext NULL,
  `taken_at` datetime(6) NULL,
  `location_name` varchar(255) NULL,
  `latitude` decimal(10,7) NULL,
  `longitude` decimal(10,7) NULL,
  `access_scope` varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'LINKED',
  `library_visible` boolean NOT NULL DEFAULT true,
  `status` varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'PROCESSING',
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  `deleted_at` datetime(6) NULL,
  PRIMARY KEY (`asset_id`),
  UNIQUE KEY `uk_media_asset_public_id` (`public_id`),
  UNIQUE KEY `uk_media_asset_space_id` (`space_id`, `asset_id`),
  KEY `idx_media_asset_space_library` (`space_id`, `library_visible`, `media_type`, `deleted_at`, `taken_at`, `asset_id`),
  CONSTRAINT `media_asset_space_fk` FOREIGN KEY (`space_id`) REFERENCES `diary_space` (`space_id`) ON DELETE CASCADE,
  CONSTRAINT `media_asset_owner_fk` FOREIGN KEY (`owner_id`) REFERENCES `account` (`account_id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_media_asset_type` CHECK (`media_type` IN ('IMAGE', 'VIDEO', 'AUDIO')),
  CONSTRAINT `ck_media_asset_scope` CHECK (`access_scope` IN ('LINKED', 'SPACE')),
  CONSTRAINT `ck_media_asset_status` CHECK (`status` IN ('UPLOADING', 'PROCESSING', 'READY', 'FAILED')),
  CONSTRAINT `ck_media_asset_latitude` CHECK (`latitude` IS NULL OR `latitude` BETWEEN -90 AND 90),
  CONSTRAINT `ck_media_asset_longitude` CHECK (`longitude` IS NULL OR `longitude` BETWEEN -180 AND 180)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `media_variant` (
  `variant_id` bigint NOT NULL AUTO_INCREMENT,
  `asset_id` bigint NOT NULL,
  `variant_type` varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `profile` varchar(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'default',
  `storage_provider` varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `storage_key` varchar(700) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `content_type` varchar(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `size_bytes` bigint NOT NULL DEFAULT 0,
  `checksum_sha256` binary(32) NULL,
  `width` int NULL,
  `height` int NULL,
  `duration_millis` bigint NULL,
  `status` varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'PENDING',
  `processing_error` varchar(1000) NULL,
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  `deleted_at` datetime(6) NULL,
  PRIMARY KEY (`variant_id`),
  UNIQUE KEY `uk_media_variant_asset_profile` (`asset_id`, `variant_type`, `profile`),
  UNIQUE KEY `uk_media_variant_storage` (`storage_provider`, `storage_key`),
  KEY `idx_media_variant_status` (`status`, `updated_at`),
  CONSTRAINT `media_variant_asset_fk` FOREIGN KEY (`asset_id`) REFERENCES `media_asset` (`asset_id`) ON DELETE CASCADE,
  CONSTRAINT `ck_media_variant_type` CHECK (`variant_type` IN ('ORIGINAL', 'THUMBNAIL', 'POSTER', 'WAVEFORM', 'TRANSCODED')),
  CONSTRAINT `ck_media_variant_status` CHECK (`status` IN ('PENDING', 'PROCESSING', 'READY', 'FAILED')),
  CONSTRAINT `ck_media_variant_size` CHECK (`size_bytes` >= 0),
  CONSTRAINT `ck_media_variant_dimensions` CHECK ((`width` IS NULL OR `width` > 0) AND (`height` IS NULL OR `height` > 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `diary_media` (
  `space_id` bigint NOT NULL,
  `diary_id` bigint NOT NULL,
  `asset_id` bigint NOT NULL,
  `position` int NOT NULL,
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`diary_id`, `asset_id`),
  UNIQUE KEY `uk_diary_media_position` (`diary_id`, `position`),
  KEY `idx_diary_media_asset` (`space_id`, `asset_id`),
  CONSTRAINT `diary_media_diary_fk` FOREIGN KEY (`space_id`, `diary_id`) REFERENCES `diary` (`space_id`, `diary_id`) ON DELETE CASCADE,
  CONSTRAINT `diary_media_asset_fk` FOREIGN KEY (`space_id`, `asset_id`) REFERENCES `media_asset` (`space_id`, `asset_id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_diary_media_position` CHECK (`position` >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `album_group` (
  `group_id` bigint NOT NULL AUTO_INCREMENT,
  `public_id` binary(16) NOT NULL,
  `space_id` bigint NOT NULL,
  `name` varchar(100) NOT NULL,
  `sort_order` int NOT NULL DEFAULT 0,
  `created_by` bigint NOT NULL,
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`group_id`),
  UNIQUE KEY `uk_album_group_public_id` (`public_id`),
  UNIQUE KEY `uk_album_group_space_id` (`space_id`, `group_id`),
  UNIQUE KEY `uk_album_group_space_name` (`space_id`, `name`),
  KEY `idx_album_group_space_sort` (`space_id`, `sort_order`, `group_id`),
  CONSTRAINT `album_group_space_fk` FOREIGN KEY (`space_id`) REFERENCES `diary_space` (`space_id`) ON DELETE CASCADE,
  CONSTRAINT `album_group_creator_fk` FOREIGN KEY (`created_by`) REFERENCES `account` (`account_id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `album` (
  `album_id` bigint NOT NULL AUTO_INCREMENT,
  `public_id` binary(16) NOT NULL,
  `space_id` bigint NOT NULL,
  `group_id` bigint NULL,
  `created_by` bigint NOT NULL,
  `name` varchar(100) NOT NULL,
  `description` text NULL,
  `type` varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `cover_asset_id` bigint NULL,
  `sort_order` int NOT NULL DEFAULT 0,
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  `deleted_at` datetime(6) NULL,
  PRIMARY KEY (`album_id`),
  UNIQUE KEY `uk_album_public_id` (`public_id`),
  UNIQUE KEY `uk_album_space_id` (`space_id`, `album_id`),
  KEY `idx_album_space_sort` (`space_id`, `deleted_at`, `sort_order`, `album_id`),
  KEY `idx_album_group` (`space_id`, `group_id`, `sort_order`),
  KEY `idx_album_cover` (`space_id`, `cover_asset_id`),
  CONSTRAINT `album_space_fk` FOREIGN KEY (`space_id`) REFERENCES `diary_space` (`space_id`) ON DELETE CASCADE,
  CONSTRAINT `album_group_fk` FOREIGN KEY (`space_id`, `group_id`) REFERENCES `album_group` (`space_id`, `group_id`) ON DELETE RESTRICT,
  CONSTRAINT `album_creator_fk` FOREIGN KEY (`created_by`) REFERENCES `account` (`account_id`) ON DELETE RESTRICT,
  CONSTRAINT `album_cover_fk` FOREIGN KEY (`space_id`, `cover_asset_id`) REFERENCES `media_asset` (`space_id`, `asset_id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_album_type` CHECK (`type` IN ('CUSTOM', 'AI'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `album_media` (
  `space_id` bigint NOT NULL,
  `album_id` bigint NOT NULL,
  `asset_id` bigint NOT NULL,
  `position` int NOT NULL,
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`album_id`, `asset_id`),
  UNIQUE KEY `uk_album_media_position` (`album_id`, `position`),
  KEY `idx_album_media_asset` (`space_id`, `asset_id`),
  CONSTRAINT `album_media_album_fk` FOREIGN KEY (`space_id`, `album_id`) REFERENCES `album` (`space_id`, `album_id`) ON DELETE CASCADE,
  CONSTRAINT `album_media_asset_fk` FOREIGN KEY (`space_id`, `asset_id`) REFERENCES `media_asset` (`space_id`, `asset_id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_album_media_position` CHECK (`position` >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `favorite_media` (
  `space_id` bigint NOT NULL,
  `account_id` bigint NOT NULL,
  `asset_id` bigint NOT NULL,
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`account_id`, `asset_id`),
  KEY `idx_favorite_media_space_asset` (`space_id`, `asset_id`),
  CONSTRAINT `favorite_media_account_fk` FOREIGN KEY (`account_id`) REFERENCES `account` (`account_id`) ON DELETE CASCADE,
  CONSTRAINT `favorite_media_asset_fk` FOREIGN KEY (`space_id`, `asset_id`) REFERENCES `media_asset` (`space_id`, `asset_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `user_avatar` (
  `account_id` bigint NOT NULL,
  `space_id` bigint NOT NULL,
  `asset_id` bigint NOT NULL,
  `updated_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`account_id`),
  UNIQUE KEY `uk_user_avatar_asset` (`asset_id`),
  CONSTRAINT `user_avatar_account_fk` FOREIGN KEY (`account_id`) REFERENCES `account` (`account_id`) ON DELETE CASCADE,
  CONSTRAINT `user_avatar_asset_fk` FOREIGN KEY (`space_id`, `asset_id`) REFERENCES `media_asset` (`space_id`, `asset_id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `anniversary` (
  `anniversary_id` bigint NOT NULL AUTO_INCREMENT,
  `public_id` binary(16) NOT NULL,
  `space_id` bigint NOT NULL,
  `created_by` bigint NOT NULL,
  `title` varchar(100) NOT NULL,
  `anniversary_date` date NOT NULL,
  `description` text NULL,
  `cover_asset_id` bigint NULL,
  `sort_order` int NOT NULL DEFAULT 0,
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  `deleted_at` datetime(6) NULL,
  PRIMARY KEY (`anniversary_id`),
  UNIQUE KEY `uk_anniversary_public_id` (`public_id`),
  KEY `idx_anniversary_space_date` (`space_id`, `deleted_at`, `anniversary_date`),
  KEY `idx_anniversary_cover` (`space_id`, `cover_asset_id`),
  CONSTRAINT `anniversary_space_fk` FOREIGN KEY (`space_id`) REFERENCES `diary_space` (`space_id`) ON DELETE CASCADE,
  CONSTRAINT `anniversary_creator_fk` FOREIGN KEY (`created_by`) REFERENCES `account` (`account_id`) ON DELETE RESTRICT,
  CONSTRAINT `anniversary_cover_fk` FOREIGN KEY (`space_id`, `cover_asset_id`) REFERENCES `media_asset` (`space_id`, `asset_id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `ai_config` (
  `config_id` bigint NOT NULL,
  `enabled` boolean NOT NULL DEFAULT false,
  `base_url` varchar(500) CHARACTER SET ascii COLLATE ascii_bin NULL,
  `model` varchar(128) NULL,
  `encrypted_api_key` text CHARACTER SET ascii COLLATE ascii_bin NULL,
  `timeout_seconds` int NOT NULL DEFAULT 30,
  `updated_by` bigint NULL,
  `updated_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`config_id`),
  CONSTRAINT `ai_config_updated_by_fk` FOREIGN KEY (`updated_by`) REFERENCES `account` (`account_id`) ON DELETE SET NULL,
  CONSTRAINT `ck_ai_config_timeout` CHECK (`timeout_seconds` BETWEEN 5 AND 300)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `background_job` (
  `job_id` bigint NOT NULL AUTO_INCREMENT,
  `public_id` binary(16) NOT NULL,
  `space_id` bigint NULL,
  `created_by` bigint NULL,
  `job_type` varchar(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `status` varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'PENDING',
  `payload` json NOT NULL,
  `result` json NULL,
  `attempt_count` int NOT NULL DEFAULT 0,
  `max_attempts` int NOT NULL DEFAULT 3,
  `available_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `claimed_at` datetime(6) NULL,
  `claimed_by` varchar(100) CHARACTER SET ascii COLLATE ascii_bin NULL,
  `last_error` varchar(2000) NULL,
  `completed_at` datetime(6) NULL,
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`job_id`),
  UNIQUE KEY `uk_background_job_public_id` (`public_id`),
  KEY `idx_background_job_claim` (`status`, `available_at`, `job_id`),
  KEY `idx_background_job_space` (`space_id`, `created_at`, `job_id`),
  CONSTRAINT `background_job_space_fk` FOREIGN KEY (`space_id`) REFERENCES `diary_space` (`space_id`) ON DELETE CASCADE,
  CONSTRAINT `background_job_creator_fk` FOREIGN KEY (`created_by`) REFERENCES `account` (`account_id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_background_job_type` CHECK (`job_type` IN ('AI_REPORT', 'AI_ALBUM', 'MEDIA_PROCESS', 'EXPORT', 'IMPORT', 'STORAGE_GC')),
  CONSTRAINT `ck_background_job_status` CHECK (`status` IN ('PENDING', 'RUNNING', 'SUCCEEDED', 'FAILED', 'CANCELLED')),
  CONSTRAINT `ck_background_job_attempts` CHECK (`attempt_count` >= 0 AND `max_attempts` BETWEEN 1 AND 10)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `ai_report` (
  `report_id` bigint NOT NULL AUTO_INCREMENT,
  `public_id` binary(16) NOT NULL,
  `space_id` bigint NOT NULL,
  `created_by` bigint NOT NULL,
  `job_id` bigint NULL,
  `period_type` varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `period_start` date NOT NULL,
  `period_end` date NOT NULL,
  `title` varchar(255) NOT NULL,
  `content_markdown` mediumtext NOT NULL,
  `diary_count` int NOT NULL DEFAULT 0,
  `model` varchar(128) NULL,
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`report_id`),
  UNIQUE KEY `uk_ai_report_public_id` (`public_id`),
  UNIQUE KEY `uk_ai_report_space_id` (`space_id`, `report_id`),
  KEY `idx_ai_report_space_period` (`space_id`, `period_start`, `period_end`, `report_id`),
  CONSTRAINT `ai_report_space_fk` FOREIGN KEY (`space_id`) REFERENCES `diary_space` (`space_id`) ON DELETE CASCADE,
  CONSTRAINT `ai_report_creator_fk` FOREIGN KEY (`created_by`) REFERENCES `account` (`account_id`) ON DELETE RESTRICT,
  CONSTRAINT `ai_report_job_fk` FOREIGN KEY (`job_id`) REFERENCES `background_job` (`job_id`) ON DELETE SET NULL,
  CONSTRAINT `ck_ai_report_period_type` CHECK (`period_type` IN ('WEEKLY', 'MONTHLY', 'ANNUAL', 'CUSTOM')),
  CONSTRAINT `ck_ai_report_period` CHECK (`period_start` <= `period_end`),
  CONSTRAINT `ck_ai_report_diary_count` CHECK (`diary_count` >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `ai_report_diary` (
  `space_id` bigint NOT NULL,
  `report_id` bigint NOT NULL,
  `diary_id` bigint NOT NULL,
  PRIMARY KEY (`report_id`, `diary_id`),
  CONSTRAINT `ai_report_diary_report_fk` FOREIGN KEY (`space_id`, `report_id`) REFERENCES `ai_report` (`space_id`, `report_id`) ON DELETE CASCADE,
  CONSTRAINT `ai_report_diary_diary_fk` FOREIGN KEY (`space_id`, `diary_id`) REFERENCES `diary` (`space_id`, `diary_id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `ai_album_proposal` (
  `proposal_id` bigint NOT NULL AUTO_INCREMENT,
  `public_id` binary(16) NOT NULL,
  `space_id` bigint NOT NULL,
  `created_by` bigint NOT NULL,
  `job_id` bigint NULL,
  `status` varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'PENDING',
  `start_date` date NOT NULL,
  `end_date` date NOT NULL,
  `prompt` text NULL,
  `model` varchar(128) NULL,
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`proposal_id`),
  UNIQUE KEY `uk_ai_album_proposal_public_id` (`public_id`),
  UNIQUE KEY `uk_ai_album_proposal_space_id` (`space_id`, `proposal_id`),
  KEY `idx_ai_album_proposal_status` (`space_id`, `status`, `created_at`),
  CONSTRAINT `ai_album_proposal_space_fk` FOREIGN KEY (`space_id`) REFERENCES `diary_space` (`space_id`) ON DELETE CASCADE,
  CONSTRAINT `ai_album_proposal_creator_fk` FOREIGN KEY (`created_by`) REFERENCES `account` (`account_id`) ON DELETE RESTRICT,
  CONSTRAINT `ai_album_proposal_job_fk` FOREIGN KEY (`job_id`) REFERENCES `background_job` (`job_id`) ON DELETE SET NULL,
  CONSTRAINT `ck_ai_album_proposal_status` CHECK (`status` IN ('PENDING', 'CONFIRMED', 'DISMISSED', 'FAILED')),
  CONSTRAINT `ck_ai_album_proposal_period` CHECK (`start_date` <= `end_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `ai_album_candidate` (
  `candidate_id` bigint NOT NULL AUTO_INCREMENT,
  `space_id` bigint NOT NULL,
  `proposal_id` bigint NOT NULL,
  `mode` varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'NEW',
  `target_album_id` bigint NULL,
  `title` varchar(100) NOT NULL,
  `description` text NULL,
  `start_date` date NULL,
  `end_date` date NULL,
  `discarded` boolean NOT NULL DEFAULT false,
  `position` int NOT NULL DEFAULT 0,
  PRIMARY KEY (`candidate_id`),
  UNIQUE KEY `uk_ai_album_candidate_space_id` (`space_id`, `candidate_id`),
  KEY `idx_ai_album_candidate_proposal` (`space_id`, `proposal_id`, `position`),
  CONSTRAINT `ai_album_candidate_proposal_fk` FOREIGN KEY (`space_id`, `proposal_id`) REFERENCES `ai_album_proposal` (`space_id`, `proposal_id`) ON DELETE CASCADE,
  CONSTRAINT `ai_album_candidate_target_fk` FOREIGN KEY (`space_id`, `target_album_id`) REFERENCES `album` (`space_id`, `album_id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_ai_album_candidate_mode` CHECK (`mode` IN ('NEW', 'MERGE')),
  CONSTRAINT `ck_ai_album_candidate_position` CHECK (`position` >= 0),
  CONSTRAINT `ck_ai_album_candidate_period` CHECK (`start_date` IS NULL OR `end_date` IS NULL OR `start_date` <= `end_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `ai_album_candidate_diary` (
  `space_id` bigint NOT NULL,
  `candidate_id` bigint NOT NULL,
  `diary_id` bigint NOT NULL,
  `position` int NOT NULL,
  PRIMARY KEY (`candidate_id`, `diary_id`),
  UNIQUE KEY `uk_ai_album_candidate_diary_position` (`candidate_id`, `position`),
  CONSTRAINT `ai_album_candidate_diary_candidate_fk` FOREIGN KEY (`space_id`, `candidate_id`) REFERENCES `ai_album_candidate` (`space_id`, `candidate_id`) ON DELETE CASCADE,
  CONSTRAINT `ai_album_candidate_diary_diary_fk` FOREIGN KEY (`space_id`, `diary_id`) REFERENCES `diary` (`space_id`, `diary_id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_ai_album_candidate_diary_position` CHECK (`position` >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `ai_album_candidate_media` (
  `space_id` bigint NOT NULL,
  `candidate_id` bigint NOT NULL,
  `asset_id` bigint NOT NULL,
  `position` int NOT NULL,
  PRIMARY KEY (`candidate_id`, `asset_id`),
  UNIQUE KEY `uk_ai_album_candidate_media_position` (`candidate_id`, `position`),
  CONSTRAINT `ai_album_candidate_media_candidate_fk` FOREIGN KEY (`space_id`, `candidate_id`) REFERENCES `ai_album_candidate` (`space_id`, `candidate_id`) ON DELETE CASCADE,
  CONSTRAINT `ai_album_candidate_media_asset_fk` FOREIGN KEY (`space_id`, `asset_id`) REFERENCES `media_asset` (`space_id`, `asset_id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_ai_album_candidate_media_position` CHECK (`position` >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `space_ai_schedule` (
  `space_id` bigint NOT NULL,
  `weekly_enabled` boolean NOT NULL DEFAULT false,
  `monthly_enabled` boolean NOT NULL DEFAULT false,
  `annual_enabled` boolean NOT NULL DEFAULT false,
  `next_run_at` datetime(6) NULL,
  `last_run_at` datetime(6) NULL,
  `updated_by` bigint NOT NULL,
  `updated_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`space_id`),
  KEY `idx_space_ai_schedule_due` (`next_run_at`),
  CONSTRAINT `space_ai_schedule_space_fk` FOREIGN KEY (`space_id`) REFERENCES `diary_space` (`space_id`) ON DELETE CASCADE,
  CONSTRAINT `space_ai_schedule_account_fk` FOREIGN KEY (`updated_by`) REFERENCES `account` (`account_id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `notification` (
  `notification_id` bigint NOT NULL AUTO_INCREMENT,
  `public_id` binary(16) NOT NULL,
  `account_id` bigint NOT NULL,
  `space_id` bigint NULL,
  `type` varchar(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `title` varchar(160) NOT NULL,
  `body` varchar(1000) NULL,
  `target_ref` json NULL,
  `dedupe_key` varchar(160) CHARACTER SET ascii COLLATE ascii_bin NULL,
  `read_at` datetime(6) NULL,
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`notification_id`),
  UNIQUE KEY `uk_notification_public_id` (`public_id`),
  UNIQUE KEY `uk_notification_account_dedupe` (`account_id`, `dedupe_key`),
  KEY `idx_notification_account_read` (`account_id`, `read_at`, `created_at`),
  CONSTRAINT `notification_account_fk` FOREIGN KEY (`account_id`) REFERENCES `account` (`account_id`) ON DELETE CASCADE,
  CONSTRAINT `notification_space_fk` FOREIGN KEY (`space_id`) REFERENCES `diary_space` (`space_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `push_subscription` (
  `subscription_id` bigint NOT NULL AUTO_INCREMENT,
  `account_id` bigint NOT NULL,
  `endpoint_hash` binary(32) NOT NULL,
  `endpoint` text NOT NULL,
  `p256dh` varchar(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `auth_secret` varchar(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `user_agent` varchar(500) NULL,
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `last_success_at` datetime(6) NULL,
  `revoked_at` datetime(6) NULL,
  PRIMARY KEY (`subscription_id`),
  UNIQUE KEY `uk_push_subscription_endpoint` (`endpoint_hash`),
  KEY `idx_push_subscription_account` (`account_id`, `revoked_at`),
  CONSTRAINT `push_subscription_account_fk` FOREIGN KEY (`account_id`) REFERENCES `account` (`account_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `reminder` (
  `reminder_id` bigint NOT NULL AUTO_INCREMENT,
  `public_id` binary(16) NOT NULL,
  `account_id` bigint NOT NULL,
  `space_id` bigint NULL,
  `type` varchar(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `enabled` boolean NOT NULL DEFAULT true,
  `schedule` json NOT NULL,
  `next_run_at` datetime(6) NULL,
  `last_run_at` datetime(6) NULL,
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`reminder_id`),
  UNIQUE KEY `uk_reminder_public_id` (`public_id`),
  UNIQUE KEY `uk_reminder_account_space_type` (`account_id`, `space_id`, `type`),
  KEY `idx_reminder_due` (`enabled`, `next_run_at`),
  CONSTRAINT `reminder_account_fk` FOREIGN KEY (`account_id`) REFERENCES `account` (`account_id`) ON DELETE CASCADE,
  CONSTRAINT `reminder_space_fk` FOREIGN KEY (`space_id`) REFERENCES `diary_space` (`space_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `private_share` (
  `share_id` bigint NOT NULL AUTO_INCREMENT,
  `public_id` binary(16) NOT NULL,
  `token_hash` binary(32) NOT NULL,
  `space_id` bigint NOT NULL,
  `diary_id` bigint NOT NULL,
  `created_by` bigint NOT NULL,
  `password_hash` varchar(255) CHARACTER SET ascii COLLATE ascii_bin NULL,
  `expires_at` datetime(6) NOT NULL,
  `max_views` int NULL,
  `view_count` int NOT NULL DEFAULT 0,
  `revoked_at` datetime(6) NULL,
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`share_id`),
  UNIQUE KEY `uk_private_share_public_id` (`public_id`),
  UNIQUE KEY `uk_private_share_token_hash` (`token_hash`),
  KEY `idx_private_share_diary` (`space_id`, `diary_id`, `revoked_at`, `expires_at`),
  CONSTRAINT `private_share_diary_fk` FOREIGN KEY (`space_id`, `diary_id`) REFERENCES `diary` (`space_id`, `diary_id`) ON DELETE CASCADE,
  CONSTRAINT `private_share_creator_fk` FOREIGN KEY (`created_by`) REFERENCES `account` (`account_id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_private_share_views` CHECK ((`max_views` IS NULL OR `max_views` > 0) AND `view_count` >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `sync_change` (
  `change_seq` bigint NOT NULL AUTO_INCREMENT,
  `space_id` bigint NOT NULL,
  `entity_type` varchar(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `entity_public_id` binary(16) NOT NULL,
  `operation` varchar(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `revision` int NOT NULL,
  `actor_id` bigint NOT NULL,
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`change_seq`),
  KEY `idx_sync_change_space_seq` (`space_id`, `change_seq`),
  KEY `idx_sync_change_retention` (`created_at`),
  CONSTRAINT `sync_change_space_fk` FOREIGN KEY (`space_id`) REFERENCES `diary_space` (`space_id`) ON DELETE CASCADE,
  CONSTRAINT `sync_change_actor_fk` FOREIGN KEY (`actor_id`) REFERENCES `account` (`account_id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_sync_change_operation` CHECK (`operation` IN ('UPSERT', 'DELETE')),
  CONSTRAINT `ck_sync_change_revision` CHECK (`revision` > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `sync_operation` (
  `operation_id` binary(16) NOT NULL,
  `account_id` bigint NOT NULL,
  `space_id` bigint NOT NULL,
  `result_code` varchar(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `entity_type` varchar(32) CHARACTER SET ascii COLLATE ascii_bin NULL,
  `entity_public_id` binary(16) NULL,
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `expires_at` datetime(6) NOT NULL,
  PRIMARY KEY (`operation_id`),
  KEY `idx_sync_operation_account` (`account_id`, `created_at`),
  KEY `idx_sync_operation_expiry` (`expires_at`),
  CONSTRAINT `sync_operation_account_fk` FOREIGN KEY (`account_id`) REFERENCES `account` (`account_id`) ON DELETE CASCADE,
  CONSTRAINT `sync_operation_space_fk` FOREIGN KEY (`space_id`) REFERENCES `diary_space` (`space_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `outbox_event` (
  `event_id` bigint NOT NULL AUTO_INCREMENT,
  `public_id` binary(16) NOT NULL,
  `space_id` bigint NULL,
  `aggregate_type` varchar(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `aggregate_public_id` binary(16) NULL,
  `event_type` varchar(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
  `payload` json NOT NULL,
  `attempt_count` int NOT NULL DEFAULT 0,
  `available_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `processed_at` datetime(6) NULL,
  `last_error` varchar(2000) NULL,
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`event_id`),
  UNIQUE KEY `uk_outbox_event_public_id` (`public_id`),
  KEY `idx_outbox_event_pending` (`processed_at`, `available_at`, `event_id`),
  CONSTRAINT `outbox_event_space_fk` FOREIGN KEY (`space_id`) REFERENCES `diary_space` (`space_id`) ON DELETE CASCADE,
  CONSTRAINT `ck_outbox_event_attempts` CHECK (`attempt_count` >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `space_storage_usage` (
  `space_id` bigint NOT NULL,
  `used_bytes` bigint NOT NULL DEFAULT 0,
  `updated_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`space_id`),
  CONSTRAINT `space_storage_usage_space_fk` FOREIGN KEY (`space_id`) REFERENCES `diary_space` (`space_id`) ON DELETE CASCADE,
  CONSTRAINT `ck_space_storage_usage` CHECK (`used_bytes` >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO `diary_template`
  (`public_id`, `template_key`, `name`, `description`, `icon`, `prompt_text`, `content_html`, `builtin`)
VALUES
  (UUID_TO_BIN(UUID()), 'daily_moment', '今日小记', '记录今天最值得留下的一件小事', 'Notebook', '今天最想记住的瞬间是什么？', '<h2>今天发生了什么</h2><p></p><h2>想对你说</h2><p></p>', true),
  (UUID_TO_BIN(UUID()), 'baby_growth', '成长记录', '记录宝宝的新变化、新能力与陪伴感受', 'Sunny', '今天发现了哪些成长变化？', '<h2>今天的新变化</h2><p></p><h2>我们的感受</h2><p></p><h2>想留给未来的话</h2><p></p>', true),
  (UUID_TO_BIN(UUID()), 'couple_memory', '两个人的回忆', '记录一起完成的事情与彼此的感受', 'Connection', '今天你们一起经历了什么？', '<h2>我们一起做了什么</h2><p></p><h2>最打动我的瞬间</h2><p></p>', true),
  (UUID_TO_BIN(UUID()), 'travel_log', '旅行手记', '按地点、见闻和照片线索整理旅程', 'Location', '这段旅程最独特的地点和体验是什么？', '<h2>今天到了哪里</h2><p></p><h2>看见与尝到</h2><p></p><h2>旅途片段</h2><p></p>', true);
