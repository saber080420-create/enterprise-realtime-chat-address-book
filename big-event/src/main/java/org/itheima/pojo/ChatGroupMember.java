package org.itheima.pojo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 聊天群组成员实体类
 */
@Data
public class ChatGroupMember {
    private Integer id; // 主键ID
    
    private Integer groupId; // 群组ID
    
    private Integer userId; // 用户ID
    
    private String role; // 角色：owner-群主，admin-管理员，member-普通成员
    
    private String alias; // 群内昵称
    
    private Boolean mute; // 是否禁言
    
    private LocalDateTime joinTime; // 加入时间
    
    private LocalDateTime createTime; // 创建时间
    
    private LocalDateTime updateTime; // 更新时间
}