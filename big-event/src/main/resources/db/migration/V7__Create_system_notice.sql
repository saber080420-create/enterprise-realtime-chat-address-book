CREATE TABLE IF NOT EXISTS system_notice (
  id INT UNSIGNED NOT NULL AUTO_INCREMENT PRIMARY KEY,
  user_id INT UNSIGNED NOT NULL COMMENT '接收者用户ID',
  operator_id INT UNSIGNED NULL COMMENT '操作者用户ID',
  type VARCHAR(50) NOT NULL COMMENT '通知类型: role_changed, department_changed, department_removed',
  title VARCHAR(100) NOT NULL COMMENT '标题',
  content VARCHAR(500) NOT NULL COMMENT '内容',
  is_read TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否已读',
  read_time DATETIME NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_user_read (user_id, is_read)
) COMMENT='系统通知表';

