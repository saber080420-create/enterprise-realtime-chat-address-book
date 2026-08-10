package org.itheima.service;

import org.itheima.pojo.dto.WebSocketMessage;
import org.itheima.websocket.WebSocketHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
// 新增导入
import java.util.List;
import java.util.stream.Collectors;
import org.itheima.mapper.WebSocketMapper;

/**
 * WebSocket服务
 * 提供发送WebSocket消息的方法
 */
@Service
public class WebSocketService {

    private static final Logger logger = LoggerFactory.getLogger(WebSocketService.class);

    @Autowired
    private WebSocketHandler webSocketHandler;
    // 注入WebSocketMapper用于查询在线成员
    @Autowired
    private WebSocketMapper webSocketMapper;

    /**
     * 向指定用户发送公告通知
     * 
     * @param userId 用户ID
     * @param announcementData 公告数据
     * @return 是否发送成功
     */
    public boolean sendAnnouncementToUser(Integer userId, Object announcementData) {
        logger.info("向用户 {} 发送公告通知", userId);
        return webSocketHandler.sendMessageToUser(userId, WebSocketMessage.announcement(announcementData));
    }

    /**
     * 广播公告通知给所有在线用户
     * 
     * @param announcementData 公告数据
     */
    public void broadcastAnnouncement(Object announcementData) {
        logger.info("广播公告通知给所有在线用户");
        webSocketHandler.broadcastMessage(WebSocketMessage.announcement(announcementData));
    }

    /**
     * 广播公告撤回通知给所有在线用户
     * 
     * @param announcementData 公告数据
     */
    public void broadcastAnnouncementRevoke(Object announcementData) {
        logger.info("广播公告撤回通知给所有在线用户");
        webSocketHandler.broadcastMessage(WebSocketMessage.announcementRevoke(announcementData));
    }

    /**
     * 向指定用户发送系统通知
     * 
     * @param userId 用户ID
     * @param notificationData 通知数据
     * @return 是否发送成功
     */
    public boolean sendNotificationToUser(Integer userId, Object notificationData) {
        logger.info("向用户 {} 发送系统通知", userId);
        return webSocketHandler.sendMessageToUser(userId, WebSocketMessage.notification(notificationData));
    }

    /**
     * 广播系统通知给所有在线用户
     * 
     * @param notificationData 通知数据
     */
    public void broadcastNotification(Object notificationData) {
        logger.info("广播系统通知给所有在线用户");
        webSocketHandler.broadcastMessage(WebSocketMessage.notification(notificationData));
    }

    // 已移除：用户状态变更通知API（前端未接入online指示灯等）

    /**
     * 向指定用户发送消息已读回执
     * @param userId 接收回执的用户（原消息发送者）
     * @param data { chatType:'single', senderId, receiverId, readTime }
     */
    public boolean sendReadAckToUser(Integer userId, Object data) {
        logger.info("向用户 {} 发送已读回执", userId);
        return webSocketHandler.sendMessageToUser(userId, WebSocketMessage.readAck(data));
    }

    /**
     * 群事件：群解散
     */
    public void broadcastGroupDisband(Integer groupId, Object data) {
        logger.info("广播群解散事件, groupId={}", groupId);
        webSocketHandler.broadcastMessage(WebSocketMessage.notification(data));
    }

    /**
     * 群事件：成员被移出
     */
    public void notifyGroupMemberRemoved(Integer targetUserId, Object data) {
        logger.info("通知被移出用户, userId={}", targetUserId);
        webSocketHandler.sendMessageToUser(targetUserId, WebSocketMessage.notification(data));
    }

    /**
     * 群事件：成员退出
     */
    public void broadcastGroupMemberLeft(Object data) {
        logger.info("广播群成员退出事件");
        webSocketHandler.broadcastMessage(WebSocketMessage.notification(data));
    }

    /**
     * 群事件：成员被移出（广播给群内其他在线成员，排除被移出者）
     */
    public void broadcastGroupMemberRemoved(Integer groupId, Object data, Integer excludeUserId) {
        logger.info("广播群成员被移出事件, groupId={}, excludeUserId={}", groupId, excludeUserId);
        java.util.List<Integer> onlineUsers = getGroupOnlineUserIds(groupId);
        if (excludeUserId != null) {
            onlineUsers.removeIf(id -> id.equals(excludeUserId));
        }
        for (Integer uid : onlineUsers) {
            webSocketHandler.sendMessageToUser(uid, WebSocketMessage.notification(data));
        }
    }

