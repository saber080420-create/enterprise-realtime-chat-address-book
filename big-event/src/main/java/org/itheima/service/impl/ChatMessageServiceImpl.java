package org.itheima.service.impl;

import org.itheima.mapper.ChatMessageMapper;
import org.itheima.mapper.UserMapper;
import org.itheima.pojo.ChatMessage;
import org.itheima.service.ChatMessageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.itheima.pojo.User;
import org.itheima.pojo.dto.ChatMessageDTO;
import org.itheima.mapper.ChatGroupMapper;
import org.itheima.pojo.ChatGroup;
import org.itheima.service.WebSocketService;
import org.itheima.mapper.ChatGroupMemberMapper;
// 新增：引入Spring @Value 与集合工具
import org.springframework.beans.factory.annotation.Value;
import java.util.Collections;

/**
 * 聊天消息服务实现类
 */
@Service
public class ChatMessageServiceImpl implements ChatMessageService {

    @Autowired
    private ChatMessageMapper chatMessageMapper;
    
    @Autowired
    private UserMapper userMapper;

    // 新增：注入WebSocketService用于消息推送
    @Autowired
    private WebSocketService webSocketService;

    // 新增：注入ChatGroupMapper用于群组信息查询
    @Autowired
    private ChatGroupMapper chatGroupMapper;

    @Autowired
    private ChatGroupMemberMapper chatGroupMemberMapper;

    // 新增：历史消息功能开关（默认开启）。当 application.yml 配置 chat.history.enabled=false 时，历史接口统一短路返回空集合。
    @Value("${chat.history.enabled:true}")
    private boolean historyEnabled;

    @Override
    public ChatMessage findById(Long id) {
        return chatMessageMapper.findById(id);
    }

