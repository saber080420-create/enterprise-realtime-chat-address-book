package org.itheima.pojo.dto;

import lombok.Data;

/**
 * WebSocket消息传输对象
 * 用于前后端WebSocket通信
 */
@Data
public class WebSocketMessage<T> {
    /**
     * 消息类型
     * chat_message: 聊天消息
     * user_status: 用户状态变更
     * notification: 系统通知
     * announcement: 公告通知
     * announcement_revoke: 公告撤回通知
     * chat_message_edit: 聊天消息编辑
     * chat_message_recall: 聊天消息撤回
     * chat_message_delete: 聊天消息删除
     * typing: 正在输入
     * read_ack: 已读回执
     */
    private String type;
    
    /**
     * 消息内容
     */
    private T data;
    
    /**
     * 时间戳
     */
    private Long timestamp;
    
    /**
     * 创建聊天消息
     */
    public static <T> WebSocketMessage<T> chatMessage(T data) {
        return createMessage("chat_message", data);
    }

    /**
     * 创建聊天消息编辑通知
     * 函数级注释：
     * - 用于通知其他客户端某条消息被编辑。
     */
    public static <T> WebSocketMessage<T> chatMessageEdit(T data) {
        return createMessage("chat_message_edit", data);
    }

    /**
     * 创建聊天消息撤回通知
     * 函数级注释：
     * - 用于通知其他客户端某条消息被撤回。
     */
    public static <T> WebSocketMessage<T> chatMessageRecall(T data) {
        return createMessage("chat_message_recall", data);
    }

    /**
     * 创建聊天消息删除通知
     * 函数级注释：
     * - 用于通知其他客户端某条消息被删除（仅对删除者可见）。
     */
    public static <T> WebSocketMessage<T> chatMessageDelete(T data) {
        return createMessage("chat_message_delete", data);
    }
    
    /**
     * 创建用户状态变更消息
     */
    public static <T> WebSocketMessage<T> userStatus(T data) {
        return createMessage("user_status", data);
    }
    
    /**
     * 创建系统通知消息
     */
    public static <T> WebSocketMessage<T> notification(T data) {
        return createMessage("notification", data);
    }
    
    /**
     * 创建公告通知消息
     */
    public static <T> WebSocketMessage<T> announcement(T data) {
        return createMessage("announcement", data);
    }
    
    /**
     * 创建公告撤回通知消息
     */
    public static <T> WebSocketMessage<T> announcementRevoke(T data) {
        return createMessage("announcement_revoke", data);
    }
    
    /**
     * 创建正在输入消息
     */
    public static <T> WebSocketMessage<T> typing(T data) {
        return createMessage("typing", data);
    }
    
    /**
     * 创建已读回执消息
     */
    public static <T> WebSocketMessage<T> readAck(T data) {
        return createMessage("read_ack", data);
    }
    
    /**
     * 创建消息的通用方法
     */
    public static <T> WebSocketMessage<T> createMessage(String type, T data) {
        WebSocketMessage<T> message = new WebSocketMessage<>();
        message.setType(type);
        message.setData(data);
        message.setTimestamp(System.currentTimeMillis());
        return message;
    }
}