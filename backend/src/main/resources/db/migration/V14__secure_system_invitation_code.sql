CREATE TABLE `system_invitation_config` (
  `config_id` int NOT NULL,
  `encrypted_code` text NOT NULL,
  `updated_by` int NULL,
  `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`config_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