    @Override
    @Transactional
    public ChatMessage sendMessage(ChatMessage chatMessage) {
        // 新增：单聊接收者存在性与状态校验
        if ("single".equals(chatMessage.getChatType())) {
            Integer receiverId = chatMessage.getReceiverId();
            if (receiverId == null) {
                throw new RuntimeException("接收者不能为空");
            }
            User receiver = userMapper.findById(receiverId);
            if (receiver == null) {
                throw new RuntimeException("接收者不存在或已被删除，无法发送消息");
            }
            if ("inactive".equals(receiver.getStatus())) {
                throw new RuntimeException("接收者账号已被禁用，无法发送消息");
            }
        }

        // 统一设置创建/更新时间
        LocalDateTime now = LocalDateTime.now();

        // 分类型处理写入
        if ("group".equals(chatMessage.getChatType())) {
            // 函数级注释：
            // - 群聊采用“写时扇出”策略，为群内每位成员写入一条个人视图消息记录（receiver_id = 成员userId）。
            // - 新增：以发送者的第一条副本ID作为 origin_message_id，将其余成员副本的 origin_message_id 设为同一值。
            Integer groupId = chatMessage.getGroupId();
            if (groupId == null) {
                throw new RuntimeException("群聊消息缺少 groupId");
            }
            Integer senderId = chatMessage.getSenderId();
            // 查询群成员
            List<org.itheima.pojo.ChatGroupMember> members = chatGroupMemberMapper.findByGroupId(groupId);
            if (members == null || members.isEmpty()) {
                throw new RuntimeException("群组暂无成员，无法发送消息");
            }

            ChatMessage myCopy = null; // 返回发送者自己的副本
            Long originId = null; // 发送者第一条副本的ID

            // 第一步：先为发送者插入自己的副本，拿到ID并回写为origin_message_id
            for (org.itheima.pojo.ChatGroupMember m : members) {
                if (m.getUserId() != null && m.getUserId().equals(senderId)) {
                    ChatMessage copy = new ChatMessage();
                    copy.setSenderId(senderId);
                    copy.setReceiverId(senderId);
                    copy.setMessageType(chatMessage.getMessageType());
                    copy.setContent(chatMessage.getContent());
                    copy.setChatType("group");
                    copy.setGroupId(groupId);
                    // 自己的副本标记为已读
                    copy.setIsRead(true);
                    copy.setReadTime(now);
                    // 初始编辑/撤回/版本
                    copy.setIsRecalled(false);
                    copy.setIsEdited(false);
                    copy.setContentVersion(1);
                    copy.setCreateTime(now);
                    copy.setUpdateTime(now);

                    chatMessageMapper.add(copy); // 拿到自增id
                    originId = copy.getId();
                    chatMessageMapper.updateOriginMessageId(originId, originId);
                    copy.setOriginMessageId(originId);
                    myCopy = copy;
                    break;
                }
            }
            if (originId == null) {
                throw new RuntimeException("您不是该群的成员，无法发送群消息");
            }

            // 第二步：为其余成员插入副本（未读），设置相同的origin_message_id
            for (org.itheima.pojo.ChatGroupMember m : members) {
                Integer memberUserId = m.getUserId();
                if (memberUserId != null && memberUserId.equals(senderId)) continue; // 跳过发送者

                ChatMessage copy = new ChatMessage();
                copy.setSenderId(senderId);
                copy.setReceiverId(memberUserId);
                copy.setMessageType(chatMessage.getMessageType());
                copy.setContent(chatMessage.getContent());
                copy.setChatType("group");
                copy.setGroupId(groupId);
                copy.setIsRead(false);
                copy.setReadTime(null);
                copy.setIsRecalled(false);
                copy.setIsEdited(false);
                copy.setContentVersion(1);
                copy.setCreateTime(now);
                copy.setUpdateTime(now);
                copy.setOriginMessageId(originId);
                chatMessageMapper.add(copy);
            }

            // 使用发送者副本构建DTO并推送给群内其他成员
            ChatMessageDTO dto = buildChatMessageDTO(myCopy);
            webSocketService.sendChatMessageToGroup(groupId, dto, senderId);
            return myCopy;
        }

        // 默认：单聊按原逻辑写入一条记录给接收者
        chatMessage.setCreateTime(now);
        chatMessage.setUpdateTime(now);
        chatMessage.setIsRead(false);
        if (chatMessage.getIsRecalled() == null) chatMessage.setIsRecalled(false);
        if (chatMessage.getIsEdited() == null) chatMessage.setIsEdited(false);
        if (chatMessage.getContentVersion() == null) chatMessage.setContentVersion(1);
        chatMessageMapper.add(chatMessage);

        ChatMessageDTO dto = buildChatMessageDTO(chatMessage);
        if ("single".equals(chatMessage.getChatType())) {
            // 单聊：直接推送给接收者
            webSocketService.sendChatMessageToUser(chatMessage.getReceiverId(), dto);
        } else if ("group".equals(chatMessage.getChatType())) {
            // 兜底：理论上不会走到这里（上面已处理group），保留兼容
            webSocketService.sendChatMessageToGroup(chatMessage.getGroupId(), dto, chatMessage.getSenderId());
        }
        
        return chatMessage;
    }

    @Override
    @Transactional
    public ChatMessage updateMessage(ChatMessage chatMessage) {
        // 检查消息是否存在
        ChatMessage existingMessage = chatMessageMapper.findById(chatMessage.getId());
        if (existingMessage == null) {
            throw new RuntimeException("消息不存在");
        }
        
        // 更新消息内容和更新时间
        chatMessage.setUpdateTime(LocalDateTime.now());
        chatMessageMapper.update(chatMessage);
        
        return chatMessage;
    }

    /**
     * 编辑消息内容
     * 函数级注释：
     * - 仅允许发送者编辑自己的消息。
     * - 持久层将自动设置 is_edited=true，content_version 自增，update_time 刷新。
     * - 编辑后通过WebSocket将最新消息DTO推送给对端（单聊）或群内成员（群聊）。
     */
    @Override
    @Transactional
    public ChatMessage editMessage(Long messageId, Integer userId, String newContent) {
        ChatMessage existing = chatMessageMapper.findById(messageId);
        if (existing == null) {
            throw new RuntimeException("消息不存在");
        }
        if (!existing.getSenderId().equals(userId)) {
            throw new RuntimeException("无权编辑此消息");
        }
        if (Boolean.TRUE.equals(existing.getIsRecalled())) {
            throw new RuntimeException("消息已被撤回，无法编辑");
        }

        // 更新内容（Mapper负责设置 is_edited 与 content_version）
        chatMessageMapper.updateEdited(messageId, newContent);
        // 重新查询最新记录
        ChatMessage updated = chatMessageMapper.findById(messageId);

        // 推送WebSocket编辑通知
        ChatMessageDTO dto = buildChatMessageDTO(updated);
        if ("single".equals(updated.getChatType())) {
            webSocketService.sendChatMessageEditToUser(updated.getReceiverId(), dto);
        } else if ("group".equals(updated.getChatType())) {
            webSocketService.sendChatMessageEditToGroup(updated.getGroupId(), dto, updated.getSenderId());
        }
        return updated;
    }

