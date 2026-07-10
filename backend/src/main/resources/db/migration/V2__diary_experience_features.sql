ALTER TABLE `diary`
  ADD COLUMN `mood_key` varchar(32) NULL AFTER `content`,
  ADD COLUMN `content_format` varchar(16) NOT NULL DEFAULT 'plain' AFTER `mood_key`;

CREATE TABLE `tag` (
  `tag_id` int NOT NULL AUTO_INCREMENT,
  `user_id` int NOT NULL,
  `name` varchar(32) NOT NULL,
  `color` varchar(16) NULL,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`tag_id`),
  UNIQUE KEY `uk_tag_user_name` (`user_id`, `name`),
  CONSTRAINT `tag_user_fk` FOREIGN KEY (`user_id`) REFERENCES `user` (`user_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE `diary_tag` (
  `diary_id` int NOT NULL,
  `tag_id` int NOT NULL,
  PRIMARY KEY (`diary_id`, `tag_id`),
  KEY `idx_diary_tag_tag` (`tag_id`),
  CONSTRAINT `diary_tag_diary_fk` FOREIGN KEY (`diary_id`) REFERENCES `diary` (`diary_id`) ON DELETE CASCADE,
  CONSTRAINT `diary_tag_tag_fk` FOREIGN KEY (`tag_id`) REFERENCES `tag` (`tag_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE `anniversary` (
  `anniversary_id` int NOT NULL AUTO_INCREMENT,
  `user_id` int NOT NULL,
  `title` varchar(100) NOT NULL,
  `date` date NOT NULL,
  `description` text NULL,
  `cover_image_path` varchar(255) NULL,
  `sort` int NOT NULL DEFAULT 0,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`anniversary_id`),
  KEY `idx_anniversary_user_date` (`user_id`, `date`),
  CONSTRAINT `anniversary_user_fk` FOREIGN KEY (`user_id`) REFERENCES `user` (`user_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE `favorite_photo` (
  `user_id` int NOT NULL,
  `image_id` int NOT NULL,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`user_id`, `image_id`),
  KEY `idx_favorite_photo_image` (`image_id`),
  CONSTRAINT `favorite_photo_user_fk` FOREIGN KEY (`user_id`) REFERENCES `user` (`user_id`) ON DELETE CASCADE,
  CONSTRAINT `favorite_photo_image_fk` FOREIGN KEY (`image_id`) REFERENCES `diary_image` (`image_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE `diary_draft` (
  `draft_id` int NOT NULL AUTO_INCREMENT,
  `user_id` int NOT NULL,
  `draft_key` varchar(64) NOT NULL,
  `diary_id` int NULL,
  `title` varchar(255) NULL,
  `date` date NULL,
  `content` mediumtext NULL,
  `content_format` varchar(16) NOT NULL DEFAULT 'html',
  `mood_key` varchar(32) NULL,
  `tag_ids` varchar(255) NULL,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`draft_id`),
  UNIQUE KEY `uk_draft_user_key` (`user_id`, `draft_key`),
  KEY `idx_draft_user_updated` (`user_id`, `updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
