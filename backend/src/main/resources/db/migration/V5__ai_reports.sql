CREATE TABLE `ai_config` (
  `config_id` int NOT NULL,
  `enabled` tinyint(1) NOT NULL DEFAULT 0,
  `base_url` varchar(255) NULL,
  `model` varchar(128) NULL,
  `encrypted_api_key` text NULL,
  `timeout_seconds` int NOT NULL DEFAULT 30,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`config_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE `ai_report` (
  `report_id` int NOT NULL AUTO_INCREMENT,
  `user_id` int NOT NULL,
  `type` varchar(16) NOT NULL,
  `period` varchar(16) NOT NULL,
  `period_start` date NOT NULL,
  `period_end` date NOT NULL,
  `title` varchar(255) NOT NULL,
  `content_markdown` mediumtext NOT NULL,
  `diary_count` int NOT NULL DEFAULT 0,
  `model` varchar(128) NULL,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`report_id`),
  KEY `idx_ai_report_user_created` (`user_id`, `created_at`),
  KEY `idx_ai_report_user_type_period` (`user_id`, `type`, `period`),
  CONSTRAINT `ai_report_user_fk` FOREIGN KEY (`user_id`) REFERENCES `user` (`user_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
