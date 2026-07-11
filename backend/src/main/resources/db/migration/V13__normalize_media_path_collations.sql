ALTER TABLE `diary_image`
    MODIFY COLUMN `image_path` VARCHAR(255)
        CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL;

ALTER TABLE `album`
    MODIFY COLUMN `cover_image_path` VARCHAR(255)
        CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL;
