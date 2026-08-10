-- 外部联系人与通讯录扩展脚本（兼容版）：避免使用 ALTER ... IF NOT EXISTS 等可能在旧版 MySQL 中不支持的语法
-- 使用方法：请在已选择/使用 big_event 数据库的会话中执行（或先执行：USE big_event;）

-- 保险起见，切库（若库不存在请先执行 big_event.sql 初始化）
USE big_event;

-- 1) 创建外部联系人表（如不存在）
CREATE TABLE IF NOT EXISTS external_contact (
    id INT UNSIGNED PRIMARY KEY AUTO_INCREMENT COMMENT '外部联系人ID',
    name VARCHAR(50) NOT NULL DEFAULT '' COMMENT '姓名',
    phone VARCHAR(20) NOT NULL DEFAULT '' COMMENT '电话',
    email VARCHAR(128) NOT NULL DEFAULT '' COMMENT '邮箱',
    company VARCHAR(100) NOT NULL DEFAULT '' COMMENT '公司/部门',
    position VARCHAR(50) NOT NULL DEFAULT '' COMMENT '职位',
    create_time DATETIME NOT NULL COMMENT '创建时间',
    update_time DATETIME NOT NULL COMMENT '更新时间',
    INDEX idx_phone (phone),
    INDEX idx_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='外部联系人表';

-- 2) 以存储过程的方式“条件化”修改 address_book，最大化兼容 MySQL 5.7/8.0
DELIMITER $$
DROP PROCEDURE IF EXISTS sp_upgrade_address_book_external $$
CREATE PROCEDURE sp_upgrade_address_book_external()
BEGIN
  -- 若 address_book 表不存在则跳过后续修改（不报错，方便先后顺序不同的环境重复执行）
  IF NOT EXISTS (
      SELECT 1 FROM information_schema.tables 
       WHERE table_schema = DATABASE() AND table_name = 'address_book'
  ) THEN
    SELECT '提示：address_book 表不存在，请先执行 address_book_db_fixed.sql 后再运行本脚本。' AS msg;
  ELSE
    -- 2.1 如 contact_id 目前为 NOT NULL，则改为允许为 NULL
    IF EXISTS (
      SELECT 1 FROM information_schema.columns 
       WHERE table_schema = DATABASE() 
         AND table_name = 'address_book' 
         AND column_name = 'contact_id' 
         AND is_nullable = 'NO'
    ) THEN
      ALTER TABLE address_book 
        MODIFY COLUMN contact_id INT UNSIGNED NULL COMMENT '联系人用户ID（内部用户，外部联系人则为空)';
    END IF;

    -- 2.2 新增 contact_type 字段（若不存在）
    IF NOT EXISTS (
      SELECT 1 FROM information_schema.columns 
       WHERE table_schema = DATABASE() AND table_name = 'address_book' AND column_name = 'contact_type'
    ) THEN
      ALTER TABLE address_book 
        ADD COLUMN contact_type VARCHAR(20) NOT NULL DEFAULT 'internal' COMMENT '联系人类型：internal-内部，external-外部' AFTER remark;
    END IF;

    -- 2.2 新增 external_id 字段（若不存在）
    IF NOT EXISTS (
      SELECT 1 FROM information_schema.columns 
       WHERE table_schema = DATABASE() AND table_name = 'address_book' AND column_name = 'external_id'
    ) THEN
      ALTER TABLE address_book 
        ADD COLUMN external_id INT UNSIGNED NULL COMMENT '外部联系人ID（external_contact.id）' AFTER contact_type;
    END IF;

    -- 2.2 为 external_id 添加普通索引（若不存在）
    IF NOT EXISTS (
      SELECT 1 FROM information_schema.statistics 
       WHERE table_schema = DATABASE() AND table_name = 'address_book' AND index_name = 'idx_external_id'
    ) THEN
      CREATE INDEX idx_external_id ON address_book(external_id);
    END IF;

    -- 2.2 添加联合唯一键 (user_id, external_id)（若不存在）
    IF NOT EXISTS (
      SELECT 1 FROM information_schema.statistics 
       WHERE table_schema = DATABASE() AND table_name = 'address_book' AND index_name = 'uk_user_external'
    ) THEN
      ALTER TABLE address_book ADD UNIQUE KEY uk_user_external (user_id, external_id);
    END IF;

    -- 2.3 历史数据兜底：确保 contact_type 有值
    SET @old_safe_updates := @@SQL_SAFE_UPDATES;
    SET SQL_SAFE_UPDATES = 0;
    UPDATE address_book SET contact_type = 'internal' WHERE contact_type IS NULL OR contact_type = '';
    SET SQL_SAFE_UPDATES = @old_safe_updates;

    -- 2.4 为 external_id 添加外键（若不存在）
    IF NOT EXISTS (
      SELECT 1 FROM information_schema.REFERENTIAL_CONSTRAINTS 
       WHERE constraint_schema = DATABASE() AND constraint_name = 'fk_ab_external_contact'
    ) THEN
      ALTER TABLE address_book 
        ADD CONSTRAINT fk_ab_external_contact 
        FOREIGN KEY (external_id) REFERENCES external_contact(id) 
        ON DELETE SET NULL ON UPDATE CASCADE;
    END IF;
  END IF;
END $$
CALL sp_upgrade_address_book_external() $$
DROP PROCEDURE sp_upgrade_address_book_external $$
DELIMITER ;