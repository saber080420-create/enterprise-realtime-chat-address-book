package org.itheima.pojo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 聊天消息实体类
 *
 * 函数级注释：
 * - 本实体与数据库表 chat_message 对应，新增字段以支持消息撤回与编辑：
 *   1) isRecalled：标识消息是否被撤回；
 *   2) recallTime：撤回发生的时间；
 *   3) isEdited：标识消息是否被编辑；
 *   4) contentVersion：消息内容的版本号，从1开始，编辑时自增，用于前端幂等与展示“已编辑”。
 *   5) originMessageId：同一条群聊消息在“写时扇出”场景下的关联ID，用于批量撤回/编辑。
 */
@Data
public class ChatMessage {
    private Long id; // 消息ID
    
    private Integer senderId; // 发送者ID
    
    private Integer receiverId; // 接收者ID（用户ID或群组ID）
    
    private String messageType; // 消息类型：text-文本消息，image-图片消息，file-文件消息
    
    private String content; // 消息内容
    
    private String chatType; // 聊天类型：single-单聊，group-群聊
    
    private Integer groupId; // 群组ID（群聊时使用）
    
    private Boolean isRead; // 是否已读
    
    private LocalDateTime readTime; // 已读时间

    // 新增：撤回/编辑相关字段
    private Boolean isRecalled; // 是否已撤回
    private LocalDateTime recallTime; // 撤回时间
    private Boolean isEdited; // 是否已编辑
    private Integer contentVersion; // 内容版本号（初始1，编辑自增）

    // 新增：同一条消息的各用户副本的关联ID（群聊写时扇出）
    private Long originMessageId; // 原始消息ID（通常取发送者副本ID）
    
    private LocalDateTime createTime; // 创建时间
    
    private LocalDateTime updateTime; // 更新时间
}