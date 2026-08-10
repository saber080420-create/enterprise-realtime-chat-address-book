-- 创建群公告表
CREATE TABLE IF NOT EXISTS group_announcement (
  id INT UNSIGNED PRIMARY KEY AUTO_INCREMENT,
  group_id INT UNSIGNED NOT NULL,
  publisher_id INT UNSIGNED NOT NULL,
  title VARCHAR(255) NOT NULL,
  content TEXT NOT NULL,
  publish_time DATETIME NULL,
  create_time DATETIME NULL,
  update_time DATETIME NULL,
  INDEX idx_group_id (group_id),
  CONSTRAINT fk_ga_group FOREIGN KEY (group_id) REFERENCES chat_group(id) ON DELETE CASCADE,
  CONSTRAINT fk_ga_publisher FOREIGN KEY (publisher_id) REFERENCES user(id) ON DELETE CASCADE
);


