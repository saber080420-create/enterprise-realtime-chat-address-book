-- V6: 为群聊消息副本建立统一关联ID，用于跨副本同步撤回/编辑等操作
-- 设计说明：
-- 1) 在 chat_message 表新增 origin_message_id 列（可空），用于将同一条群聊消息的所有“写时扇出”副本关联起来；
-- 2) 为该列创建单列索引，便于后续按 origin_message_id 批量更新和查询；
-- 3) 历史数据保持为 NULL，不影响既有功能与数据；

ALTER TABLE chat_message
  ADD COLUMN origin_message_id BIGINT NULL COMMENT '同一条群消息的统一关联ID（通常为发送者副本的ID）';

CREATE INDEX idx_chat_message_origin ON chat_message (origin_message_id);