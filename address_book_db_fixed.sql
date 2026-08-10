-- 个人通讯录数据库表结构设计（修复版）
-- 兼容现有项目的SQL语法风格

-- 切换到项目数据库
USE big_event;

-- 1. 通讯录分组表
CREATE TABLE address_group (
    id int unsigned primary key auto_increment comment '分组ID',
    user_id int unsigned not null comment '分组所属用户ID',
    group_name varchar(50) not null comment '分组名称',
    is_default tinyint(1) not null default 0 comment '是否默认分组：0-否，1-是',
    sort_order int not null default 0 comment '排序序号',
    create_time datetime not null comment '创建时间',
    update_time datetime not null comment '更新时间',
    
    unique key uk_user_group_name (user_id, group_name),
    index idx_user_id (user_id)
) comment '通讯录分组表';

-- 2. 个人通讯录表
CREATE TABLE address_book (
    id int unsigned primary key auto_increment comment '记录ID',
    user_id int unsigned not null comment '通讯录所属用户ID',
    contact_id int unsigned not null comment '联系人用户ID',
    group_id int unsigned not null comment '所属分组ID',
    alias varchar(50) default '' comment '联系人备注名称',
    tags varchar(200) default '' comment '联系人标签',
    remark text comment '备注信息',
    create_time datetime not null comment '添加时间',
    update_time datetime not null comment '更新时间',
    
    unique key uk_user_contact (user_id, contact_id),
    index idx_user_id (user_id),
    index idx_group_id (group_id),
    index idx_contact_id (contact_id)
) comment '个人通讯录表';

-- 3. 添加外键约束
ALTER TABLE address_group
ADD CONSTRAINT fk_address_group_user
FOREIGN KEY (user_id) REFERENCES user(id) ON DELETE CASCADE ON UPDATE CASCADE;

ALTER TABLE address_book
ADD CONSTRAINT fk_address_book_user
FOREIGN KEY (user_id) REFERENCES user(id) ON DELETE CASCADE ON UPDATE CASCADE,
ADD CONSTRAINT fk_address_book_contact
FOREIGN KEY (contact_id) REFERENCES user(id) ON DELETE CASCADE ON UPDATE CASCADE,
ADD CONSTRAINT fk_address_book_group
FOREIGN KEY (group_id) REFERENCES address_group(id) ON DELETE CASCADE ON UPDATE CASCADE;

-- 4. 为每个现有用户创建默认分组
INSERT INTO address_group (user_id, group_name, is_default, sort_order, create_time, update_time)
SELECT 
    id, 
    '默认分组' as group_name,
    1 as is_default,
    0 as sort_order,
    NOW() as create_time,
    NOW() as update_time
FROM user 
WHERE NOT EXISTS (
    SELECT 1 FROM address_group WHERE address_group.user_id = user.id AND is_default = 1
);

-- 5. 验证表结构
DESCRIBE address_group;
DESCRIBE address_book;

-- 6. 查看创建的默认分组
SELECT 
    ag.id,
    ag.user_id,
    u.username,
    ag.group_name,
    ag.is_default
FROM address_group ag
LEFT JOIN user u ON ag.user_id = u.id
ORDER BY ag.user_id, ag.sort_order;