    /**
     * 获取在线用户数量
     * 
     * @return 在线用户数量
     */
    public int getOnlineUserCount() {
        return webSocketHandler.getOnlineUserCount();
    }

    /**
     * 检查用户是否在线
     * 
     * @param userId 用户ID
     * @return 是否在线
     */
    public boolean isUserOnline(Integer userId) {
        return webSocketHandler.isUserOnline(userId);
    }

    /**
     * 向指定用户发送聊天消息
     * 
     * @param userId 接收者用户ID
     * @param chatMessageData 聊天消息数据
     * @return 是否发送成功
     */
    public boolean sendChatMessageToUser(Integer userId, Object chatMessageData) {
        logger.info("向用户 {} 发送聊天消息", userId);
        return webSocketHandler.sendMessageToUser(userId, WebSocketMessage.chatMessage(chatMessageData));
    }

    /**
     * 向群组中所有在线成员发送聊天消息
     * 
     * @param groupId 群组ID
     * @param chatMessageData 聊天消息数据
     * @param excludeUserId 排除的用户ID（通常是发送者，避免收到自己发送的消息）
     */
    public void sendChatMessageToGroup(Integer groupId, Object chatMessageData, Integer excludeUserId) {
        logger.info("向群组 {} 发送聊天消息，排除用户 {}", groupId, excludeUserId);
        // 获取群组所有在线成员
        List<Integer> onlineUsers = getGroupOnlineUserIds(groupId);
        // 排除发送者
        if (excludeUserId != null) {
            onlineUsers.removeIf(id -> id.equals(excludeUserId));
        }
        // 向所有在线成员发送消息
        for (Integer userId : onlineUsers) {
            sendChatMessageToUser(userId, chatMessageData);
        }
    }

    /**
     * 向指定用户发送消息编辑通知
     * 函数级注释：
     * - 由后端在消息被编辑后调用，通知对端实时更新内容。
     */
    public boolean sendChatMessageEditToUser(Integer userId, Object data) {
        logger.info("向用户 {} 发送消息编辑通知", userId);
        return webSocketHandler.sendMessageToUser(userId, WebSocketMessage.chatMessageEdit(data));
    }

    /**
     * 向群组在线成员发送消息编辑通知
     */
    public void sendChatMessageEditToGroup(Integer groupId, Object data, Integer excludeUserId) {
        logger.info("向群组 {} 发送消息编辑通知，排除用户 {}", groupId, excludeUserId);
        List<Integer> onlineUsers = getGroupOnlineUserIds(groupId);
        if (excludeUserId != null) {
            onlineUsers.removeIf(id -> id.equals(excludeUserId));
        }
        for (Integer userId : onlineUsers) {
            webSocketHandler.sendMessageToUser(userId, WebSocketMessage.chatMessageEdit(data));
        }
    }

    /**
     * 向指定用户发送消息撤回通知
     */
    public boolean sendChatMessageRecallToUser(Integer userId, Object data) {
        logger.info("向用户 {} 发送消息撤回通知", userId);
        return webSocketHandler.sendMessageToUser(userId, WebSocketMessage.chatMessageRecall(data));
    }

    /**
     * 向群组在线成员发送消息撤回通知
     */
    public void sendChatMessageRecallToGroup(Integer groupId, Object data, Integer excludeUserId) {
        logger.info("向群组 {} 发送消息撤回通知，排除用户 {}", groupId, excludeUserId);
        List<Integer> onlineUsers = getGroupOnlineUserIds(groupId);
        if (excludeUserId != null) {
            onlineUsers.removeIf(id -> id.equals(excludeUserId));
        }
        for (Integer userId : onlineUsers) {
            webSocketHandler.sendMessageToUser(userId, WebSocketMessage.chatMessageRecall(data));
        }
    }

    /**
     * 向删除者本人发送消息删除通知（仅本人会收到）
     */
    public boolean sendChatMessageDeleteToUser(Integer userId, Object data) {
        logger.info("向用户 {} 发送消息删除通知", userId);
        return webSocketHandler.sendMessageToUser(userId, WebSocketMessage.chatMessageDelete(data));
    }

    /**
     * 获取群组中所有在线用户ID列表
     * 
     * @param groupId 群组ID
     * @return 在线用户ID列表
     */
    private List<Integer> getGroupOnlineUserIds(Integer groupId) {
        return webSocketMapper.getGroupOnlineUsers(groupId)
                .stream()
                .map(u -> u.getUserId())
                .collect(Collectors.toList());
    }
}