package org.itheima.pojo;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 聊天群组实体类
 */
@Data
public class ChatGroup {
    private Integer id; // 群组ID
    
    @NotEmpty(message = "群组名称不能为空")
    private String groupName; // 群组名称
    
    private String groupAvatar; // 群组头像
    
    private String description; // 群组描述
    
    // 去掉 @NotNull 校验，创建时由服务端在控制器或服务层根据登录用户填充 creatorId，避免 @Valid 触发 400
    private Integer creatorId; // 创建者ID
    
    private LocalDateTime createTime; // 创建时间
    
    private LocalDateTime updateTime; // 更新时间
    
    private Integer memberCount; // 群组成员数量
}