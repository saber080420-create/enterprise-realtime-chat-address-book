package org.itheima.listener;

import org.itheima.events.UserConnectedEvent;
import org.itheima.service.ChatMessageService;
import org.itheima.service.WebSocketService;
// import org.itheima.pojo.dto.WebSocketMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
// import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 离线消息监听器
 * 函数级注释：
 * - 监听用户连接事件，查询未读/离线消息并通过WebSocket推送通知
 * - 通过事件驱动方式解耦 WebSocketHandler 和 ChatMessageService，消除循环依赖
 */
// 已移除：离线消息监听器（前端未接入离线同步提示，避免维护冗余）
// @Component
public class OfflineMessageListener {

    private static final Logger logger = LoggerFactory.getLogger(OfflineMessageListener.class);

    private final ChatMessageService chatMessageService;
    private final WebSocketService webSocketService;

    public OfflineMessageListener(ChatMessageService chatMessageService, WebSocketService webSocketService) {
        this.chatMessageService = chatMessageService;
        this.webSocketService = webSocketService;
    }

    /**
     * 处理用户连接事件
     * - 查询用户的未读消息数量与部分消息详情
     * - 通过 WebSocket 向该用户推送同步通知
     */
    @EventListener
    public void onUserConnected(UserConnectedEvent event) {
        Integer userId = event.getUserId();
        try {
            Long unreadCount = chatMessageService.countUnreadMessages(userId);
            if (unreadCount != null && unreadCount > 0) {
                List<Map<String, Object>> unreadMessages = chatMessageService.findUnreadMessagesWithDetails(userId, 50);
                Map<String, Object> syncData = new HashMap<>();
                syncData.put("unreadCount", unreadCount);
                syncData.put("message", "您有 " + unreadCount + " 条未读消息，请刷新会话列表");
                syncData.put("unreadMessages", unreadMessages);
                // 发送离线消息同步通知
                webSocketService.sendNotificationToUser(userId, syncData);
            }
        } catch (Exception e) {
            logger.error("处理用户连接后的离线消息推送时发生异常, userId={}", userId, e);
            // 失败不影响主流程
            Map<String, Object> errorData = new HashMap<>();
            errorData.put("message", "离线消息同步失败: " + e.getMessage());
            webSocketService.sendNotificationToUser(userId, errorData);
        }
    }
}