    /**
     * 撤回消息
     * 函数级注释：
     * - 仅允许发送者撤回自己的消息。
     * - 撤回后，通过WebSocket向对端（单聊）或群内成员（群聊）推送撤回通知。
     */
    @Override
    @Transactional
    public ChatMessage recallMessage(Long messageId, Integer userId) {
        ChatMessage existing = chatMessageMapper.findById(messageId);
        if (existing == null) {
            throw new RuntimeException("消息不存在");
        }
        if (!existing.getSenderId().equals(userId)) {
            throw new RuntimeException("无权撤回此消息");
        }
        if (Boolean.TRUE.equals(existing.getIsRecalled())) {
            return existing; // 已撤回，无需重复操作
        }

        // 执行撤回
        // 单聊：保持原有按ID撤回；群聊：按origin_message_id批量撤回
        if ("group".equals(existing.getChatType()) && existing.getOriginMessageId() != null) {
            chatMessageMapper.updateRecalledByOriginId(existing.getOriginMessageId());
        } else {
            chatMessageMapper.updateRecalled(messageId);
        }
        ChatMessage recalled = chatMessageMapper.findById(messageId);

        // 推送WebSocket撤回通知
        ChatMessageDTO dto = buildChatMessageDTO(recalled);
        if ("single".equals(recalled.getChatType())) {
            webSocketService.sendChatMessageRecallToUser(recalled.getReceiverId(), dto);
        } else if ("group".equals(recalled.getChatType())) {
            webSocketService.sendChatMessageRecallToGroup(recalled.getGroupId(), dto, recalled.getSenderId());
        }
        return recalled;
    }

    @Override
    @Transactional
    public boolean deleteMessage(Long id) {
        // 检查消息是否存在
        ChatMessage existingMessage = chatMessageMapper.findById(id);
        if (existingMessage == null) {
            throw new RuntimeException("消息不存在");
        }
        
        // 删除消息
        chatMessageMapper.deleteById(id);
        
        return true;
    }
    
    @Override
    @Transactional
    public boolean deleteMessage(Integer messageId, Integer userId) {
        // 将 Integer 类型的 messageId 转换为 Long 类型
        Long messageIdLong = messageId.longValue();
        
        // 检查消息是否存在
        ChatMessage existingMessage = chatMessageMapper.findById(messageIdLong);
        if (existingMessage == null) {
            throw new RuntimeException("消息不存在");
        }
        
        // 验证消息所有权（只有发送者可以删除消息）
        if (!existingMessage.getSenderId().equals(userId)) {
            throw new RuntimeException("无权删除此消息");
        }
        
        // 删除消息
        chatMessageMapper.deleteById(messageIdLong);

        // 仅向删除者本人推送消息删除通知，便于前端从列表移除
        Map<String, Object> payload = new HashMap<>();
        payload.put("id", messageIdLong);
        payload.put("chatType", existingMessage.getChatType());
        payload.put("groupId", existingMessage.getGroupId());
        payload.put("receiverId", existingMessage.getReceiverId());
        webSocketService.sendChatMessageDeleteToUser(userId, payload);
        
        return true;
    }

    @Override
    public List<ChatMessage> getSingleChatHistory(Integer userId, Integer contactId, Integer limit, Integer offset) {
        // 函数级注释：
        // - 功能：获取与指定联系人的单聊历史。
        // - 特性开关：当 historyEnabled=false 时，后端统一短路，直接返回空集合，避免访问数据库并对外表现为“无历史”。
        if (!historyEnabled) {
            return Collections.emptyList();
        }
        return chatMessageMapper.findSingleChatHistory(userId, contactId, limit, offset);
    }

