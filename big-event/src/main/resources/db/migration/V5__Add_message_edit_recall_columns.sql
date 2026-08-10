-- 为chat_message表添加撤回和编辑相关字段
ALTER TABLE chat_message ADD COLUMN is_recalled BOOLEAN DEFAULT FALSE COMMENT '是否已撤回';
ALTER TABLE chat_message ADD COLUMN recall_time DATETIME NULL COMMENT '撤回时间';
ALTER TABLE chat_message ADD COLUMN is_edited BOOLEAN DEFAULT FALSE COMMENT '是否已编辑';
ALTER TABLE chat_message ADD COLUMN content_version INT DEFAULT 1 COMMENT '内容版本号（编辑次数+1）';

-- 为字段添加索引以提升查询性能
CREATE INDEX idx_chat_message_is_recalled ON chat_message(is_recalled);
CREATE INDEX idx_chat_message_is_edited ON chat_message(is_edited);