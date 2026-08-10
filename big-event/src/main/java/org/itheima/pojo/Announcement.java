package org.itheima.pojo;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 公告实体类
 */
@Data
public class Announcement {
    private Integer id; // 公告ID
    
    @NotEmpty(message = "公告标题不能为空")
    private String title; // 公告标题
    
    @NotEmpty(message = "公告内容不能为空")
    private String content; // 公告内容
    
    private Integer publisherId; // 发布者ID
    
    private String type; // 公告类型：company-公司公告，department-部门公告
    
    private Integer departmentId; // 部门ID（部门公告时使用）
    
    private String status; // 状态：draft-草稿，published-已发布，archived-已归档
    
    private LocalDateTime publishTime; // 发布时间
    
    private LocalDateTime createTime; // 创建时间
    
    private LocalDateTime updateTime; // 更新时间
}