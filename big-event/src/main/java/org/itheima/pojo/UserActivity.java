package org.itheima.pojo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户活跃度实体类
 */
@Data
public class UserActivity {
    private Integer id; // 主键ID
    
    private Integer userId; // 用户ID
    
    private LocalDateTime loginTime; // 登录时间
    
    private LocalDateTime logoutTime; // 登出时间
    
    private String ipAddress; // IP地址
    
    private String deviceInfo; // 设备信息
    
    private Integer messageCount; // 消息数量
    
    private Boolean online; // 是否在线
    
    private LocalDateTime lastActiveTime; // 最后活跃时间
    
    private LocalDateTime createTime; // 创建时间
    
    private LocalDateTime updateTime; // 更新时间
}