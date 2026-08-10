-- 个人通讯录数据库表结构设计
-- 需要执行此脚本来支持"添加联系人到通讯录"功能

-- 1. 通讯录分组表（每个用户可以创建多个分组来管理联系人）
CREATE TABLE IF NOT EXISTS address_group (
    id INT AUTO_INCREMENT PRIMARY KEY COMMENT '分组ID',
    user_id INT NOT NULL COMMENT '分组所属用户ID',
    group_name VARCHAR(50) NOT NULL COMMENT '分组名称',
    is_default TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否默认分组：0-否，1-是',
    sort_order INT NOT NULL DEFAULT 0 COMMENT '排序序号',
    create_time DATETIME NOT NULL DEFAULT NOW() COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT NOW() ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    
    UNIQUE KEY uk_user_group_name (user_id, group_name) COMMENT '同一用户下分组名称唯一',
    INDEX idx_user_id (user_id) COMMENT '用户ID索引',
    FOREIGN KEY fk_ag_user_id (user_id) REFERENCES user(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='通讯录分组表';

-- 2. 个人通讯录表（用户自己添加的联系人）
CREATE TABLE IF NOT EXISTS address_book (
    id INT AUTO_INCREMENT PRIMARY KEY COMMENT '记录ID',
    user_id INT NOT NULL COMMENT '通讯录所属用户ID',
    contact_id INT NOT NULL COMMENT '联系人用户ID',
    group_id INT NOT NULL COMMENT '所属分组ID',
    alias VARCHAR(50) NULL COMMENT '联系人备注名称（可选）',
    tags VARCHAR(200) NULL COMMENT '联系人标签（JSON数组格式，可空）',
    remark TEXT NULL COMMENT '备注信息',
    create_time DATETIME NOT NULL DEFAULT NOW() COMMENT '添加时间',
    update_time DATETIME NOT NULL DEFAULT NOW() ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    
    UNIQUE KEY uk_user_contact (user_id, contact_id) COMMENT '同一用户不能重复添加同一联系人',
    INDEX idx_user_id (user_id) COMMENT '用户ID索引',
    INDEX idx_group_id (group_id) COMMENT '分组ID索引',
    INDEX idx_contact_id (contact_id) COMMENT '联系人ID索引',
    FOREIGN KEY fk_ab_user_id (user_id) REFERENCES user(id) ON DELETE CASCADE,
    FOREIGN KEY fk_ab_contact_id (contact_id) REFERENCES user(id) ON DELETE CASCADE,
    FOREIGN KEY fk_ab_group_id (group_id) REFERENCES address_group(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='个人通讯录表';

-- 3. 为每个现有用户创建默认分组（可选执行）
INSERT INTO address_group (user_id, group_name, is_default, sort_order)
SELECT 
    id, 
    '默认分组' as group_name,
    1 as is_default,
    0 as sort_order
FROM user 
WHERE NOT EXISTS (
    SELECT 1 FROM address_group WHERE address_group.user_id = user.id AND is_default = 1
);

-- 4. 验证表结构
DESCRIBE address_group;
DESCRIBE address_book;

-- 5. 查看初始数据
SELECT 
    ag.id,
    ag.user_id,
    u.username,
    ag.group_name,
    ag.is_default
FROM address_group ag
LEFT JOIN user u ON ag.user_id = u.id
ORDER BY ag.user_id, ag.sort_order
LIMIT 10;