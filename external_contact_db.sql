-- 外部联系人与通讯录扩展脚本
-- 作用：新增 external_contact 表，扩展 address_book 支持外部联系人

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
) COMMENT='外部联系人表';

-- 2) 扩展 address_book 表，支持外部联系人
-- 2.1 允许 contact_id 为空（仅外部联系人记录为空）
ALTER TABLE address_book 
    MODIFY COLUMN contact_id INT UNSIGNED NULL COMMENT '联系人用户ID（内部用户，外部联系人则为空)';

-- 2.2 新增 contact_type 与 external_id 字段（若不存在）
ALTER TABLE address_book 
    ADD COLUMN IF NOT EXISTS contact_type VARCHAR(20) NOT NULL DEFAULT 'internal' COMMENT '联系人类型：internal-内部，external-外部' AFTER remark,
    ADD COLUMN IF NOT EXISTS external_id INT UNSIGNED NULL COMMENT '外部联系人ID（external_contact.id）' AFTER contact_type,
    ADD INDEX IF NOT EXISTS idx_external_id (external_id),
    ADD UNIQUE KEY IF NOT EXISTS uk_user_external (user_id, external_id);

-- 2.3 为安全起见，确保历史数据类型标为 internal
UPDATE address_book SET contact_type = 'internal' WHERE contact_type IS NULL OR contact_type = '';

-- 2.4 为外部联系人建立外键（可选，允许为空时外键不生效）
ALTER TABLE address_book 
    ADD CONSTRAINT IF NOT EXISTS fk_ab_external_contact 
    FOREIGN KEY (external_id) REFERENCES external_contact(id) 
    ON DELETE SET NULL ON UPDATE CASCADE;