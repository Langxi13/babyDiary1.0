ALTER TABLE `media_asset`
  ADD COLUMN `checksum_sha256` char(64) NULL AFTER `size_bytes`,
  ADD COLUMN `access_scope` varchar(16) NOT NULL DEFAULT 'LINKED' AFTER `checksum_sha256`,
  ADD COLUMN `library_visible` tinyint(1) NOT NULL DEFAULT 1 AFTER `access_scope`,
  ADD KEY `idx_media_asset_space_library` (`space_id`, `library_visible`, `media_type`, `deleted_at`, `created_at`),
  ADD KEY `idx_media_asset_space_checksum` (`space_id`, `checksum_sha256`);

ALTER TABLE `diary_media`
  ADD KEY `idx_diary_media_sort` (`diary_id`, `sort`, `asset_id`);

CREATE TABLE `album_media` (
  `album_id` int NOT NULL,
  `asset_id` bigint NOT NULL,
  `sort` int NOT NULL DEFAULT 0,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`album_id`, `asset_id`),
  KEY `idx_album_media_sort` (`album_id`, `sort`, `asset_id`),
  KEY `idx_album_media_asset` (`asset_id`),
  CONSTRAINT `album_media_album_fk` FOREIGN KEY (`album_id`) REFERENCES `album` (`album_id`) ON DELETE CASCADE,
  CONSTRAINT `album_media_asset_fk` FOREIGN KEY (`asset_id`) REFERENCES `media_asset` (`asset_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `favorite_media` (
  `user_id` int NOT NULL,
  `asset_id` bigint NOT NULL,
  `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`user_id`, `asset_id`),
  KEY `idx_favorite_media_asset` (`asset_id`),
  CONSTRAINT `favorite_media_user_fk` FOREIGN KEY (`user_id`) REFERENCES `user` (`user_id`) ON DELETE CASCADE,
  CONSTRAINT `favorite_media_asset_fk` FOREIGN KEY (`asset_id`) REFERENCES `media_asset` (`asset_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `user_avatar` (
  `user_id` int NOT NULL,
  `asset_id` bigint NOT NULL,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`user_id`),
  UNIQUE KEY `uk_user_avatar_asset` (`asset_id`),
  CONSTRAINT `user_avatar_user_fk` FOREIGN KEY (`user_id`) REFERENCES `user` (`user_id`) ON DELETE CASCADE,
  CONSTRAINT `user_avatar_asset_fk` FOREIGN KEY (`asset_id`) REFERENCES `media_asset` (`asset_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE `album`
  ADD COLUMN `cover_asset_id` bigint NULL AFTER `cover_image_path`,
  ADD KEY `idx_album_cover_asset` (`cover_asset_id`),
  ADD CONSTRAINT `album_cover_asset_fk` FOREIGN KEY (`cover_asset_id`) REFERENCES `media_asset` (`asset_id`) ON DELETE SET NULL;

ALTER TABLE `anniversary`
  ADD COLUMN `cover_asset_id` bigint NULL AFTER `cover_image_path`,
  ADD KEY `idx_anniversary_cover_asset` (`cover_asset_id`),
  ADD CONSTRAINT `anniversary_cover_asset_fk` FOREIGN KEY (`cover_asset_id`) REFERENCES `media_asset` (`asset_id`) ON DELETE SET NULL;

CREATE TABLE `media_legacy_map` (
  `space_id` bigint NOT NULL,
  `legacy_path` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
  `asset_id` bigint NOT NULL,
  `checksum_sha256` char(64) NOT NULL,
  `migrated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`space_id`, `legacy_path`),
  UNIQUE KEY `uk_media_legacy_asset` (`asset_id`),
  CONSTRAINT `media_legacy_map_space_fk` FOREIGN KEY (`space_id`) REFERENCES `diary_space` (`space_id`) ON DELETE CASCADE,
  CONSTRAINT `media_legacy_map_asset_fk` FOREIGN KEY (`asset_id`) REFERENCES `media_asset` (`asset_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