    @Override
    public List<ChatMessageDTO> getGroupChatHistory(Integer userId, Integer groupId, Integer limit, Integer offset) {
        // 函数级注释：
        // - 功能：获取指定用户在某群的消息视图（仅该用户的消息副本），并将实体映射为包含发送者资料的 DTO，便于前端展示头像与昵称；
        // - 权限：仅群成员可查看；否则抛出异常。
        // - 特性开关：当 historyEnabled=false 时，统一短路返回空集合。
        if (!historyEnabled) {
            return Collections.emptyList();
        }
        // 成员校验：若不是成员，也允许查看自己的历史副本（被移除后保留查看历史的能力）
        // org.itheima.pojo.ChatGroupMember member = chatGroupMemberMapper.findByGroupIdAndUserId(groupId, userId);
        // if (member == null) {
        //     throw new RuntimeException("你不是该群成员，无法查看群聊历史");
        // }
        // 查询当前用户在该群的消息副本
        List<ChatMessage> records = chatMessageMapper.findGroupChatHistoryByUser(groupId, userId, limit, offset);
        if (records == null || records.isEmpty()) return Collections.emptyList();
        // 映射为 DTO，补齐 senderName/senderAvatar
        List<ChatMessageDTO> dtos = new java.util.ArrayList<>(records.size());
        for (ChatMessage m : records) {
            ChatMessageDTO dto = buildChatMessageDTO(m);
            // 附加 group 相关字段，便于前端展示
            dto.setGroupId(m.getGroupId());
            ChatGroup g = chatGroupMapper.findById(m.getGroupId());
            if (g != null) {
                dto.setGroupName(g.getGroupName());
                dto.setReceiverAvatar(g.getGroupAvatar());
            }
            // isSelf 字段：便于前端快速区分我方消息
            dto.setIsSelf(m.getSenderId() != null && m.getSenderId().equals(userId));
            // receiverName/receiverAvatar 在群聊场景下可选（前端通常展示群信息在顶部）
            dtos.add(dto);
        }
        return dtos;
    }

    @Override
    @Transactional
    public boolean markMessageAsRead(Long messageId, Integer userId) {
        // 检查消息是否存在
        ChatMessage message = chatMessageMapper.findById(messageId);
        if (message == null) {
            throw new RuntimeException("消息不存在");
        }
        
        // 检查用户是否是消息的接收者
        if (!message.getReceiverId().equals(userId)) {
            throw new RuntimeException("无权限标记该消息为已读");
        }
        
        // 标记消息为已读
        message.setIsRead(true);
        message.setReadTime(LocalDateTime.now());
        message.setUpdateTime(LocalDateTime.now());
        
        chatMessageMapper.update(message);
        
        // 单聊：向原发送者推送已读回执
        if ("single".equalsIgnoreCase(message.getChatType())) {
            try {
                java.util.Map<String, Object> ack = new java.util.HashMap<>();
                ack.put("messageId", messageId);
                ack.put("chatType", message.getChatType());
                ack.put("senderId", message.getSenderId());
                ack.put("receiverId", message.getReceiverId());
                ack.put("readTime", message.getReadTime());
                webSocketService.sendReadAckToUser(message.getSenderId(), ack);
            } catch (Exception ignored) {}
        }
        
        return true;
    }

    @Override
    @Transactional
    public int markMessagesAsRead(Integer senderId, Integer receiverId, String chatType) {
        LocalDateTime now = LocalDateTime.now();
        if ("single".equals(chatType)) {
            int c = chatMessageMapper.markAllAsRead(senderId, receiverId, now);
            // 推送批量已读回执（仅单聊，对原发送者）
            try {
                java.util.Map<String, Object> ack = new java.util.HashMap<>();
                ack.put("event", "batch_read");
                ack.put("chatType", "single");
                ack.put("senderId", senderId);
                ack.put("receiverId", receiverId);
                ack.put("readTime", now);
                webSocketService.sendReadAckToUser(senderId, ack);
            } catch (Exception ignored) {}
            return c;
        } else if ("group".equals(chatType)) {
            return chatMessageMapper.markGroupMessagesAsRead(receiverId, senderId, now);
        }
        return 0;
    }

