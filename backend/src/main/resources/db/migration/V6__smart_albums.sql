CREATE TABLE `album_group` (
  `group_id` int NOT NULL AUTO_INCREMENT,
  `user_id` int NOT NULL,
  `name` varchar(100) NOT NULL,
  `type` varchar(16) NOT NULL,
  `sort` int NOT NULL DEFAULT 0,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`group_id`),
  KEY `idx_album_group_user_sort` (`user_id`, `sort`, `group_id`),
  UNIQUE KEY `uk_album_group_user_type_name` (`user_id`, `type`, `name`),
  CONSTRAINT `album_group_user_fk` FOREIGN KEY (`user_id`) REFERENCES `user` (`user_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE `album` (
  `album_id` int NOT NULL AUTO_INCREMENT,
  `group_id` int NOT NULL,
  `user_id` int NOT NULL,
  `name` varchar(100) NOT NULL,
  `description` text NULL,
  `type` varchar(16) NOT NULL,
  `cover_image_path` varchar(255) NULL,
  `sort` int NOT NULL DEFAULT 0,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`album_id`),
  KEY `idx_album_group_sort` (`group_id`, `sort`, `album_id`),
  KEY `idx_album_user_type` (`user_id`, `type`),
  CONSTRAINT `album_group_fk` FOREIGN KEY (`group_id`) REFERENCES `album_group` (`group_id`) ON DELETE CASCADE,
  CONSTRAINT `album_user_fk` FOREIGN KEY (`user_id`) REFERENCES `user` (`user_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE `album_photo` (
  `album_id` int NOT NULL,
  `image_id` int NOT NULL,
  `sort` int NOT NULL DEFAULT 0,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`album_id`, `image_id`),
  KEY `idx_album_photo_image` (`image_id`),
  CONSTRAINT `album_photo_album_fk` FOREIGN KEY (`album_id`) REFERENCES `album` (`album_id`) ON DELETE CASCADE,
  CONSTRAINT `album_photo_image_fk` FOREIGN KEY (`image_id`) REFERENCES `diary_image` (`image_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE `ai_album_proposal` (
  `proposal_id` int NOT NULL AUTO_INCREMENT,
  `user_id` int NOT NULL,
  `status` varchar(16) NOT NULL,
  `start_date` date NOT NULL,
  `end_date` date NOT NULL,
  `prompt` text NULL,
  `content_json` mediumtext NOT NULL,
  `model` varchar(128) NULL,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`proposal_id`),
  KEY `idx_ai_album_proposal_user_status` (`user_id`, `status`, `created_at`),
  CONSTRAINT `ai_album_proposal_user_fk` FOREIGN KEY (`user_id`) REFERENCES `user` (`user_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
