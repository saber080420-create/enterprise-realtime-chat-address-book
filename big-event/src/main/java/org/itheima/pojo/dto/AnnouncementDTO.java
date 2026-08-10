package org.itheima.pojo.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 公告数据传输对象
 * 用于公告信息的传输和展示，包含发布者和阅读状态等额外信息
 */
@Data
public class AnnouncementDTO {
    private Integer id; // 公告ID
    
    private String title; // 公告标题
    
    private String content; // 公告内容
    
    private Integer publisherId; // 发布者ID
    
    private String publisherName; // 发布者姓名
    
    private String publisherAvatar; // 发布者头像
    
    private String type; // 公告类型：company-公司公告，department-部门公告
    
    private Integer departmentId; // 部门ID（部门公告时使用）
    
    private String departmentName; // 部门名称（部门公告时使用）
    
    private String status; // 状态：draft-草稿，published-已发布，archived-已归档
    
    private LocalDateTime publishTime; // 发布时间
    
    private LocalDateTime createTime; // 创建时间
    
    private LocalDateTime updateTime; // 更新时间
    
    private Boolean isRead; // 当前用户是否已读
    
    private LocalDateTime readTime; // 当前用户阅读时间
    
    private Integer readCount; // 已读人数
    
    private Integer totalCount; // 总接收人数
}