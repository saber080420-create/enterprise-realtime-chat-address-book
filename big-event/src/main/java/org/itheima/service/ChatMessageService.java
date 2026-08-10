package org.itheima.service;

import org.itheima.pojo.ChatMessage;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 聊天消息服务接口
 */
public interface ChatMessageService {
    
    /**
     * 根据ID查询消息
     * 
     * @param id 消息ID
     * @return 消息信息
     */
    ChatMessage findById(Long id);
    
    /**
     * 发送消息
     * 
     * @param chatMessage 消息信息
     * @return 发送的消息
     */
    ChatMessage sendMessage(ChatMessage chatMessage);
    
    /**
     * 更新消息
     * 
     * @param chatMessage 消息信息
     * @return 更新后的消息
     */
    ChatMessage updateMessage(ChatMessage chatMessage);

    /**
     * 编辑消息内容
     * 函数级注释：
     * - 仅允许发送者编辑自己的消息，并更新版本号与时间。
     * - 编辑后通过WebSocket通知相关用户。
     */
    ChatMessage editMessage(Long messageId, Integer userId, String newContent);

    /**
     * 撤回消息
     * 函数级注释：
     * - 仅允许发送者撤回自己的消息。
     * - 撤回后通过WebSocket通知相关用户。
     */
    ChatMessage recallMessage(Long messageId, Integer userId);
    
    /**
     * 删除消息
     * 
     * @param id 消息ID
     * @return 是否删除成功
     */
    boolean deleteMessage(Long id);
    
    /**
     * 删除消息（带用户ID验证）
     * 
     * @param messageId 消息ID
     * @param userId 用户ID（用于验证消息所有权）
     * @return 是否删除成功
     */
    boolean deleteMessage(Integer messageId, Integer userId);
    
    /**
     * 获取单聊历史消息
     * 
     * @param userId 当前用户ID
     * @param contactId 联系人ID
     * @param limit 消息数量限制
     * @param offset 偏移量
     * @return 消息列表
     */
    List<ChatMessage> getSingleChatHistory(Integer userId, Integer contactId, Integer limit, Integer offset);
    
    /**
     * 获取群聊历史消息
     * 
     * @param userId 当前用户ID（用于按接收者视图过滤）
     * @param groupId 群组ID
     * @param limit 消息数量限制
     * @param offset 偏移量
     * @return 消息列表（包含发送者信息的DTO，用于前端显示发送者头像与昵称）
     */
    List<org.itheima.pojo.dto.ChatMessageDTO> getGroupChatHistory(Integer userId, Integer groupId, Integer limit, Integer offset);
    
    /**
     * 标记消息为已读
     * 
     * @param messageId 消息ID
     * @param userId 用户ID
     * @return 是否标记成功
     */
    boolean markMessageAsRead(Long messageId, Integer userId);
    
    /**
     * 批量标记消息为已读
     * 
     * @param senderId 发送者ID
     * @param receiverId 接收者ID
     * @param chatType 聊天类型
     * @return 标记的消息数量
     */
    int markMessagesAsRead(Integer senderId, Integer receiverId, String chatType);
    
    /**
     * 统计用户的未读消息数量
     * @param receiverId 接收者用户ID
     * @return 未读消息数量
     */
    Long countUnreadMessages(Integer receiverId);

    /**
     * 获取用户的未读消息详细列表（包含发送者信息）
     * 函数级注释：
     * - 获取用户所有未读消息的详细信息
     * - 包含发送者的用户名、昵称、头像等信息
     * - 按创建时间倒序排列，最新消息在前
     * - 限制返回数量以避免数据过多
     */
    List<Map<String, Object>> findUnreadMessagesWithDetails(Integer receiverId, Integer limit);
    
    /**
     * 获取最近聊天列表
     * 
     * @param userId 用户ID
     * @return 最近聊天列表
     */
    List<Map<String, Object>> getRecentChats(Integer userId);
    
    /**
     * 获取最近聊天列表，带数量限制
     * 
     * @param userId 用户ID
     * @param limit 返回的聊天数量限制
     * @return 最近聊天列表
     */
    List<Map<String, Object>> getRecentChats(Integer userId, Integer limit);
    
    /**
     * 获取指定时间段内的消息统计
     * 
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return 消息统计
     */
    Map<String, Object> getMessageStatsByTimeRange(LocalDateTime startTime, LocalDateTime endTime);
    
    /**
     * 获取指定时间段内的消息类型统计
     * 
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return 消息类型统计
     */
    Map<String, Integer> getMessageTypeStatsByTimeRange(LocalDateTime startTime, LocalDateTime endTime);
    
    // 已移除：消息搜索（前端未接入）
    
    /**
     * 基于游标的单聊历史消息
     * 函数级注释：
     * - 用于“加载更多”场景；当 beforeTime/beforeId 为 null 时返回最新的前 limit 条。
     * - 排序规则与持久层一致（create_time desc, id desc），以保证稳定分页。
     *
     * @param userId 当前用户ID
     * @param contactId 联系人ID
     * @param beforeTime 游标时间（LocalDateTime），仅返回该时间点之前的记录
     * @param beforeId 游标ID（当 create_time 相等时，取 id 小于该值的记录）
     * @param limit 返回条数
     * @return 历史消息列表（倒序）
     */
    List<ChatMessage> getSingleChatHistoryByCursor(Integer userId, Integer contactId, LocalDateTime beforeTime, Long beforeId, Integer limit);
}