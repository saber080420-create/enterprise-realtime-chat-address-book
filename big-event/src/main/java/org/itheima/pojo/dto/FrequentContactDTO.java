package org.itheima.pojo.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 常用联系人数据传输对象
 * 用于常用联系人信息的传输和展示，包含联系人的额外信息
 */
@Data
public class FrequentContactDTO {
    private Integer id; // 主键ID
    
    private Integer userId; // 用户ID
    
    private Integer contactId; // 联系人ID
    
    private String contactName; // 联系人姓名
    
    private String contactAvatar; // 联系人头像
    
    private String departmentName; // 联系人部门名称
    
    private String position; // 联系人职位
    
    private Boolean isFavorite; // 是否收藏
    
    private Boolean online; // 是否在线
    
    private Long unreadCount; // 未读消息数量
    
    private String lastMessage; // 最后一条消息
    
    private LocalDateTime createTime; // 创建时间
    
    private LocalDateTime updateTime; // 更新时间
}