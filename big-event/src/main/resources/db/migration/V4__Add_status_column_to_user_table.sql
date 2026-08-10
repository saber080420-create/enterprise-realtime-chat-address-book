-- 添加status字段到user表
ALTER TABLE user ADD COLUMN status VARCHAR(20) DEFAULT 'active' COMMENT '用户状态：active-正常，inactive-禁用';

-- 更新所有现有用户的状态为active
UPDATE user SET status = 'active' WHERE status IS NULL;