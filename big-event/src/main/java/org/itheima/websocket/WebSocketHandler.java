package org.itheima.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.itheima.events.UserConnectedEvent;
import org.itheima.pojo.dto.WebSocketMessage;
import org.itheima.utils.JwtUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket事件处理器
 * 函数级注释：
 * - 负责追踪在线用户会话，并接收/发送文本消息
 * - 连接建立后发布 UserConnectedEvent，由监听器完成离线消息推送，从而移除对 ChatMessageService 的直接依赖
 * - 兼容旧有调用：保留 sendMessageToUser/broadcastMessage 方法供 WebSocketService 使用
 */
@Component
public class WebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(WebSocketHandler.class);

    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;

    // 在线用户: userId -> WebSocketSession
    private static final Map<Integer, WebSocketSession> onlineSessions = new ConcurrentHashMap<>();

    public WebSocketHandler(ObjectMapper objectMapper, ApplicationEventPublisher eventPublisher) {
        this.objectMapper = objectMapper;
        this.eventPublisher = eventPublisher;
    }

    /**
     * 建立连接：解析用户ID，记录会话，并发布 UserConnectedEvent
     */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Integer userId = parseUserId(session);
        if (userId != null) {
            // 若已存在旧会话，先通知旧会话被强制下线，并关闭旧连接
            WebSocketSession old = onlineSessions.get(userId);
            if (old != null && old.isOpen() && old != session) {
                try {
                    String payload = objectMapper.writeValueAsString(
                        org.itheima.pojo.dto.WebSocketMessage.notification(
                            java.util.Map.of(
                                "event", "force_logout",
                                "reason", "login_from_another_device",
                                "time", System.currentTimeMillis()
                            )
                        )
                    );
                    old.sendMessage(new TextMessage(payload));
                } catch (Exception e) {
                    log.warn("通知旧会话下线失败 userId={}", userId, e);
                }
                try { old.close(CloseStatus.NORMAL); } catch (Exception ignore) {}
            }

            onlineSessions.put(userId, session);
            log.info("WebSocket已连接, userId={}", userId);
            // 发布用户已连接事件，交由监听器处理离线消息
            eventPublisher.publishEvent(new UserConnectedEvent(userId));
        } else {
            log.warn("无法从会话中解析用户ID, 将关闭连接");
            session.close(CloseStatus.BAD_DATA);
        }
    }

    /**
     * 接收客户端文本消息：目前仅打印日志，可按需扩展
     */
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        log.debug("收到客户端消息: {}", message.getPayload());
    }

    /**
     * 断开连接时移除在线会话
     */
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        Integer userId = parseUserId(session);
        if (userId != null) {
            onlineSessions.remove(userId);
            log.info("WebSocket已断开, userId={}, status={}", userId, status);
        }
    }

    /**
     * 工具：解析会话中的用户ID
     * 优先从 attributes 读取；其次从 URL 查询参数中的 token 解析
     */
    private Integer parseUserId(WebSocketSession session) {
        try {
            // 1) attributes 中直接携带的 userId
            Object idObj = session.getAttributes() != null ? session.getAttributes().get("id") : null;
            if (idObj == null) {
                idObj = session.getAttributes() != null ? session.getAttributes().get("userId") : null;
            }
            if (idObj != null) {
                return Integer.valueOf(String.valueOf(idObj));
            }
            // 2) 从 token 中解析
            String token = extractToken(session);
            if (token != null && !token.isEmpty()) {
                Map<String, Object> claims = JwtUtil.parseToken(token);
                Object claimId = claims.get("id");
                if (claimId != null) {
                    return Integer.valueOf(String.valueOf(claimId));
                }
            }
        } catch (Exception e) {
            log.error("解析用户ID失败", e);
        }
        return null;
    }

    /**
     * 工具：从WebSocket会话URL中提取token参数
     */
    private String extractToken(WebSocketSession session) {
        try {
            if (session.getUri() == null) return null;
            String query = session.getUri().getQuery();
            if (query == null) return null;
            for (String kv : query.split("&")) {
                String[] arr = kv.split("=");
                if (arr.length == 2 && "token".equals(arr[0])) {
                    return arr[1];
                }
            }
        } catch (Exception e) {
            log.warn("从会话中提取token失败", e);
        }
        return null;
    }

    /**
     * 发送文本：向指定用户发送任意payload（供 WebSocketService 使用）
     */
    public void sendToUser(Integer userId, Object payload) {
        WebSocketSession session = onlineSessions.get(userId);
        if (session == null || !session.isOpen()) {
            log.debug("用户不在线或会话已关闭, userId={}", userId);
            return;
        }
        try {
            String text = (payload instanceof String) ? (String) payload : objectMapper.writeValueAsString(payload);
            session.sendMessage(new TextMessage(text));
        } catch (Exception e) {
            log.error("发送消息给用户失败, userId={}", userId, e);
        }
    }

    /**
     * 兼容方法：向指定用户发送WebSocketMessage（保持与现有 WebSocketService 的调用一致）
     * 返回是否发送成功
     */
    public boolean sendMessageToUser(Integer userId, WebSocketMessage<?> message) {
        WebSocketSession session = onlineSessions.get(userId);
        if (session != null && session.isOpen()) {
            return sendMessage(session, message);
        }
        return false;
    }

    /**
     * 兼容方法：向所有在线用户广播WebSocketMessage（保持与现有 WebSocketService 的调用一致）
     */
    public void broadcastMessage(WebSocketMessage<?> message) {
        onlineSessions.forEach((uid, session) -> {
            if (session != null && session.isOpen()) {
                sendMessage(session, message);
            }
        });
    }

    /**
     * 工具：向具体会话发送WebSocketMessage
     */
    private boolean sendMessage(WebSocketSession session, WebSocketMessage<?> message) {
        try {
            String payload = objectMapper.writeValueAsString(message);
            session.sendMessage(new TextMessage(payload));
            return true;
        } catch (IOException e) {
            log.error("发送WebSocket消息失败", e);
            return false;
        }
    }

    /**
     * 获取在线用户数量
     * 函数级注释：
     * - 返回当前维护的在线会话数量
     */
    public int getOnlineUserCount() {
        return onlineSessions.size();
    }

    /**
     * 判断指定用户是否在线
     * 函数级注释：
     * - 通过 userId 查找在线会话并判断是否仍处于打开状态
     */
    public boolean isUserOnline(Integer userId) {
        WebSocketSession session = onlineSessions.get(userId);
        return session != null && session.isOpen();
    }
}