package org.itheima.pojo.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 聊天群组数据传输对象
 * 用于群组信息的传输和展示，包含成员数量等额外信息
 */
@Data
public class ChatGroupDTO {
    private Integer id; // 群组ID
    
    private String groupName; // 群组名称
    
    private String groupAvatar; // 群组头像
    
    private String description; // 群组描述
    
    private Integer creatorId; // 创建者ID
    
    private String creatorName; // 创建者姓名
    
    private Integer memberCount; // 成员数量
    
    private List<ChatGroupMemberDTO> members; // 成员列表
    
    private String userRole; // 当前用户在群组中的角色
    
    private LocalDateTime createTime; // 创建时间
    
    private LocalDateTime updateTime; // 更新时间
    
    private Long unreadCount; // 未读消息数量
    
    private ChatMessageDTO lastMessage; // 最后一条消息
}