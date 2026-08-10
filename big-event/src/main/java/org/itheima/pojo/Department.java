package org.itheima.pojo;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 部门实体类
 */
@Data
public class Department {
    private Integer id; // 部门ID
    
    @NotEmpty(message = "部门名称不能为空")
    private String departmentName; // 部门名称
    
    private Integer parentId; // 上级部门ID
    
    private String description; // 部门描述
    
    @NotNull(message = "创建人ID不能为空")
    private Integer createUserId; // 创建人ID
    
    private LocalDateTime createTime; // 创建时间
    
    private LocalDateTime updateTime; // 更新时间
}