package org.itheima.pojo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 公告阅读状态实体类
 */
@Data
public class AnnouncementReadStatus {
    private Integer id; // 主键ID
    
    private Integer announcementId; // 公告ID
    
    private Integer userId; // 用户ID
    
    private Boolean isRead; // 是否已读
    
    private LocalDateTime readTime; // 阅读时间
    
    private LocalDateTime createTime; // 创建时间
    
    private LocalDateTime updateTime; // 更新时间
}