-- 为群公告增加状态字段：published | withdrawn
ALTER TABLE `group_announcement`
  ADD COLUMN `status` VARCHAR(20) NOT NULL DEFAULT 'published' AFTER `update_time`;

-- 群公告已读表
CREATE TABLE IF NOT EXISTS `group_announcement_read` (
  `id` INT UNSIGNED NOT NULL AUTO_INCREMENT,
  `announcement_id` INT UNSIGNED NOT NULL,
  `user_id` INT UNSIGNED NOT NULL,
  `read_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ann_user` (`announcement_id`, `user_id`),
  KEY `idx_announcement_id` (`announcement_id`),
  KEY `idx_user_id` (`user_id`),
  CONSTRAINT `fk_gar_announcement` FOREIGN KEY (`announcement_id`) REFERENCES `group_announcement`(`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_gar_user` FOREIGN KEY (`user_id`) REFERENCES `user`(`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;


