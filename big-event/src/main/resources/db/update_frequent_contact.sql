-- 修改 frequent_contact 表，添加 is_favorite 字段
ALTER TABLE frequent_contact
ADD COLUMN is_favorite TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否收藏：0-否，1-是',
ADD COLUMN update_time DATETIME NOT NULL DEFAULT NOW() COMMENT '更新时间';