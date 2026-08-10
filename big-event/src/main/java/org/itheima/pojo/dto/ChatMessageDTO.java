package org.itheima.pojo.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 聊天消息数据传输对象
 * 用于消息的传输和展示，包含发送者和接收者的额外信息
 * 
 * 函数级注释：
 * - 新增撤回/编辑字段，用于WebSocket推送告知前端消息状态变更。
 */
@Data
public class ChatMessageDTO {
    private Long id; // 消息ID
    
    private Integer senderId; // 发送者ID
    
    private String senderName; // 发送者姓名
    
    private String senderAvatar; // 发送者头像
    
    private Integer receiverId; // 接收者ID（用户ID或群组ID）
    
    private String receiverName; // 接收者姓名
    
    private String receiverAvatar; // 接收者头像
    
    private String messageType; // 消息类型：text-文本消息，image-图片消息，file-文件消息
    
    private String content; // 消息内容
    
    private String chatType; // 聊天类型：single-单聊，group-群聊
    
    private Integer groupId; // 群组ID（群聊时使用）
    
    private String groupName; // 群组名称（群聊时使用）
    
    // 新增：群聊关联字段
    private Long originMessageId; // 同一条群聊消息的统一关联ID（通常为发送者副本ID），用于前端按组定位批量撤回

    private Boolean isRead; // 是否已读
    
    private LocalDateTime readTime; // 已读时间

    // 新增：撤回/编辑相关字段
    private Boolean isRecalled; // 是否已撤回
    private LocalDateTime recallTime; // 撤回时间
    private Boolean isEdited; // 是否已编辑
    private Integer contentVersion; // 内容版本号（用于前端展示"已编辑"）
    
    private LocalDateTime createTime; // 创建时间
    
    private Boolean isSelf; // 是否为自己发送的消息（前端展示用）
}