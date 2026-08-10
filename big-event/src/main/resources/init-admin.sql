-- 初始化系统管理员用户
INSERT INTO user (username, password, nickname, realname, email, department_id, role, create_time, update_time)
VALUES ('admin', '96e79218965eb72c92a549dd5a330112', '系统管理员', '管理员', 'admin@example.com', 1, 'system_admin', NOW(), NOW())
ON DUPLICATE KEY UPDATE role = 'system_admin';

-- 密码为：111111（MD5加密后的值）