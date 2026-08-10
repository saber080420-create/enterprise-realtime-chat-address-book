package org.itheima.mapper;

import org.apache.ibatis.annotations.*;
import org.itheima.pojo.ChatMessage;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 聊天消息Mapper接口
 */
@Mapper
public interface ChatMessageMapper {
    
    /**
     * 根据ID查询消息
     * 
     * @param id 消息ID
     * @return 消息信息
     */
    @Select("select * from chat_message where id = #{id}")
    ChatMessage findById(Long id);
    
    /**
     * 添加消息
     * 
     * @param chatMessage 消息信息
     */
    @Insert("insert into chat_message(sender_id, receiver_id, message_type, content, chat_type, " +
            "group_id, is_read, is_recalled, recall_time, is_edited, content_version, origin_message_id, create_time, update_time) " +
            "values(#{senderId}, #{receiverId}, #{messageType}, #{content}, #{chatType}, " +
            "#{groupId}, #{isRead}, #{isRecalled}, #{recallTime}, #{isEdited}, #{contentVersion}, #{originMessageId}, now(), now())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    void add(ChatMessage chatMessage);
    
    /**
     * 更新消息（通用：用于已读/时间）
     * 
     * @param chatMessage 消息信息
     */
    @Update("update chat_message set is_read = #{isRead}, read_time = #{readTime}, " +
            "update_time = now() where id = #{id}")
    void update(ChatMessage chatMessage);

    /**
     * 编辑消息内容
     * 函数级注释：
     * - 更新文本内容，并将 is_edited = true，content_version 自增，update_time 刷新。
     */
    @Update("update chat_message set content = #{content}, is_edited = true, content_version = content_version + 1, update_time = now() where id = #{id}")
    void updateEdited(Long id, String content);

    /**
     * 撤回消息
     * 函数级注释：
     * - 将 is_recalled = true，recall_time = now，update_time 刷新。
     */
    @Update("update chat_message set is_recalled = true, recall_time = now(), update_time = now() where id = #{id}")
    void updateRecalled(Long id);
    
    /**
     * 按 origin_message_id 批量撤回群聊消息副本
     * 函数级注释：
     * - 用于群聊“写时扇出”场景，origin_message_id 关联同一条群消息的所有用户副本；
     * - 撤回时一次性将所有相关副本标记为撤回，保持会话内多端一致性；
     * - 历史数据可能没有该关联（为 NULL），此方法仅影响设置了 origin_message_id 的记录。
     * @param originMessageId 群聊消息的统一关联ID（通常为发送者副本ID）
     */
    @Update("update chat_message set is_recalled = true, recall_time = now(), update_time = now() where origin_message_id = #{originMessageId}")
    void updateRecalledByOriginId(Long originMessageId);
    
    /**
     * 删除消息
     * 
     * @param id 消息ID
     */
    @Delete("delete from chat_message where id = #{id}")
    void deleteById(Long id);
    
    /**
     * 删除指定发送者的所有消息
     * 
     * @param senderId 发送者ID
     */
    @Delete("delete from chat_message where sender_id = #{senderId}")
    void deleteBySenderId(Integer senderId);
    
    /**
     * 删除指定接收者的所有消息
     * 
     * @param receiverId 接收者ID
     */
    @Delete("delete from chat_message where receiver_id = #{receiverId}")
    void deleteByReceiverId(Integer receiverId);
    
    /**
     * 查询单聊消息历史（偏移量分页）
     */
    @Select("select * from chat_message where " +
            "((sender_id = #{userId1} and receiver_id = #{userId2}) or " +
            "(sender_id = #{userId2} and receiver_id = #{userId1})) " +
            "and chat_type = 'single' " +
            "order by create_time desc limit #{limit} offset #{offset}")
    List<ChatMessage> findSingleChatHistory(Integer userId1, Integer userId2, Integer limit, Integer offset);
    
    /**
     * 查询群聊消息历史（按用户视图）
     * 函数级注释：
     * - 返回某个群组内，指定用户的消息副本（写时扇出下，每人一条副本）。
     * - 仅按 receiver_id = userId 过滤即可覆盖“别人发给我”和“我在群里的自有副本”。
     */
    @Select("select * from chat_message where group_id = #{groupId} and chat_type = 'group' and receiver_id = #{userId} " +
            "order by create_time desc limit #{limit} offset #{offset}")
    List<ChatMessage> findGroupChatHistoryByUser(Integer groupId, Integer userId, Integer limit, Integer offset);
    
    /**
     * 基于游标的单聊历史查询
     * 函数级注释：
     * - 用于在“加载更多”场景中获取某个时间点之前的更旧消息。
     * - 稳定排序：先按 create_time 降序，再按 id 降序，避免同一秒内多条消息顺序不稳定。
     * - 过滤条件：当提供 beforeTime 时，仅返回 (create_time < beforeTime) 或 (create_time = beforeTime 且 id < beforeId) 的记录。
     * - 当 beforeTime 为空时，返回最新的前 limit 条记录。
     */
    @Select({
        "<script>",
        "select * from chat_message",
        "where ((sender_id = #{userId1} and receiver_id = #{userId2}) or (sender_id = #{userId2} and receiver_id = #{userId1}))",
        "and chat_type = 'single'",
        "<if test='beforeTime != null'>",
        "  and (create_time &lt; #{beforeTime} or (create_time = #{beforeTime} and id &lt; #{beforeId}))",
        "</if>",
        "order by create_time desc, id desc",
        "limit #{limit}",
        "</script>"
    })
    List<ChatMessage> findSingleChatHistoryByCursor(Integer userId1, Integer userId2, LocalDateTime beforeTime, Long beforeId, Integer limit);
    
    /**
     * 标记消息为已读
     */
    @Update("update chat_message set is_read = true, read_time = #{readTime}, " +
            "update_time = now() where id = #{messageId}")
    void markAsRead(Long messageId, LocalDateTime readTime);
    
    /**
     * 批量标记用户的未读消息为已读（单聊）
     */
    @Update("update chat_message set is_read = true, read_time = #{readTime}, " +
            "update_time = now() where sender_id = #{senderId} and receiver_id = #{receiverId} " +
            "and chat_type = 'single' and is_read = false")
    int markAllAsRead(Integer senderId, Integer receiverId, LocalDateTime readTime);
    
    /**
     * 批量标记群组的未读消息为已读
     */
    @Update("update chat_message set is_read = true, read_time = #{readTime}, " +
            "update_time = now() where group_id = #{groupId} and chat_type = 'group' " +
            "and receiver_id = #{userId} and is_read = false")
    int markGroupMessagesAsRead(Integer userId, Integer groupId, LocalDateTime readTime);
    
    /**
     * 统计用户的未读消息数量
     */
    @Select("select count(*) from chat_message where receiver_id = #{receiverId} and is_read = false")
    Long countUnreadMessages(Integer receiverId);

    /**
     * 获取用户的未读消息详细列表（包含发送者信息）
     * 函数级注释：
     * - 获取用户所有未读消息的详细信息
     * - 包含发送者的用户名、昵称、头像等信息
     * - 按创建时间倒序排列，最新消息在前
     * - 限制返回数量以避免数据过多
     */
    @Select("SELECT cm.*, " +
            "COALESCE(u.nickname, u.username) as sender_name, " +
            "u.user_pic as sender_avatar, " +
            "CASE WHEN cm.chat_type = 'group' THEN g.group_name ELSE NULL END as group_name " +
            "FROM chat_message cm " +
            "LEFT JOIN user u ON cm.sender_id = u.id " +
            "LEFT JOIN chat_group g ON cm.group_id = g.id " +
            "WHERE cm.receiver_id = #{receiverId} AND cm.is_read = false " +
            "ORDER BY cm.create_time DESC " +
            "LIMIT #{limit}")
    List<Map<String, Object>> findUnreadMessagesWithDetails(Integer receiverId, Integer limit);

    /**
     * 统计用户在特定聊天中的未读消息数量（单聊）
     */
    @Select("select count(*) from chat_message where sender_id = #{senderId} and " +
            "receiver_id = #{receiverId} and chat_type = 'single' and is_read = false")
    Long countUnreadMessagesInSingleChat(Integer senderId, Integer receiverId);
    
    /**
     * 统计用户在特定群组中的未读消息数量
     */
    @Select("select count(*) from chat_message where group_id = #{groupId} and " +
            "chat_type = 'group' and receiver_id = #{userId} and is_read = false")
    Long countUnreadMessagesInGroupChat(Integer userId, Integer groupId);
    
    /**
     * 获取用户最近的聊天列表（默认20条）
     */
    @Select(
            "SELECT rc.chat_type, rc.contact_id, rc.group_id, rc.last_time, " +
            "CASE WHEN rc.chat_type = 'single' THEN COALESCE(u.nickname, u.username) ELSE g.group_name END AS name, " +
            "CASE WHEN rc.chat_type = 'single' THEN u.user_pic ELSE NULL END AS avatar, " +
            "CASE WHEN rc.chat_type = 'single' THEN " +
            "   (SELECT COUNT(*) FROM chat_message cm WHERE cm.chat_type = 'single' AND cm.sender_id = rc.contact_id AND cm.receiver_id = #{userId} AND cm.is_read = false) " +
            " ELSE " +
            "   (SELECT COUNT(*) FROM chat_message cm WHERE cm.chat_type = 'group' AND cm.group_id = rc.group_id AND cm.receiver_id = #{userId} AND cm.is_read = false) " +
            "END AS unread_count, " +
            "CASE WHEN rc.chat_type = 'single' THEN " +
            "   (SELECT content FROM chat_message cm WHERE cm.chat_type = 'single' AND ((cm.sender_id = rc.contact_id AND cm.receiver_id = #{userId}) OR (cm.sender_id = #{userId} AND cm.receiver_id = rc.contact_id)) ORDER BY cm.create_time DESC LIMIT 1) " +
            " ELSE " +
            "   (SELECT content FROM chat_message cm WHERE cm.chat_type = 'group' AND cm.group_id = rc.group_id ORDER BY cm.create_time DESC LIMIT 1) " +
            "END AS last_content " +
            "FROM ( " +
            "    SELECT sender_id AS contact_id, 'single' AS chat_type, NULL AS group_id, MAX(create_time) AS last_time " +
            "    FROM chat_message WHERE receiver_id = #{userId} AND chat_type = 'single' GROUP BY sender_id " +
            "    UNION " +
            "    SELECT receiver_id AS contact_id, 'single' AS chat_type, NULL AS group_id, MAX(create_time) AS last_time " +
            "    FROM chat_message WHERE sender_id = #{userId} AND chat_type = 'single' GROUP BY receiver_id " +
            "    UNION " +
            "    SELECT NULL AS contact_id, 'group' AS chat_type, group_id, MAX(create_time) AS last_time " +
            "    FROM chat_message WHERE (sender_id = #{userId} OR receiver_id = #{userId}) AND chat_type = 'group' GROUP BY group_id " +
            ") rc " +
            "LEFT JOIN user u ON rc.chat_type = 'single' AND u.id = rc.contact_id " +
            "LEFT JOIN chat_group g ON rc.chat_type = 'group' AND g.id = rc.group_id " +
            "WHERE (rc.chat_type = 'group') OR (rc.chat_type = 'single' AND rc.contact_id IN (SELECT id FROM user)) " +
            "ORDER BY rc.last_time DESC " +
            "LIMIT 20"
    )
    List<Map<String, Object>> getRecentChatsDefault(Integer userId);
    
    /**
     * 获取用户最近的聊天列表（limit可调）
     */
    @Select(
            "SELECT rc.chat_type, rc.contact_id, rc.group_id, rc.last_time, " +
            "CASE WHEN rc.chat_type = 'single' THEN COALESCE(u.nickname, u.username) ELSE g.group_name END AS name, " +
            "CASE WHEN rc.chat_type = 'single' THEN u.user_pic ELSE NULL END AS avatar, " +
            "CASE WHEN rc.chat_type = 'single' THEN " +
            "   (SELECT COUNT(*) FROM chat_message cm WHERE cm.chat_type = 'single' AND cm.sender_id = rc.contact_id AND cm.receiver_id = #{userId} AND cm.is_read = false) " +
            " ELSE " +
            "   (SELECT COUNT(*) FROM chat_message cm WHERE cm.chat_type = 'group' AND cm.group_id = rc.group_id AND cm.receiver_id = #{userId} AND cm.is_read = false) " +
            "END AS unread_count, " +
            "CASE WHEN rc.chat_type = 'single' THEN " +
            "   (SELECT content FROM chat_message cm WHERE cm.chat_type = 'single' AND ((cm.sender_id = rc.contact_id AND cm.receiver_id = #{userId}) OR (cm.sender_id = #{userId} AND cm.receiver_id = rc.contact_id)) ORDER BY cm.create_time DESC LIMIT 1) " +
            " ELSE " +
            "   (SELECT content FROM chat_message cm WHERE cm.chat_type = 'group' AND cm.group_id = rc.group_id ORDER BY cm.create_time DESC LIMIT 1) " +
            "END AS last_content " +
            "FROM ( " +
            "    SELECT sender_id AS contact_id, 'single' AS chat_type, NULL AS group_id, MAX(create_time) AS last_time " +
            "    FROM chat_message WHERE receiver_id = #{userId} AND chat_type = 'single' GROUP BY sender_id " +
            "    UNION " +
            "    SELECT receiver_id AS contact_id, 'single' AS chat_type, NULL AS group_id, MAX(create_time) AS last_time " +
            "    FROM chat_message WHERE sender_id = #{userId} AND chat_type = 'single' GROUP BY receiver_id " +
            "    UNION " +
            "    SELECT NULL AS contact_id, 'group' AS chat_type, group_id, MAX(create_time) AS last_time " +
            "    FROM chat_message WHERE (sender_id = #{userId} OR receiver_id = #{userId}) AND chat_type = 'group' GROUP BY group_id " +
            ") rc " +
            "LEFT JOIN user u ON rc.chat_type = 'single' AND u.id = rc.contact_id " +
            "LEFT JOIN chat_group g ON rc.chat_type = 'group' AND g.id = rc.group_id " +
            "WHERE (rc.chat_type = 'group') OR (rc.chat_type = 'single' AND rc.contact_id IN (SELECT id FROM user)) " +
            "ORDER BY rc.last_time DESC " +
            "LIMIT #{limit}"
    )
    List<Map<String, Object>> getRecentChats(Integer userId, Integer limit);

    /**
     * 获取指定时间段内的消息统计
     */
    @Select("SELECT DATE(create_time) as date, COUNT(*) as count " +
            "FROM chat_message " +
            "WHERE create_time BETWEEN #{startTime} AND #{endTime} " +
            "GROUP BY DATE(create_time) " +
            "ORDER BY date")
    Map<String, Object> getMessageStatsByTimeRange(LocalDateTime startTime, LocalDateTime endTime);

    /**
     * 获取指定时间段内的消息类型统计
     */
    @Select("SELECT message_type as messageType, COUNT(*) as count " +
            "FROM chat_message " +
            "WHERE create_time BETWEEN #{startTime} AND #{endTime} " +
            "GROUP BY message_type")
    List<Map<String, Object>> getMessageTypeStatsByTimeRange(LocalDateTime startTime, LocalDateTime endTime);

    // 已移除：消息搜索
    
    /**
     * 更新消息的 origin_message_id 字段
     * 函数级注释：
     * - 用于将发送者的第一条群聊副本的 origin_message_id 设置为其自身的 id，
     *   以作为同一条群聊消息的关联键；随后其他成员副本在插入时直接使用该键。
     * @param id 消息主键ID
     * @param originMessageId 关联的原始消息ID
     */
    @Update("update chat_message set origin_message_id = #{originMessageId}, update_time = now() where id = #{id}")
    void updateOriginMessageId(Long id, Long originMessageId);
}