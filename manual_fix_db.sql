-- ========================
-- 手动修复：frequent_contact 缺少字段
-- 解决错误：Column 'is_favorite' is missing
-- ========================

-- 使用目标数据库
USE big_event;

-- 添加缺失的字段（兼容 MySQL 5.7：使用 information_schema 判断后动态添加）
SET @db := DATABASE();

-- 添加 is_favorite 列（若不存在）
SET @exists := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='frequent_contact' AND COLUMN_NAME='is_favorite');
SET @sql := IF(@exists=0,
  'ALTER TABLE frequent_contact ADD COLUMN is_favorite TINYINT(1) NOT NULL DEFAULT 0 COMMENT ''是否收藏：0-否，1-是''',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 添加 update_time 列（若不存在）
SET @exists := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='frequent_contact' AND COLUMN_NAME='update_time');
SET @sql := IF(@exists=0,
  'ALTER TABLE frequent_contact ADD COLUMN update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT ''更新时间''',
  'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 为现有数据设置默认值
UPDATE frequent_contact SET is_favorite = 0 WHERE is_favorite IS NULL;
UPDATE frequent_contact SET update_time = NOW() WHERE update_time IS NULL;

-- 查看当前数据
SELECT * FROM frequent_contact LIMIT 5;

-- ========================
-- 手动修复：创建缺失的 chat_group_member 表并迁移旧数据
-- 解决错误：Table 'big_event.chat_group_member' doesn't exist
-- 说明：代码使用表名 chat_group_member，现有初始化脚本使用了 group_member，且字段不完全一致。
-- 本修复将创建新表 chat_group_member，并从旧表 group_member 迁移数据。
-- ========================

-- 1) 创建新表（若不存在）
CREATE TABLE IF NOT EXISTS chat_group_member (
  id INT UNSIGNED PRIMARY KEY AUTO_INCREMENT COMMENT 'ID',
  group_id INT UNSIGNED NOT NULL COMMENT '群组ID',
  user_id INT UNSIGNED NOT NULL COMMENT '用户ID',
  role VARCHAR(20) DEFAULT 'member' COMMENT '角色：owner-群主，admin-管理员，member-普通成员',
  alias VARCHAR(50) DEFAULT NULL COMMENT '群内昵称',
  mute TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否禁言：0-否，1-是',
  join_time DATETIME NOT NULL COMMENT '加入时间',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  INDEX idx_group_id (group_id),
  INDEX idx_user_id (user_id),
  UNIQUE KEY uk_group_user (group_id, user_id)
) COMMENT='群组成员表（与代码保持一致）';

-- 2) 将旧表 group_member 的数据迁移到新表（仅在新表为空时执行）
INSERT INTO chat_group_member (id, group_id, user_id, role, alias, mute, join_time, create_time, update_time)
SELECT gm.id, gm.group_id, gm.user_id, gm.role, gm.nickname AS alias, 0 AS mute, gm.join_time, NOW(), NOW()
FROM group_member gm
WHERE NOT EXISTS (SELECT 1 FROM chat_group_member cgm LIMIT 1);

-- 3) 校验新表数据
SELECT COUNT(*) AS chat_group_member_count FROM chat_group_member;
SELECT * FROM chat_group_member LIMIT 5;

-- 备注：保留旧表 group_member 以兼容历史初始化脚本；应用侧全部使用 chat_group_member 表。
-- ... existing code ...

-- ========================
-- 手动修复：纠正用户110头像路径（从绝对URL改为相对路径）
-- 说明：将 user.user_pic 设置为实际存在的相对路径，以便通过 Vite 5173 代理访问。
-- ========================
USE big_event;

-- 更新用户ID=110的头像为实际存在的文件（相对路径）
UPDATE user
SET user_pic = '/uploads/avatars/user_110_9e545c7a-6115-443e-abfb-4d3a534fb8b3.png'
WHERE id = 110;

-- 验证结果
SELECT id, user_pic FROM user WHERE id = 110;

-- ========================
-- 一次性迁移：批量将 user.user_pic 的绝对URL 转换为相对路径（/uploads/...）
-- 说明：仅转换以 http:// 或 https:// 开头且包含 /uploads/ 的记录；已是相对路径的不会受影响。
-- ========================
USE big_event;

-- 预览将被转换的记录
SELECT id, user_pic FROM user 
WHERE user_pic LIKE 'http://%/uploads/%' OR user_pic LIKE 'https://%/uploads/%' 
LIMIT 20;

-- 执行转换：将 http(s)://<host>/uploads/avatars/xxx.png → /uploads/avatars/xxx.png
UPDATE user
SET user_pic = CONCAT('/uploads/', SUBSTRING_INDEX(user_pic, '/uploads/', -1))
WHERE user_pic LIKE 'http://%/uploads/%' OR user_pic LIKE 'https://%/uploads/%';

-- 验证转换结果（统计以相对路径开头的头像数）
SELECT COUNT(*) AS relative_avatar_count FROM user WHERE user_pic LIKE '/uploads/%';