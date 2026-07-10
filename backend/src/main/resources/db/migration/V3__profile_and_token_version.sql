ALTER TABLE `user`
  ADD COLUMN `avatar_path` varchar(255) NULL AFTER `created_at`,
  ADD COLUMN `token_version` int NOT NULL DEFAULT 0 AFTER `avatar_path`;
