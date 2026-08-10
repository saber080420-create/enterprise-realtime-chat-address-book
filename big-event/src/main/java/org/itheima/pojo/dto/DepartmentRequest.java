package org.itheima.pojo.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

/**
 * 部门请求参数类
 * 用于接收前端传递的部门创建和更新请求
 */
@Data
public class DepartmentRequest {
    private Integer id; // 部门ID，更新时使用
    
    @NotEmpty(message = "部门名称不能为空")
    private String departmentName; // 部门名称
    
    private Integer parentId; // 上级部门ID
    
    private String description; // 部门描述
}