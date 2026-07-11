ALTER TABLE `reminder`
  ADD UNIQUE KEY `uk_reminder_user_space_type` (`user_id`, `space_id`, `type`);
