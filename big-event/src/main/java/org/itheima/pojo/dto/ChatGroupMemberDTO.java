package org.itheima.pojo.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 聊天群组成员数据传输对象
 * 用于群组成员信息的传输和展示，包含用户的额外信息
 */
@Data
public class ChatGroupMemberDTO {
    private Integer id; // 主键ID
    
    private Integer groupId; // 群组ID
    
    private Integer userId; // 用户ID
    
    private String username; // 用户名
    
    private String nickname; // 用户昵称
    
    private String userPic; // 用户头像
    
    private String departmentName; // 部门名称
    
    private String position; // 职位
    
    private String role; // 角色：owner-群主，admin-管理员，member-普通成员
    
    private String alias; // 群内昵称
    
    private Boolean mute; // 是否禁言
    
    private Boolean online; // 是否在线
    
    private LocalDateTime joinTime; // 加入时间
    
    private LocalDateTime lastActiveTime; // 最后活跃时间
}