package org.itheima.events;

/**
 * 用户WebSocket已连接事件
 * 函数级注释：
 * - 由 WebSocketHandler 在连接建立后发布
 * - 监听器可基于该事件执行离线消息推送等后续动作
 */
public class UserConnectedEvent {
    private final Integer userId;

    public UserConnectedEvent(Integer userId) {
        this.userId = userId;
    }

    public Integer getUserId() {
        return userId;
    }
}