    @Override
    public Long countUnreadMessages(Integer receiverId) {
        return chatMessageMapper.countUnreadMessages(receiverId);
    }

    /**
     * 获取用户的未读消息详细列表（包含发送者信息）
     * 函数级注释：
     * - 获取用户所有未读消息的详细信息
     * - 包含发送者的用户名、昵称、头像等信息
     * - 按创建时间倒序排列，最新消息在前
     * - 限制返回数量以避免数据过多
     */
    @Override
    public List<Map<String, Object>> findUnreadMessagesWithDetails(Integer receiverId, Integer limit) {
        return chatMessageMapper.findUnreadMessagesWithDetails(receiverId, limit);
    }

    @Override
    public List<Map<String, Object>> getRecentChats(Integer userId) {
        return chatMessageMapper.getRecentChatsDefault(userId);
    }
    
    @Override
    public List<Map<String, Object>> getRecentChats(Integer userId, Integer limit) {
        return chatMessageMapper.getRecentChats(userId, limit);
    }

    @Override
    public Map<String, Object> getMessageStatsByTimeRange(LocalDateTime startTime, LocalDateTime endTime) {
        return chatMessageMapper.getMessageStatsByTimeRange(startTime, endTime);
    }

    @Override
    public Map<String, Integer> getMessageTypeStatsByTimeRange(LocalDateTime startTime, LocalDateTime endTime) {
        List<Map<String, Object>> stats = chatMessageMapper.getMessageTypeStatsByTimeRange(startTime, endTime);
        
        // 转换为Map<String, Integer>格式
        Map<String, Integer> result = new HashMap<>();
        for (Map<String, Object> entry : stats) {
            String messageType = (String) entry.get("messageType");
            Long count = (Long) entry.get("count");
            result.put(messageType, count.intValue());
        }
        return result;
    }

    /**
     * 构建用于前端展示的消息DTO
     * 函数级注释：
     * - 将数据库实体映射为前端需要的字段命名与结构。
     */
     private ChatMessageDTO buildChatMessageDTO(ChatMessage chatMessage) {
         ChatMessageDTO dto = new ChatMessageDTO();
         dto.setId(chatMessage.getId());
         dto.setSenderId(chatMessage.getSenderId());
         dto.setReceiverId(chatMessage.getReceiverId());
         dto.setMessageType(chatMessage.getMessageType());
         dto.setContent(chatMessage.getContent());
         dto.setChatType(chatMessage.getChatType());
         dto.setGroupId(chatMessage.getGroupId());
        // 新增：传出originMessageId，便于前端在群聊中按关联ID定位消息副本
        dto.setOriginMessageId(chatMessage.getOriginMessageId());
        dto.setIsRead(chatMessage.getIsRead());
         dto.setIsRecalled(chatMessage.getIsRecalled());
         dto.setIsEdited(chatMessage.getIsEdited());
         dto.setContentVersion(chatMessage.getContentVersion());
         dto.setCreateTime(chatMessage.getCreateTime());
         // 移除：ChatMessageDTO 当前未包含 updateTime 字段
         // dto.setUpdateTime(chatMessage.getUpdateTime());
         // 可扩展：附带发送者显示名/头像等
         User sender = userMapper.findById(chatMessage.getSenderId());
         dto.setSenderName(displayName(sender));
         dto.setSenderAvatar(sender != null ? sender.getUserPic() : null);
         return dto;
     }

    // 工具：显示昵称优先，其次用户名
    private String displayName(User user) {
        if (user == null) return null;
        if (user.getNickname() != null && !user.getNickname().trim().isEmpty()) {
            return user.getNickname();
        }
        return user.getUsername();
    }

    // 已移除：消息搜索（前端未接入）

    @Override
    public List<ChatMessage> getSingleChatHistoryByCursor(Integer userId, Integer contactId, LocalDateTime beforeTime, Long beforeId, Integer limit) {
        return chatMessageMapper.findSingleChatHistoryByCursor(userId, contactId, beforeTime, beforeId, limit);
    }
}