CREATE TABLE `space_invitation` (
  `invitation_id` bigint NOT NULL AUTO_INCREMENT,
  `space_id` bigint NOT NULL,
  `invited_by` int NOT NULL,
  `email` varchar(255) NULL,
  `token_hash` char(64) NOT NULL,
  `role` varchar(16) NOT NULL DEFAULT 'MEMBER',
  `status` varchar(16) NOT NULL DEFAULT 'PENDING',
  `expires_at` timestamp NOT NULL,
  `accepted_by` int NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`invitation_id`),
  UNIQUE KEY `uk_space_invitation_token` (`token_hash`),
  KEY `idx_space_invitation_space_status` (`space_id`, `status`, `expires_at`),
  CONSTRAINT `space_invitation_space_fk` FOREIGN KEY (`space_id`) REFERENCES `diary_space` (`space_id`) ON DELETE CASCADE,
  CONSTRAINT `space_invitation_inviter_fk` FOREIGN KEY (`invited_by`) REFERENCES `user` (`user_id`) ON DELETE CASCADE,
  CONSTRAINT `space_invitation_acceptor_fk` FOREIGN KEY (`accepted_by`) REFERENCES `user` (`user_id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `auth_session` (
  `session_id` bigint NOT NULL AUTO_INCREMENT,
  `public_id` char(36) NOT NULL,
  `user_id` int NOT NULL,
  `refresh_token_hash` char(64) NOT NULL,
  `device_name` varchar(160) NULL,
  `user_agent` varchar(500) NULL,
  `ip_address` varchar(64) NULL,
  `expires_at` timestamp NOT NULL,
  `last_seen_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `revoked_at` timestamp NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`session_id`),
  UNIQUE KEY `uk_auth_session_public_id` (`public_id`),
  UNIQUE KEY `uk_auth_session_refresh_hash` (`refresh_token_hash`),
  KEY `idx_auth_session_user_active` (`user_id`, `revoked_at`, `expires_at`),
  CONSTRAINT `auth_session_user_fk` FOREIGN KEY (`user_id`) REFERENCES `user` (`user_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `account_token` (
  `token_id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` int NOT NULL,
  `type` varchar(24) NOT NULL,
  `token_hash` char(64) NOT NULL,
  `expires_at` timestamp NOT NULL,
  `used_at` timestamp NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`token_id`),
  UNIQUE KEY `uk_account_token_hash` (`token_hash`),
  KEY `idx_account_token_user_type` (`user_id`, `type`, `used_at`, `expires_at`),
  CONSTRAINT `account_token_user_fk` FOREIGN KEY (`user_id`) REFERENCES `user` (`user_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `recovery_code` (
  `recovery_code_id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` int NOT NULL,
  `code_hash` char(64) NOT NULL,
  `used_at` timestamp NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`recovery_code_id`),
  UNIQUE KEY `uk_recovery_code_hash` (`code_hash`),
  KEY `idx_recovery_code_user` (`user_id`, `used_at`),
  CONSTRAINT `recovery_code_user_fk` FOREIGN KEY (`user_id`) REFERENCES `user` (`user_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `diary_revision` (
  `revision_id` bigint NOT NULL AUTO_INCREMENT,
  `diary_id` int NOT NULL,
  `version` int NOT NULL,
  `editor_user_id` int NOT NULL,
  `snapshot_json` mediumtext NOT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`revision_id`),
  UNIQUE KEY `uk_diary_revision_version` (`diary_id`, `version`),
  KEY `idx_diary_revision_created` (`diary_id`, `created_at`),
  CONSTRAINT `diary_revision_diary_fk` FOREIGN KEY (`diary_id`) REFERENCES `diary` (`diary_id`) ON DELETE CASCADE,
  CONSTRAINT `diary_revision_editor_fk` FOREIGN KEY (`editor_user_id`) REFERENCES `user` (`user_id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `diary_comment` (
  `comment_id` bigint NOT NULL AUTO_INCREMENT,
  `public_id` char(36) NOT NULL,
  `diary_id` int NOT NULL,
  `user_id` int NOT NULL,
  `content` varchar(2000) NOT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted_at` timestamp NULL,
  PRIMARY KEY (`comment_id`),
  UNIQUE KEY `uk_diary_comment_public_id` (`public_id`),
  KEY `idx_diary_comment_diary` (`diary_id`, `deleted_at`, `created_at`),
  CONSTRAINT `diary_comment_diary_fk` FOREIGN KEY (`diary_id`) REFERENCES `diary` (`diary_id`) ON DELETE CASCADE,
  CONSTRAINT `diary_comment_user_fk` FOREIGN KEY (`user_id`) REFERENCES `user` (`user_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `diary_reaction` (
  `diary_id` int NOT NULL,
  `user_id` int NOT NULL,
  `emoji` varchar(16) NOT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`diary_id`, `user_id`, `emoji`),
  KEY `idx_diary_reaction_diary` (`diary_id`, `created_at`),
  CONSTRAINT `diary_reaction_diary_fk` FOREIGN KEY (`diary_id`) REFERENCES `diary` (`diary_id`) ON DELETE CASCADE,
  CONSTRAINT `diary_reaction_user_fk` FOREIGN KEY (`user_id`) REFERENCES `user` (`user_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `media_asset` (
  `asset_id` bigint NOT NULL AUTO_INCREMENT,
  `public_id` char(36) NOT NULL,
  `space_id` bigint NOT NULL,
  `owner_user_id` int NOT NULL,
  `media_type` varchar(16) NOT NULL,
  `storage_provider` varchar(16) NOT NULL DEFAULT 'LOCAL',
  `storage_key` varchar(500) NOT NULL,
  `thumbnail_key` varchar(500) NULL,
  `content_type` varchar(128) NULL,
  `size_bytes` bigint NOT NULL DEFAULT 0,
  `duration_seconds` int NULL,
  `width` int NULL,
  `height` int NULL,
  `caption` varchar(500) NULL,
  `taken_at` timestamp NULL,
  `location_name` varchar(255) NULL,
  `latitude` decimal(10,7) NULL,
  `longitude` decimal(10,7) NULL,
  `status` varchar(16) NOT NULL DEFAULT 'READY',
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `deleted_at` timestamp NULL,
  PRIMARY KEY (`asset_id`),
  UNIQUE KEY `uk_media_asset_public_id` (`public_id`),
  UNIQUE KEY `uk_media_asset_storage` (`storage_provider`, `storage_key`),
  KEY `idx_media_asset_space_created` (`space_id`, `deleted_at`, `created_at`),
  CONSTRAINT `media_asset_space_fk` FOREIGN KEY (`space_id`) REFERENCES `diary_space` (`space_id`) ON DELETE CASCADE,
  CONSTRAINT `media_asset_owner_fk` FOREIGN KEY (`owner_user_id`) REFERENCES `user` (`user_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `diary_media` (
  `diary_id` int NOT NULL,
  `asset_id` bigint NOT NULL,
  `sort` int NOT NULL DEFAULT 0,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`diary_id`, `asset_id`),
  KEY `idx_diary_media_asset` (`asset_id`),
  CONSTRAINT `diary_media_diary_fk` FOREIGN KEY (`diary_id`) REFERENCES `diary` (`diary_id`) ON DELETE CASCADE,
  CONSTRAINT `diary_media_asset_fk` FOREIGN KEY (`asset_id`) REFERENCES `media_asset` (`asset_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `sync_change` (
  `change_seq` bigint NOT NULL AUTO_INCREMENT,
  `space_id` bigint NOT NULL,
  `entity_type` varchar(32) NOT NULL,
  `entity_public_id` char(36) NOT NULL,
  `operation` varchar(16) NOT NULL,
  `revision` int NOT NULL DEFAULT 1,
  `actor_user_id` int NOT NULL,
  `payload_json` mediumtext NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`change_seq`),
  KEY `idx_sync_change_space_seq` (`space_id`, `change_seq`),
  CONSTRAINT `sync_change_space_fk` FOREIGN KEY (`space_id`) REFERENCES `diary_space` (`space_id`) ON DELETE CASCADE,
  CONSTRAINT `sync_change_actor_fk` FOREIGN KEY (`actor_user_id`) REFERENCES `user` (`user_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `sync_operation` (
  `operation_id` char(36) NOT NULL,
  `user_id` int NOT NULL,
  `space_id` bigint NOT NULL,
  `result_json` mediumtext NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`operation_id`),
  KEY `idx_sync_operation_user_created` (`user_id`, `created_at`),
  CONSTRAINT `sync_operation_user_fk` FOREIGN KEY (`user_id`) REFERENCES `user` (`user_id`) ON DELETE CASCADE,
  CONSTRAINT `sync_operation_space_fk` FOREIGN KEY (`space_id`) REFERENCES `diary_space` (`space_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `notification` (
  `notification_id` bigint NOT NULL AUTO_INCREMENT,
  `public_id` char(36) NOT NULL,
  `user_id` int NOT NULL,
  `space_id` bigint NULL,
  `type` varchar(32) NOT NULL,
  `title` varchar(160) NOT NULL,
  `body` varchar(1000) NULL,
  `target_path` varchar(500) NULL,
  `dedupe_key` varchar(160) NULL,
  `read_at` timestamp NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`notification_id`),
  UNIQUE KEY `uk_notification_public_id` (`public_id`),
  UNIQUE KEY `uk_notification_user_dedupe` (`user_id`, `dedupe_key`),
  KEY `idx_notification_user_read` (`user_id`, `read_at`, `created_at`),
  CONSTRAINT `notification_user_fk` FOREIGN KEY (`user_id`) REFERENCES `user` (`user_id`) ON DELETE CASCADE,
  CONSTRAINT `notification_space_fk` FOREIGN KEY (`space_id`) REFERENCES `diary_space` (`space_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `push_subscription` (
  `subscription_id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` int NOT NULL,
  `endpoint_hash` char(64) NOT NULL,
  `endpoint` text NOT NULL,
  `p256dh` varchar(255) NOT NULL,
  `auth_secret` varchar(255) NOT NULL,
  `user_agent` varchar(500) NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `last_success_at` timestamp NULL,
  `revoked_at` timestamp NULL,
  PRIMARY KEY (`subscription_id`),
  UNIQUE KEY `uk_push_subscription_endpoint` (`endpoint_hash`),
  KEY `idx_push_subscription_user` (`user_id`, `revoked_at`),
  CONSTRAINT `push_subscription_user_fk` FOREIGN KEY (`user_id`) REFERENCES `user` (`user_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `reminder` (
  `reminder_id` bigint NOT NULL AUTO_INCREMENT,
  `public_id` char(36) NOT NULL,
  `user_id` int NOT NULL,
  `space_id` bigint NULL,
  `type` varchar(32) NOT NULL,
  `enabled` tinyint(1) NOT NULL DEFAULT 1,
  `schedule_json` text NOT NULL,
  `next_run_at` timestamp NULL,
  `last_run_at` timestamp NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`reminder_id`),
  UNIQUE KEY `uk_reminder_public_id` (`public_id`),
  KEY `idx_reminder_due` (`enabled`, `next_run_at`),
  CONSTRAINT `reminder_user_fk` FOREIGN KEY (`user_id`) REFERENCES `user` (`user_id`) ON DELETE CASCADE,
  CONSTRAINT `reminder_space_fk` FOREIGN KEY (`space_id`) REFERENCES `diary_space` (`space_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
